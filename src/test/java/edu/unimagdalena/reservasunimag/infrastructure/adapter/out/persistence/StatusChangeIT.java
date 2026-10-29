package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.TestcontainersConfiguration;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutReport;
import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowReport;
import edu.unimagdalena.reservasunimag.domain.model.compliance.Verdict;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationDeniedException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Occupancy;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.reservation.UseStartResult;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.CancelReservationPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReceiveCheckOutPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReceiveNoShowPort;
import edu.unimagdalena.reservasunimag.domain.port.in.RegisterUseStartedPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReserveResourcesPort;
import edu.unimagdalena.reservasunimag.domain.port.out.OccupancyRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static edu.unimagdalena.reservasunimag.support.Database.academicBlock;
import static edu.unimagdalena.reservasunimag.support.Database.clean;
import static edu.unimagdalena.reservasunimag.support.Database.loan;
import static edu.unimagdalena.reservasunimag.support.Database.startedReservation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC7 contra PostgreSQL: el historial que deja cada invocador, el inicio de uso y los invariantes del modelo. */
@SpringBootTest(properties = {"reservations.outbox.enabled=false", "reservations.academic.use-start-enabled=false"})
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class StatusChangeIT {

    private static final CurrentUser STUDENT = new CurrentUser(UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "2021114001", Role.ESTUDIANTE);
    private static final LocalDate TOMORROW = LocalDate.now(TimeSlot.ZONE).plusDays(1);

    @Autowired
    private ReserveResourcesPort reserveResources;
    @Autowired
    private CancelReservationPort cancelReservation;
    @Autowired
    private ReceiveNoShowPort receiveNoShow;
    @Autowired
    private ReceiveCheckOutPort receiveCheckOut;
    @Autowired
    private RegisterUseStartedPort registerUseStarted;
    @Autowired
    private OccupancyRepositoryPort occupancies;
    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyTables() {
        clean(jdbc);
    }

    private List<String> reasons(UUID reservationId) {
        return jdbc.sql("SELECT reason FROM status_change WHERE reservation_id = :id ORDER BY changed_at, reason")
                .param("id", reservationId).query(String.class).list();
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    @Test
    void reservingAndCancellingLeaveExactlyOneRecordEachAndNoNotice() {
        var reservation = reserveResources.reserve(ReservationRequest.forSpace(STUDENT, "ESP-0107", TOMORROW,
                LocalTime.of(10, 0), LocalTime.of(12, 0))).reservation();
        cancelReservation.cancelByHolder(STUDENT, reservation.id());
        cancelReservation.cancelByHolder(STUDENT, reservation.id());

        assertThat(reasons(reservation.id())).containsExactly("RESERVATION_CONFIRMED", "RESERVATION_CANCELLED");
        var cancelled = jdbc.sql("""
                        SELECT previous_status_module2, new_status, notified_status, outbox_message_id IS NULL AS quiet
                        FROM status_change WHERE reason = 'RESERVATION_CANCELLED'""").query().singleRow();
        assertThat(cancelled.get("previous_status_module2")).isEqualTo("RESERVADO");
        assertThat(cancelled.get("new_status")).isEqualTo("DISPONIBLE");
        assertThat(cancelled.get("notified_status")).isEqualTo("NOT_SENT");
        assertThat(cancelled.get("quiet")).isEqualTo(true);
        assertThat(count("SELECT count(*) FROM outbox_message WHERE type = 'RESOURCE_USE_STARTED'")).isZero();
    }

    @Test
    void anAbsenceAndAReturnLeaveTheirRecordAndASpaceReviewLeavesNone() {
        UUID absent = startedReservation(jdbc, STUDENT.id(), "ESP-0107", 15);
        receiveNoShow.receive(new NoShowReport(UUID.randomUUID(), absent, Instant.now()));
        UUID loanId = loan(jdbc, STUDENT.id(), "ACT-000913", 1, 72, true);
        receiveCheckOut.receive(new CheckOutReport(UUID.randomUUID(), loanId, Instant.now(), null, false));
        UUID reviewed = startedReservation(jdbc, STUDENT.id(), "ESP-0108", 120);
        receiveCheckOut.receive(new CheckOutReport(UUID.randomUUID(), reviewed, Instant.now(), Verdict.SIN_NOVEDAD,
                false));

        assertThat(reasons(absent)).containsExactly("NO_SHOW_REGISTERED");
        assertThat(reasons(loanId)).containsExactly("CHECK_OUT_RECEIVED");
        assertThat(reasons(reviewed)).isEmpty();
    }

    @Test
    void aDeniedOperationLeavesNoRecord() {
        reserveResources.reserve(ReservationRequest.forSpace(STUDENT, "ESP-0107", TOMORROW, LocalTime.of(10, 0),
                LocalTime.of(12, 0)));
        long before = count("SELECT count(*) FROM status_change");

        assertThatThrownBy(() -> reserveResources.reserve(ReservationRequest.forSpace(STUDENT, "ESP-0107", TOMORROW,
                LocalTime.of(11, 0), LocalTime.of(12, 0)))).isInstanceOf(ReservationDeniedException.class);

        assertThat(count("SELECT count(*) FROM status_change")).isEqualTo(before);
    }

    @Test
    void theArrivalMarksTheReservationRecordsItAndQueuesTheOnlyNoticeForModule1() {
        UUID reservation = startedReservation(jdbc, STUDENT.id(), "ESP-0107", 5);

        UseStartResult result = registerUseStarted.startByHolder(STUDENT, reservation);
        UseStartResult again = registerUseStarted.startByHolder(STUDENT, reservation);

        assertThat(again.alreadyStarted()).isTrue();
        assertThat(again.change().outboxMessageId()).isEqualTo(result.change().outboxMessageId());
        assertThat(count("SELECT count(*) FROM reservation WHERE use_started_at IS NOT NULL")).isEqualTo(1);
        var change = jdbc.sql("""
                        SELECT c.notified_status, c.previous_status_module2, c.new_status, o.type,
                               o.payload->'data'->>'newStatus' AS sent_status,
                               o.payload->'data'->>'useEndsAt' IS NOT NULL AS has_end
                        FROM status_change c JOIN outbox_message o ON o.id = c.outbox_message_id""")
                .query().singleRow();
        assertThat(change.get("notified_status")).isEqualTo("PENDING");
        assertThat(change.get("previous_status_module2")).isEqualTo("RESERVADO");
        assertThat(change.get("new_status")).isEqualTo("EN_USO");
        assertThat(change.get("type")).isEqualTo("RESOURCE_USE_STARTED");
        assertThat(change.get("sent_status")).isEqualTo("EN_USO");
        assertThat(change.get("has_end")).isEqualTo(true);
    }

    @Test
    void manySimultaneousArrivalsProduceASingleNotice() throws Exception {
        UUID reservation = startedReservation(jdbc, STUDENT.id(), "ESP-0107", 5);
        int threads = 6;
        var pool = Executors.newFixedThreadPool(threads);
        var start = new CountDownLatch(1);
        try {
            List<CompletableFuture<UseStartResult>> runs = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                runs.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return registerUseStarted.startByHolder(STUDENT, reservation);
                }, pool));
            }
            start.countDown();
            List<UseStartResult> results = runs.stream().map(CompletableFuture::join).toList();
            assertThat(results).filteredOn(r -> !r.alreadyStarted()).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("SELECT count(*) FROM outbox_message WHERE type = 'RESOURCE_USE_STARTED'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM status_change WHERE reason = 'USE_STARTED'")).isEqualTo(1);
    }

    @Test
    void pickingUpAnAssetMarksThePickupAndItsNoticeHasNoEnd() {
        UUID loanId = loan(jdbc, STUDENT.id(), "ACT-000913", 0, 72, false);

        registerUseStarted.startByHolder(STUDENT, loanId);

        assertThat(count("SELECT count(*) FROM loan WHERE picked_up_at IS NOT NULL")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT payload->'data'->'useEndsAt' IS NULL FROM outbox_message").query(Boolean.class)
                .single()).isTrue();
    }

    @Test
    void anArrivalOnAResourceUnderMaintenanceIsNotMarkedNorNotified() {
        UUID loanId = loan(jdbc, STUDENT.id(), "ACT-000912", 0, 72, false);

        assertThatThrownBy(() -> registerUseStarted.startByHolder(STUDENT, loanId))
                .isInstanceOf(ReservationDeniedException.class);

        assertThat(count("SELECT count(*) FROM reservation WHERE use_started_at IS NOT NULL")).isZero();
        assertThat(count("SELECT count(*) FROM outbox_message")).isZero();
    }

    @Test
    void aClassThatReachesItsHourStartsByItselfAndOnlyOnce() {
        UUID due = academicBlock(jdbc, "ESP-0201", 3);
        academicBlock(jdbc, "ESP-0305", -30);                                 // empieza dentro de media hora

        var first = registerUseStarted.startDueAcademicBlocks();
        var second = registerUseStarted.startDueAcademicBlocks();

        assertThat(first.started()).isEqualTo(1);
        assertThat(second.started()).isZero();
        assertThat(reasons(due)).containsExactly("USE_STARTED");
        assertThat(jdbc.sql("SELECT previous_status_module2 FROM status_change").query(String.class).single())
                .isEqualTo("BLOQUEO_ACADEMICO");
        assertThat(count("SELECT count(*) FROM outbox_message WHERE type = 'RESOURCE_USE_STARTED'")).isEqualTo(1);
    }

    @Test
    void aSlotThatEndedFreesTheResourceWithoutAnyTaskAndAnOverdueLoanKeepsIt() {
        // FR-010: el rango dice la verdad en cada consulta; nada tiene que correr para liberar.
        startedReservation(jdbc, STUDENT.id(), "ESP-0107", 120);
        Instant now = Instant.now();
        assertThat(occupancies.findOccupancies(List.of("ESP-0107"), now, now.plus(Duration.ofHours(1)))).isEmpty();

        // FR-011: un préstamo entregado y vencido sigue ocupando, por mucho tiempo que pase.
        loan(jdbc, STUDENT.id(), "ACT-000913", 10, -72, true);
        List<Occupancy> later = occupancies.findOccupancies(List.of("ACT-000913"), now.plus(Duration.ofDays(30)),
                now.plus(Duration.ofDays(30)).plus(Duration.ofHours(1)));
        assertThat(later).hasSize(1);
    }
}
