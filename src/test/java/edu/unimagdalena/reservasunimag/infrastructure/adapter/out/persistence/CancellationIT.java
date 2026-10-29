package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.TestcontainersConfiguration;
import edu.unimagdalena.reservasunimag.domain.model.error.CancellationDeniedException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationAlreadyClosedException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationResult;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.CancelForMaintenancePort;
import edu.unimagdalena.reservasunimag.domain.port.in.CancelReservationPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReserveResourcesPort;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static edu.unimagdalena.reservasunimag.support.Database.clean;
import static edu.unimagdalena.reservasunimag.support.Database.student;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC4 y UC11 contra PostgreSQL: lo que la base garantiza al cancelar (plan de UC4 T011, plan de UC11 T007 y T008). */
@SpringBootTest(properties = "reservations.outbox.enabled=false")
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class CancellationIT {

    private static final CurrentUser STUDENT = new CurrentUser(UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "2021114001", Role.ESTUDIANTE);
    private static final LocalDate TOMORROW = LocalDate.now(TimeSlot.ZONE).plusDays(1);

    @Autowired
    private ReserveResourcesPort reserveResources;
    @Autowired
    private CancelReservationPort cancelReservation;
    @Autowired
    private CancelForMaintenancePort cancelForMaintenance;
    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyTables() {
        clean(jdbc);
    }

    private Reservation reserve(CurrentUser user, String resourceId, int startHour, int endHour) {
        return reserveResources.reserve(ReservationRequest.forSpace(user, resourceId, TOMORROW,
                LocalTime.of(startHour, 0), LocalTime.of(endHour, 0))).reservation();
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    @Test
    void afterCancellingAnotherReservationCanTakeTheSameSlotAndTheRowStaysForAudit() {
        Reservation first = reserve(STUDENT, "ESP-0107", 10, 12);
        CurrentUser other = new CurrentUser(student(jdbc, "IT-CAN-1"), "IT-CAN-1", Role.ESTUDIANTE);

        cancelReservation.cancelByHolder(STUDENT, first.id());
        Reservation second = reserve(other, "ESP-0107", 10, 12);

        assertThat(second.status()).isEqualTo(ReservationStatus.CONFIRMADA);
        var row = jdbc.sql("SELECT status, cancellation_reason, cancelled_at IS NOT NULL AS stamped FROM reservation WHERE id = :id")
                .param("id", first.id()).query().singleRow();
        assertThat(row.get("status")).isEqualTo("CANCELADA");
        assertThat(row.get("cancellation_reason")).isEqualTo("HOLDER");
        assertThat(row.get("stamped")).isEqualTo(true);
    }

    @Test
    void theCancellationTheReportTheClosureAndTheEventAreWrittenTogether() {
        Reservation reservation = reserve(STUDENT, "ESP-0107", 10, 12);

        cancelReservation.cancelByHolder(STUDENT, reservation.id());

        assertThat(count("SELECT count(*) FROM reservation_closure WHERE closure_type = 'CANCELLATION'")).isEqualTo(1);
        var report = jdbc.sql("""
                        SELECT c.event_id = o.id AS linked, c.cancellation_origin, c.reservation_status,
                               c.attributable_to_person, c.notice_minutes > 0 AS has_notice, c.delivery_status,
                               o.type, o.payload->'data'->>'cancellationOrigin' AS event_origin
                        FROM cancellation_report c JOIN outbox_message o ON o.id = c.event_id""")
                .query().singleRow();
        assertThat(report.get("linked")).isEqualTo(true);
        assertThat(report.get("cancellation_origin")).isEqualTo("HOLDER");
        assertThat(report.get("reservation_status")).isEqualTo("CANCELADA");
        assertThat(report.get("attributable_to_person")).isEqualTo(true);
        assertThat(report.get("has_notice")).isEqualTo(true);
        assertThat(report.get("delivery_status")).isEqualTo("PENDING");
        assertThat(report.get("type")).isEqualTo("CANCELLATION");
        assertThat(report.get("event_origin")).isEqualTo("HOLDER");
    }

    @Test
    void twoSimultaneousCancellationsOfTheSameReservationLeaveASingleEvent() throws Exception {
        Reservation reservation = reserve(STUDENT, "ESP-0107", 10, 12);
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            List<CompletableFuture<CancellationResult>> runs = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                runs.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return cancelReservation.cancelByHolder(STUDENT, reservation.id());
                }, pool));
            }
            start.countDown();
            List<CancellationResult> results = runs.stream().map(CompletableFuture::join).toList();

            assertThat(results).extracting(CancellationResult::alreadyCancelled).containsExactlyInAnyOrder(false, true);
            assertThat(results.get(0).cancellation().cancelledAt().getEpochSecond())
                    .isEqualTo(results.get(1).cancellation().cancelledAt().getEpochSecond());
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("SELECT count(*) FROM outbox_message WHERE type = 'CANCELLATION'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM cancellation_report")).isEqualTo(1);
    }

    @Test
    void aRejectedCancellationLeavesNoReportAndNoEvent() {
        // Una reserva que ya empezó: el titular está fuera de plazo.
        UUID started = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO reservation (id, user_id, resource_id, resource_category, origin, status, starts_at, ends_at)
                        VALUES (:id, :user, 'ESP-0107', 'ESPACIO', 'ESTUDIANTIL', 'CONFIRMADA',
                                now() - interval '5 minutes', now() + interval '55 minutes')""")
                .param("id", started).param("user", STUDENT.id()).update();

        assertThatThrownBy(() -> cancelReservation.cancelByHolder(STUDENT, started))
                .isInstanceOf(CancellationDeniedException.class);

        assertThat(count("SELECT count(*) FROM outbox_message WHERE type = 'CANCELLATION'")).isZero();
        assertThat(count("SELECT count(*) FROM cancellation_report")).isZero();
        assertThat(count("SELECT count(*) FROM reservation_closure")).isZero();
    }

    @Test
    void aReservationAlreadyClosedAsAbsenceIsNotCancelledAndNothingIsLeftHalfWritten() {
        Reservation reservation = reserve(STUDENT, "ESP-0107", 10, 12);
        jdbc.sql("INSERT INTO reservation_closure (reservation_id, closure_type) VALUES (:id, 'ABSENCE')")
                .param("id", reservation.id()).update();

        assertThatThrownBy(() -> cancelReservation.cancelByHolder(STUDENT, reservation.id()))
                .isInstanceOf(ReservationAlreadyClosedException.class);

        assertThat(jdbc.sql("SELECT status FROM reservation WHERE id = :id").param("id", reservation.id())
                .query(String.class).single()).isEqualTo("CONFIRMADA");
        assertThat(count("SELECT count(*) FROM outbox_message WHERE type = 'CANCELLATION'")).isZero();
        assertThat(count("SELECT count(*) FROM cancellation_report")).isZero();
    }

    @Test
    void anAbsenceCannotBeRegisteredOnACancelledReservation() {
        Reservation reservation = reserve(STUDENT, "ESP-0107", 10, 12);
        cancelReservation.cancelByHolder(STUDENT, reservation.id());

        assertThatThrownBy(() -> jdbc.sql("INSERT INTO reservation_closure (reservation_id, closure_type) VALUES (:id, 'ABSENCE')")
                .param("id", reservation.id()).update())
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void theDatabaseRefusesAnAttributableAutomaticCancellationOrANoticeWithoutHolder() {
        Reservation reservation = reserve(STUDENT, "ESP-0107", 10, 12);
        String insert = """
                INSERT INTO cancellation_report (reservation_id, event_id, cancellation_origin, reservation_status,
                    reason, attributable_to_person, notice_minutes, cancelled_at, resource_id, resource_category,
                    released_starts_at, released_ends_at)
                VALUES (:id, gen_random_uuid(), 'ACADEMIC_PRIORITY', 'CANCELADA_POR_PRIORIDAD_ACADEMICA', 'x',
                    :attributable, :notice, now(), 'ESP-0107', 'ESPACIO', now(), now() + interval '1 hour')""";

        assertThatThrownBy(() -> jdbc.sql(insert).param("id", reservation.id()).param("attributable", true)
                .param("notice", null).update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql(insert).param("id", reservation.id()).param("attributable", false)
                .param("notice", 30).update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void maintenanceCancelsTheFutureReservationsOfTheResourceInTheDatabase() {
        Reservation first = reserve(STUDENT, "ESP-0305", 8, 9);
        CurrentUser other = new CurrentUser(student(jdbc, "IT-CAN-2"), "IT-CAN-2", Role.ESTUDIANTE);
        Reservation second = reserve(other, "ESP-0305", 10, 12);
        Reservation elsewhere = reserve(other, "ESP-0107", 10, 12);

        assertThat(cancelForMaintenance.preview("ESP-0305").affected()).hasSize(2);
        var applied = cancelForMaintenance.apply("ESP-0305", "Gotera en el salón.");

        assertThat(applied.affected()).extracting(affected -> affected.reservation().id())
                .containsExactly(first.id(), second.id());
        assertThat(jdbc.sql("SELECT count(*) FROM reservation WHERE status = 'CANCELADA_POR_RECURSO_NO_DISPONIBLE'")
                .query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT status FROM reservation WHERE id = :id").param("id", elsewhere.id())
                .query(String.class).single()).isEqualTo("CONFIRMADA");
        assertThat(count("""
                SELECT count(*) FROM cancellation_report
                WHERE cancellation_origin = 'RESOURCE_UNAVAILABLE' AND NOT attributable_to_person
                  AND notice_minutes IS NULL AND reason = 'Gotera en el salón.'""")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM outbox_message WHERE type = 'CANCELLATION'")).isEqualTo(2);
        assertThat(cancelForMaintenance.preview("ESP-0305").affected()).isEmpty();
    }

    @Test
    void anAcademicPriorityCancellationFromTheSystemIsStoredWithItsOwnOrigin() {
        Reservation reservation = reserve(STUDENT, "ESP-0107", 10, 12);

        cancelReservation.cancelBySystem(reservation.id(), CancellationOrigin.ACADEMIC_PRIORITY,
                "La franja se asignó a actividad docente: IS-510 grupo 1.");

        assertThat(jdbc.sql("SELECT status FROM reservation WHERE id = :id").param("id", reservation.id())
                .query(String.class).single()).isEqualTo("CANCELADA_POR_PRIORIDAD_ACADEMICA");
        assertThat(jdbc.sql("SELECT payload->'data'->'noticeMinutes' IS NOT NULL FROM outbox_message WHERE type = 'CANCELLATION'").query(Boolean.class)
                .single()).isFalse();
    }
}
