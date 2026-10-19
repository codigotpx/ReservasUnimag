package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.error.DenialCode;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceTakenException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Denial;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.QuotaUsage;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecord;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.out.DenialRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Module3NotifierPort;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;
import edu.unimagdalena.reservasunimag.TestcontainersConfiguration;
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

import static edu.unimagdalena.reservasunimag.support.Database.clean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La confirmación contra PostgreSQL real: restricción de exclusión, atomicidad con la outbox y cupo (UC2 T017). */
@SpringBootTest
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class ReservationPersistenceAdapterIT {

    private static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOON = Instant.parse("2026-09-01T17:00:00Z");

    @Autowired
    private ReservationRepositoryPort reservations;
    @Autowired
    private DenialRepositoryPort denials;
    @Autowired
    private Module3NotifierPort notifier;
    @Autowired
    private TransactionPort transactions;
    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyTables() {
        clean(jdbc);
    }

    private static Reservation space(String resourceId, String start, String end) {
        return Reservation.confirmed(UUID.randomUUID(), STUDENT, resourceId, ResourceCategory.ESPACIO,
                Instant.parse(start), Instant.parse(end), NOON, null);
    }

    private static Reservation asset(String resourceId, String start, String end) {
        return Reservation.confirmed(UUID.randomUUID(), STUDENT, resourceId, ResourceCategory.ACTIVO,
                Instant.parse(start), Instant.parse(end), NOON, Loan.reserved(7));
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    // ------------------------------------------------------------------------------ restricción de exclusión

    @Test
    void theExclusionConstraintRejectsAnOverlappingConfirmedOccupation() {
        reservations.insert(space("ESP-1", "2026-09-01T15:00:00Z", "2026-09-01T17:00:00Z"));

        assertThatThrownBy(() -> reservations.insert(space("ESP-1", "2026-09-01T16:00:00Z", "2026-09-01T18:00:00Z")))
                .isInstanceOf(ResourceTakenException.class);
        assertThat(count("reservation")).isEqualTo(1);
    }

    @Test
    void touchingOccupationsAndOtherResourcesAreFine() {
        reservations.insert(space("ESP-1", "2026-09-01T15:00:00Z", "2026-09-01T17:00:00Z"));
        reservations.insert(space("ESP-1", "2026-09-01T17:00:00Z", "2026-09-01T19:00:00Z"));
        reservations.insert(space("ESP-2", "2026-09-01T15:00:00Z", "2026-09-01T17:00:00Z"));

        assertThat(count("reservation")).isEqualTo(3);
    }

    @Test
    void aCancelledReservationDoesNotBlockTheSameTime() {
        Reservation cancelled = space("ESP-1", "2026-09-01T15:00:00Z", "2026-09-01T17:00:00Z");
        reservations.insert(cancelled);
        jdbc.sql("UPDATE reservation SET status = 'CANCELADA' WHERE id = :id").param("id", cancelled.id()).update();

        reservations.insert(space("ESP-1", "2026-09-01T15:00:00Z", "2026-09-01T17:00:00Z"));

        assertThat(count("reservation")).isEqualTo(2);
    }

    @Test
    void twoLoanPeriodsOfTheSameAssetCannotOverlap() {
        reservations.insert(asset("ACT-1", "2026-09-01T19:30:00Z", "2026-09-11T03:00:00Z"));

        assertThatThrownBy(() -> reservations.insert(asset("ACT-1", "2026-09-05T15:00:00Z", "2026-09-12T03:00:00Z")))
                .isInstanceOf(ResourceTakenException.class);
    }

    @Test
    void aLoanWritesItsLoanRowWithTheAppliedTerm() {
        Reservation loan = asset("ACT-1", "2026-09-01T19:30:00Z", "2026-09-11T03:00:00Z");
        reservations.insert(loan);

        assertThat(jdbc.sql("SELECT term_business_days FROM loan WHERE reservation_id = :id")
                .param("id", loan.id()).query(Integer.class).single()).isEqualTo(7);
    }

    // ------------------------------------------------------------------------------------------ atomicidad

    private ReservationRecord recordOf(Reservation reservation) {
        return new ReservationRecord(reservation,
                Resource.space("ESP-1", "Sala de Estudio 3", "SALA_DE_ESTUDIO", "Biblioteca", OperationalStatus.DISPONIBLE,
                        "Biblioteca Central", 8, List.of()),
                new ReservationHolder(STUDENT, "2021114001", "Estudiante de Prueba", Role.ESTUDIANTE), NOON);
    }

    @Test
    void theReservationAndItsEventAreWrittenTogether() {
        Reservation reservation = space("ESP-1", "2026-09-01T15:00:00Z", "2026-09-01T17:00:00Z");

        transactions.required(() -> {
            reservations.insert(reservation);
            notifier.reservationCreated(recordOf(reservation));
            return null;
        });

        assertThat(count("reservation")).isEqualTo(1);
        var outbox = jdbc.sql("SELECT type, status, attempts, payload::text AS payload FROM outbox_message")
                .query().singleRow();
        assertThat(outbox.get("type")).isEqualTo("RESERVATION_RECORD");
        assertThat(outbox.get("status")).isEqualTo("PENDING");
        assertThat(outbox.get("attempts")).isEqualTo(0);
        assertThat((String) outbox.get("payload")).contains(reservation.id().toString());
    }

    @Test
    void aRollbackLeavesNeitherTheReservationNorTheEvent() {
        Reservation reservation = space("ESP-1", "2026-09-01T15:00:00Z", "2026-09-01T17:00:00Z");

        assertThatThrownBy(() -> transactions.required(() -> {
            reservations.insert(reservation);
            notifier.reservationCreated(recordOf(reservation));
            throw new IllegalStateException("algo falló después");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(count("reservation")).isZero();
        assertThat(count("outbox_message")).isZero();
    }

    @Test
    void theEventIdIsTheOutboxRowId() {
        Reservation reservation = space("ESP-1", "2026-09-01T15:00:00Z", "2026-09-01T17:00:00Z");
        transactions.required(() -> {
            reservations.insert(reservation);
            notifier.reservationCreated(recordOf(reservation));
            return null;
        });

        var row = jdbc.sql("SELECT id::text AS id, payload->>'eventId' AS event_id FROM outbox_message")
                .query().singleRow();
        assertThat(row.get("event_id")).isEqualTo(row.get("id"));
    }

    // ----------------------------------------------------------------------------------------- denegaciones

    @Test
    void aDenialSurvivesTheRollbackOfTheTransactionItHappenedIn() {
        assertThatThrownBy(() -> transactions.required(() -> {
            denials.record(new Denial(UUID.randomUUID(), STUDENT, "ESP-1", DenialCode.RES_004, NOON));
            throw new IllegalStateException("la reserva se deshace");
        })).isInstanceOf(IllegalStateException.class);

        var row = jdbc.sql("SELECT user_id, resource_id, code FROM denial").query().singleRow();
        assertThat(row.get("user_id")).isEqualTo(STUDENT);
        assertThat(row.get("resource_id")).isEqualTo("ESP-1");
        assertThat(row.get("code")).isEqualTo("RES-004");
    }

    // ------------------------------------------------------------------------------------------------ cupo

    private void setStatus(Reservation reservation, ReservationStatus status) {
        jdbc.sql("UPDATE reservation SET status = :status WHERE id = :id")
                .param("status", status.name()).param("id", reservation.id()).update();
    }

    @Test
    void aSpaceCountsUntilItsSlotEnds() {
        reservations.insert(space("ESP-1", "2026-09-01T15:00:00Z", "2026-09-01T17:00:00Z"));
        reservations.insert(space("ESP-2", "2026-09-01T12:00:00Z", "2026-09-01T14:00:00Z"));

        QuotaUsage usage = reservations.quotaOf(STUDENT, Instant.parse("2026-09-01T14:30:00Z"));

        assertThat(usage.used()).isEqualTo(1);
        assertThat(usage.nextRelease()).isEqualTo(Instant.parse("2026-09-01T17:00:00Z"));
        assertThat(usage.waitsForReturn()).isFalse();
    }

    @Test
    void cancelledAndFinishedReservationsFreeTheQuota() {
        Reservation cancelled = space("ESP-1", "2026-09-01T15:00:00Z", "2026-09-01T17:00:00Z");
        Reservation finished = space("ESP-2", "2026-09-01T15:00:00Z", "2026-09-01T17:00:00Z");
        reservations.insert(cancelled);
        reservations.insert(finished);
        setStatus(cancelled, ReservationStatus.CANCELADA);
        setStatus(finished, ReservationStatus.FINALIZADA);

        QuotaUsage usage = reservations.quotaOf(STUDENT, Instant.parse("2026-09-01T14:00:00Z"));

        assertThat(usage.used()).isZero();
        assertThat(usage.nextRelease()).isNull();
    }

    @Test
    void aLoanCountsUntilItIsReturnedEvenWhenOverdue() {
        Reservation overdue = asset("ACT-1", "2026-08-20T15:00:00Z", "2026-08-25T03:00:00Z");
        Reservation returned = asset("ACT-2", "2026-08-20T15:00:00Z", "2026-08-25T03:00:00Z");
        reservations.insert(overdue);
        reservations.insert(returned);
        jdbc.sql("UPDATE loan SET returned_at = :at WHERE reservation_id = :id")
                .param("at", java.time.OffsetDateTime.parse("2026-08-24T10:00:00Z")).param("id", returned.id()).update();

        QuotaUsage usage = reservations.quotaOf(STUDENT, Instant.parse("2026-09-01T14:00:00Z"));

        assertThat(usage.used()).isEqualTo(1);
        assertThat(usage.nextRelease()).isNull();
        assertThat(usage.waitsForReturn()).isTrue();
    }

    @Test
    void otherPeopleDoNotCountAgainstMyQuota() {
        reservations.insert(space("ESP-1", "2026-09-01T15:00:00Z", "2026-09-01T17:00:00Z"));

        assertThat(reservations.quotaOf(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                Instant.parse("2026-09-01T14:00:00Z")).used()).isZero();
    }

    @Test
    void myReservationsComeOrderedAndOnlyTheActiveOnesWhenAsked() {
        Reservation later = space("ESP-1", "2026-09-01T19:00:00Z", "2026-09-01T21:00:00Z");
        Reservation sooner = space("ESP-2", "2026-09-01T15:00:00Z", "2026-09-01T17:00:00Z");
        Reservation past = space("ESP-3", "2026-08-30T15:00:00Z", "2026-08-30T17:00:00Z");
        reservations.insert(later);
        reservations.insert(sooner);
        reservations.insert(past);
        Instant now = Instant.parse("2026-09-01T14:00:00Z");

        assertThat(reservations.findByHolder(STUDENT, true, now)).extracting(Reservation::id)
                .containsExactly(sooner.id(), later.id());
        assertThat(reservations.findByHolder(STUDENT, false, now)).extracting(Reservation::id)
                .containsExactly(past.id(), sooner.id(), later.id());
    }

    @Test
    void aLoanComesBackWithItsTermAndPickupState() {
        Reservation loan = asset("ACT-1", "2026-09-01T19:30:00Z", "2026-09-11T03:00:00Z");
        reservations.insert(loan);

        Reservation read = reservations.findByHolder(STUDENT, true, Instant.parse("2026-09-01T14:00:00Z")).get(0);

        assertThat(read.loan()).isEqualTo(Loan.reserved(7));
        assertThat(read.endsAt()).isEqualTo(Instant.parse("2026-09-11T03:00:00Z"));
    }
}
