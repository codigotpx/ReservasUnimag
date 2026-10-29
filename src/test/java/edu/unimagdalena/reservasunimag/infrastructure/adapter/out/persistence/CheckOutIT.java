package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.TestcontainersConfiguration;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutAck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutReport;
import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowReport;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReviewFilter;
import edu.unimagdalena.reservasunimag.domain.model.compliance.Verdict;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.SearchResourcesQuery;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryPendingCheckOutsPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReceiveCheckOutPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReceiveNoShowPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReserveResourcesPort;
import edu.unimagdalena.reservasunimag.domain.port.in.SearchResourcesPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.Database.clean;
import static edu.unimagdalena.reservasunimag.support.Database.loan;
import static edu.unimagdalena.reservasunimag.support.Database.startedReservation;
import static edu.unimagdalena.reservasunimag.support.Database.student;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC12 contra PostgreSQL: el cierre del préstamo, la revisión que no toca nada y las listas de pendientes. */
@SpringBootTest(properties = "reservations.outbox.enabled=false")
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class CheckOutIT {

    private static final CurrentUser STUDENT = new CurrentUser(UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "2021114001", Role.ESTUDIANTE);

    @Autowired
    private ReceiveCheckOutPort receiveCheckOut;
    @Autowired
    private ReceiveNoShowPort receiveNoShow;
    @Autowired
    private QueryPendingCheckOutsPort pending;
    @Autowired
    private ReserveResourcesPort reserveResources;
    @Autowired
    private SearchResourcesPort searchResources;
    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyTables() {
        clean(jdbc);
    }

    private CheckOutAck checkOut(UUID reservationId, Verdict verdict) {
        return receiveCheckOut.receive(new CheckOutReport(UUID.randomUUID(), reservationId, Instant.now(), verdict,
                false));
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private String status(UUID id) {
        return jdbc.sql("SELECT status FROM reservation WHERE id = :id").param("id", id).query(String.class).single();
    }

    @Test
    void afterTheCheckOutTheAssetCanBeReservedByAnotherPerson() {
        // El préstamo de ACT-000913 cubre de hace 1 día hasta dentro de 3 días: mañana está ocupado.
        UUID loanId = loan(jdbc, STUDENT.id(), "ACT-000913", 1, 72, true);
        CurrentUser other = new CurrentUser(student(jdbc, "IT-CO-1"), "IT-CO-1", Role.ESTUDIANTE);
        var tomorrow = ReservationRequest.forAsset(other, "ACT-000913", LocalDate.now(TimeSlot.ZONE).plusDays(1),
                LocalTime.of(9, 0));
        assertThatThrownBy(() -> reserveResources.reserve(tomorrow))
                .isInstanceOf(edu.unimagdalena.reservasunimag.domain.model.error.ReservationDeniedException.class);

        CheckOutAck ack = checkOut(loanId, null);

        assertThat(ack.loanClosed()).isTrue();
        assertThat(status(loanId)).isEqualTo("FINALIZADA");
        assertThat(count("SELECT count(*) FROM loan WHERE returned_at IS NOT NULL")).isEqualTo(1);
        assertThat(reserveResources.reserve(tomorrow).reservation().status().name()).isEqualTo("CONFIRMADA");
        assertThat(count("SELECT count(*) FROM outbox_message WHERE type = 'CHECK_OUT_ACK'")).isEqualTo(1);
    }

    @Test
    void aRepeatedCheckOutDoesNotCloseOrReleaseTwice() {
        UUID loanId = loan(jdbc, STUDENT.id(), "ACT-000913", 1, 72, true);
        checkOut(loanId, null);
        Instant firstReturn = jdbc.sql("SELECT returned_at FROM loan").query(java.time.OffsetDateTime.class).single()
                .toInstant();

        CheckOutAck second = checkOut(loanId, null);

        assertThat(second.duplicate()).isTrue();
        assertThat(count("SELECT count(*) FROM check_out")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT returned_at FROM loan").query(java.time.OffsetDateTime.class).single()
                .toInstant()).isEqualTo(firstReturn);
    }

    @Test
    void theDatabaseRefusesAVerdictOnAnAssetOrASpaceReviewWithoutOne() {
        UUID loanId = loan(jdbc, STUDENT.id(), "ACT-000913", 1, 72, true);
        UUID spaceId = startedReservation(jdbc, STUDENT.id(), "ESP-0107", 120);
        String insert = """
                INSERT INTO check_out (reservation_id, kind, source_event_id, occurred_at, received_at, verdict,
                                       due_at_snapshot)
                VALUES (:id, :kind, gen_random_uuid(), now(), now(), :verdict, :due)""";

        assertThatThrownBy(() -> jdbc.sql(insert).param("id", loanId).param("kind", "ASSET_RETURN")
                .param("verdict", "SIN_NOVEDAD").param("due", java.time.OffsetDateTime.now()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql(insert).param("id", spaceId).param("kind", "SPACE_REVIEW")
                .param("verdict", null).param("due", null).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aSpaceReviewChangesNothingInTheConsultation() {
        // SC-005: la consulta de UC1 de esa franja da exactamente lo mismo antes y después de la revisión.
        UUID spaceId = startedReservation(jdbc, STUDENT.id(), "ESP-0107", 120);
        LocalDate today = LocalDate.now(TimeSlot.ZONE);
        var query = new SearchResourcesQuery(STUDENT, new TimeSlot(today, LocalTime.of(6, 0), LocalTime.of(22, 0)),
                new edu.unimagdalena.reservasunimag.domain.model.resource.ResourceFilter(null, "SALA_DE_ESTUDIO", null),
                new PageRequest(1, 20));
        var before = searchResources.search(query).page().items();

        CheckOutAck ack = checkOut(spaceId, Verdict.SIN_NOVEDAD);

        assertThat(ack.accepted()).isTrue();
        assertThat(status(spaceId)).isEqualTo("CONFIRMADA");
        assertThat(searchResources.search(query).page().items()).isEqualTo(before);
    }

    @Test
    void aMaintenanceVerdictCancelsTheReservationsAheadWithoutBlamingAnyone() {
        UUID used = startedReservation(jdbc, STUDENT.id(), "ESP-0305", 120);
        var ahead = reserveResources.reserve(ReservationRequest.forSpace(STUDENT, "ESP-0305",
                LocalDate.now(TimeSlot.ZONE).plusDays(1), LocalTime.of(14, 0), LocalTime.of(16, 0))).reservation();

        checkOut(used, Verdict.REQUIERE_MANTENIMIENTO);

        assertThat(status(ahead.id())).isEqualTo("CANCELADA_POR_RECURSO_NO_DISPONIBLE");
        assertThat(status(used)).isEqualTo("CONFIRMADA");
        assertThat(count("""
                SELECT count(*) FROM cancellation_report
                WHERE cancellation_origin = 'RESOURCE_UNAVAILABLE' AND NOT attributable_to_person""")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM check_out WHERE verdict = 'REQUIERE_MANTENIMIENTO'")).isEqualTo(1);
    }

    @Test
    void thePendingReturnsAreTheOverdueLoansInHandsAndNothingElse() {
        UUID overdue = loan(jdbc, STUDENT.id(), "ACT-000913", 10, -72, true);
        UUID nearThreshold = loan(jdbc, STUDENT.id(), "ACT-001200", 10, -24 * 6, true);
        loan(jdbc, STUDENT.id(), "ACT-001450", 1, 48, true);                   // en plazo
        loan(jdbc, STUDENT.id(), "ACT-000912", 10, -72, false);                // nunca recogido
        UUID returned = loan(jdbc, STUDENT.id(), "ACT-004512", 10, -72, true);
        checkOut(returned, null);

        var result = pending.pendingReturns(new PageRequest(1, 20));

        assertThat(result.page().items()).extracting(p -> p.reservation().id())
                .containsExactly(nearThreshold, overdue);
        assertThat(result.page().total()).isEqualTo(2);
        assertThat(result.nearLossThreshold()).isEqualTo(1);
        assertThat(result.overdueDays(result.page().items().get(1))).isEqualTo(3);
        assertThat(result.page().items().get(1).holder().code()).isEqualTo("2021114001");
        assertThat(result.page().items().get(1).resourceName()).isEqualTo("Microscopio M-015");
    }

    @Test
    void thePendingReviewsAreTheFinishedSpacesThatWereUsedAndNotReviewed() {
        UUID finished = startedReservation(jdbc, STUDENT.id(), "ESP-0107", 120);
        UUID reviewed = startedReservation(jdbc, STUDENT.id(), "ESP-0108", 120);
        checkOut(reviewed, Verdict.SIN_NOVEDAD);
        UUID absent = startedReservation(jdbc, STUDENT.id(), "ESP-0201", 120);
        receiveNoShow.receive(new NoShowReport(UUID.randomUUID(), absent, Instant.now().minusSeconds(3_600)));
        startedReservation(jdbc, STUDENT.id(), "ESP-0305", 10);              // todavía en curso
        UUID academic = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO reservation (id, resource_id, resource_category, origin, status, starts_at, ends_at)
                        VALUES (:id, 'ESP-0412', 'ESPACIO', 'ACADEMICO', 'CONFIRMADA',
                                now() - interval '3 hours', now() - interval '1 hour')""")
                .param("id", academic).update();

        var all = pending.pendingReviews(new PendingReviewFilter(null, null, null, null), new PageRequest(1, 20));
        assertThat(all.page().items()).extracting(p -> p.reservation().id())
                .containsExactlyInAnyOrder(finished, academic);
        assertThat(all.student()).isEqualTo(1);
        assertThat(all.academic()).isEqualTo(1);

        var onlyStudents = pending.pendingReviews(new PendingReviewFilter(null, null, ReservationOrigin.ESTUDIANTIL,
                null), new PageRequest(1, 20));
        assertThat(onlyStudents.page().items()).singleElement()
                .satisfies(p -> assertThat(p.reservation().id()).isEqualTo(finished));
        assertThat(pending.pendingReviews(new PendingReviewFilter(null, null, null, "ESP-0412"),
                new PageRequest(1, 20)).page().items()).singleElement()
                .satisfies(p -> assertThat(p.holder()).isNull());
    }
}
