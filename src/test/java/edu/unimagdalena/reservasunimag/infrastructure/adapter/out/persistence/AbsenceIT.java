package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import com.zaxxer.hikari.HikariDataSource;
import edu.unimagdalena.reservasunimag.TestcontainersConfiguration;
import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowAck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowReport;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationAlreadyClosedException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.CancelReservationPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ListMyReservationsPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReceiveNoShowPort;
import edu.unimagdalena.reservasunimag.domain.port.in.VoidNoShowPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static edu.unimagdalena.reservasunimag.support.Database.clean;
import static edu.unimagdalena.reservasunimag.support.Database.startedReservation;
import static edu.unimagdalena.reservasunimag.support.Database.student;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC9 contra PostgreSQL: lo que la base garantiza al registrar una ausencia (plan de UC9 T013). */
@SpringBootTest(properties = "reservations.outbox.enabled=false")
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class AbsenceIT {

    private static final CurrentUser STUDENT = new CurrentUser(UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "2021114001", Role.ESTUDIANTE);

    @Autowired
    private ReceiveNoShowPort receiveNoShow;
    @Autowired
    private VoidNoShowPort voidNoShow;
    @Autowired
    private CancelReservationPort cancelReservation;
    @Autowired
    private ListMyReservationsPort listMyReservations;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private HikariDataSource dataSource;

    @BeforeEach
    void emptyTables() {
        clean(jdbc);
    }

    private NoShowAck report(UUID reservationId) {
        return receiveNoShow.receive(new NoShowReport(UUID.randomUUID(), reservationId, Instant.now()));
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private String status(UUID reservationId) {
        return jdbc.sql("SELECT status FROM reservation WHERE id = :id").param("id", reservationId)
                .query(String.class).single();
    }

    @Test
    void theAbsenceTheFinishedReservationTheClosureAndTheAckAreWrittenTogether() {
        UUID reservation = startedReservation(jdbc, STUDENT.id(), "ESP-0107", 15);

        NoShowAck ack = report(reservation);

        assertThat(ack.accepted()).isTrue();
        assertThat(status(reservation)).isEqualTo("FINALIZADA");
        assertThat(count("SELECT count(*) FROM absence WHERE resource_released AND NOT voided")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM reservation_closure WHERE closure_type = 'ABSENCE'")).isEqualTo(1);
        var event = jdbc.sql("""
                        SELECT payload->'data'->>'accepted' AS accepted, payload->'data'->'holder'->>'code' AS code
                        FROM outbox_message WHERE type = 'NO_SHOW_ACK'""").query().singleRow();
        assertThat(event.get("accepted")).isEqualTo("true");
        assertThat(event.get("code")).isEqualTo("2021114001");
    }

    @Test
    void afterTheAbsenceSomeoneElseCanTakeTheRestOfTheSlot() {
        UUID reservation = startedReservation(jdbc, STUDENT.id(), "ESP-0107", 15);
        report(reservation);

        UUID other = student(jdbc, "IT-ABS-1");
        // La restricción de exclusión ya no ve la reserva FINALIZADA: el mismo tramo se puede volver a ocupar.
        startedReservation(jdbc, other, "ESP-0107", 15);

        assertThat(count("SELECT count(*) FROM reservation WHERE status = 'CONFIRMADA'")).isEqualTo(1);
    }

    @Test
    void aCancellationAndANoShowArrivingTogetherLeaveExactlyOneClosure() throws Exception {
        for (int round = 0; round < 5; round++) {
            clean(jdbc);
            UUID reservation = startedReservation(jdbc, STUDENT.id(), "ESP-0107", 15);
            var pool = Executors.newFixedThreadPool(2);
            var start = new CountDownLatch(1);
            try {
                var cancel = CompletableFuture.runAsync(() -> {
                    await(start);
                    try {
                        cancelReservation.cancelBySystem(reservation, CancellationOrigin.RESOURCE_UNAVAILABLE, "Gotera.");
                    } catch (RuntimeException lostTheRace) {
                        // Si la ausencia llegó primero la reserva ya está FINALIZADA y no se cancela.
                    }
                }, pool);
                var noShow = CompletableFuture.runAsync(() -> {
                    await(start);
                    report(reservation);
                }, pool);
                start.countDown();
                CompletableFuture.allOf(cancel, noShow).join();
            } finally {
                pool.shutdownNow();
            }

            assertThat(count("SELECT count(*) FROM reservation_closure")).isEqualTo(1);
            assertThat(count("SELECT (SELECT count(*) FROM absence) + (SELECT count(*) FROM cancellation_report)"))
                    .isEqualTo(1);
        }
    }

    @Test
    void ifTheReservationWasAlreadyClosedNothingIsLeftHalfWritten() {
        UUID reservation = startedReservation(jdbc, STUDENT.id(), "ESP-0107", 15);
        jdbc.sql("INSERT INTO reservation_closure (reservation_id, closure_type) VALUES (:id, 'CANCELLATION')")
                .param("id", reservation).update();

        assertThatThrownBy(() -> report(reservation)).isInstanceOf(ReservationAlreadyClosedException.class);

        assertThat(status(reservation)).isEqualTo("CONFIRMADA");
        assertThat(count("SELECT count(*) FROM absence")).isZero();
        assertThat(count("SELECT count(*) FROM outbox_message")).isZero();
    }

    @Test
    void voidingUndoesTheRecordButNotTheReleasedResource() {
        UUID reservation = startedReservation(jdbc, STUDENT.id(), "ESP-0107", 15);
        report(reservation);

        assertThat(voidNoShow.voidReport(reservation, "La persona sí se presentó.")).isTrue();

        var row = jdbc.sql("SELECT voided, voided_at IS NOT NULL AS stamped, voided_reason FROM absence")
                .query().singleRow();
        assertThat(row.get("voided")).isEqualTo(true);
        assertThat(row.get("stamped")).isEqualTo(true);
        assertThat(row.get("voided_reason")).isEqualTo("La persona sí se presentó.");
        assertThat(count("SELECT count(*) FROM reservation_closure")).isZero();
        assertThat(status(reservation)).isEqualTo("FINALIZADA");
    }

    @Test
    void theHolderSeesTheAbsenceInTheirReservationsUntilItIsVoided() {
        UUID reservation = startedReservation(jdbc, STUDENT.id(), "ESP-0107", 15);
        report(reservation);

        var item = listMyReservations.list(STUDENT, false).reservations().get(0);
        assertThat(item.absence()).isNotNull();
        assertThat(item.cancellable()).isFalse();

        voidNoShow.voidReport(reservation, "error");
        assertThat(listMyReservations.list(STUDENT, false).reservations()).extracting(i -> i.absence())
                .containsExactly((edu.unimagdalena.reservasunimag.domain.model.compliance.Absence) null);
        assertThat(listMyReservations.list(STUDENT, true).reservations()).isEqualTo(List.of());
    }

    @Test
    void listingTheReservationsGivesBackEveryConnection() {
        startedReservation(jdbc, STUDENT.id(), "ESP-0107", 15);
        int poolSize = dataSource.getMaximumPoolSize();

        // Cada consulta de las ausencias dejaba su conexión tomada; pasado el tamaño del pool, la app se quedaba sin base.
        for (int i = 0; i <= poolSize; i++) {
            listMyReservations.list(STUDENT, true);
        }

        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
