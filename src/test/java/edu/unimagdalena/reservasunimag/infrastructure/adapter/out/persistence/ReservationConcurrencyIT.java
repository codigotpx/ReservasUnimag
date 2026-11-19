package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.error.DenialCode;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationDeniedException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRequest;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.ReserveResourcesPort;
import edu.unimagdalena.reservasunimag.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static edu.unimagdalena.reservasunimag.support.Database.clean;
import static edu.unimagdalena.reservasunimag.support.Database.student;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrencia de verdad contra PostgreSQL, atravesando todo el caso de uso con los Módulos 1 y 3 falsos del perfil
 * local: dos reservas del mismo recurso no pueden coexistir, y una persona no se cuela dos veces en su cupo
 * (UC2 FR-004, FR-008, SC-004).
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class ReservationConcurrencyIT {

    private static final LocalDate TOMORROW = LocalDate.now(edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot.ZONE).plusDays(1);

    @Autowired
    private ReserveResourcesPort reserveResources;
    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyTables() {
        clean(jdbc);
    }

    private CurrentUser newStudent(String code) {
        return new CurrentUser(student(jdbc, code), code, Role.ESTUDIANTE);
    }

    /** Lanza todas las tareas a la vez y devuelve lo que cada una respondió: la reserva o la excepción. */
    private <T> List<Object> runTogether(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CyclicBarrier startTogether = new CyclicBarrier(tasks.size());
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    startTogether.await();
                    try {
                        return task.call();
                    } catch (Exception failure) {
                        return failure;
                    }
                }));
            }
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void whenManyPeopleAskForTheSameSpaceAtTheSameTimeExactlyOneGetsIt() throws Exception {
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            CurrentUser user = newStudent("IT-%04d".formatted(i));
            tasks.add(() -> reserveResources.reserve(ReservationRequest.forSpace(user, "ESP-0107", TOMORROW,
                    LocalTime.of(10, 0), LocalTime.of(12, 0))));
        }

        List<Object> outcomes = runTogether(tasks);

        assertThat(outcomes.stream().filter(o -> !(o instanceof Exception))).hasSize(1);
        assertThat(outcomes.stream().filter(o -> o instanceof Exception))
                .hasSize(7)
                .allSatisfy(o -> assertThat(o).isInstanceOfSatisfying(ReservationDeniedException.class,
                        denial -> assertThat(denial.code()).isEqualTo(DenialCode.RES_004)));
        assertThat(jdbc.sql("SELECT count(*) FROM reservation WHERE resource_id = 'ESP-0107' AND status = 'CONFIRMADA'")
                .query(Long.class).single()).isEqualTo(1);
        // Cada perdedor dejó su denegación, y solo el ganador dejó su evento.
        assertThat(jdbc.sql("SELECT count(*) FROM denial WHERE code = 'RES-004'").query(Long.class).single()).isEqualTo(7);
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_message").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void partiallyOverlappingSlotsAtTheSameTimeAlsoLeaveOnlyOne() throws Exception {
        List<Callable<Object>> tasks = new ArrayList<>();
        int[][] slots = {{9, 11}, {10, 12}, {11, 13}, {10, 11}};
        for (int i = 0; i < slots.length; i++) {
            CurrentUser user = newStudent("IT-%04d".formatted(i));
            int[] slot = slots[i];
            tasks.add(() -> reserveResources.reserve(ReservationRequest.forSpace(user, "ESP-0305", TOMORROW,
                    LocalTime.of(slot[0], 0), LocalTime.of(slot[1], 0))));
        }

        runTogether(tasks);

        // [9,11), [10,12) y [11,13) se cruzan entre sí de a pares; [10,11) cruza con las dos primeras.
        // Lo que se garantiza: ninguna pareja confirmada se cruza.
        var overlapping = jdbc.sql("""
                        SELECT count(*) FROM reservation a JOIN reservation b
                          ON a.resource_id = b.resource_id AND a.id < b.id AND a.occupancy && b.occupancy
                        WHERE a.status = 'CONFIRMADA' AND b.status = 'CONFIRMADA'""")
                .query(Long.class).single();
        assertThat(overlapping).isZero();
    }

    @Test
    void onePersonCannotSneakPastTheQuotaWithSimultaneousRequests() throws Exception {
        CurrentUser user = newStudent("IT-0100");
        // Ya tiene 2 vigentes (cupo de 3).
        for (int i = 1; i <= 2; i++) {
            reserveResources.reserve(ReservationRequest.forSpace(user, "ESP-0201", TOMORROW.plusDays(i),
                    LocalTime.of(10, 0), LocalTime.of(11, 0)));
        }
        List<Callable<Object>> tasks = new ArrayList<>();
        for (String space : List.of("ESP-0107", "ESP-0305", "ESP-0412", "ESP-0413", "ESP-0108")) {
            tasks.add(() -> reserveResources.reserve(ReservationRequest.forSpace(user, space, TOMORROW,
                    LocalTime.of(14, 0), LocalTime.of(15, 0))));
        }

        List<Object> outcomes = runTogether(tasks);

        assertThat(outcomes.stream().filter(o -> !(o instanceof Exception))).hasSize(1);
        assertThat(outcomes.stream().filter(o -> o instanceof Exception)).hasSize(4)
                .allSatisfy(o -> assertThat(o).isInstanceOfSatisfying(ReservationDeniedException.class,
                        denial -> assertThat(denial.code()).isEqualTo(DenialCode.RES_002)));
        UUID id = user.id();
        assertThat(jdbc.sql("SELECT count(*) FROM reservation WHERE user_id = :id AND status = 'CONFIRMADA'")
                .param("id", id).query(Long.class).single()).isEqualTo(3);
    }
}
