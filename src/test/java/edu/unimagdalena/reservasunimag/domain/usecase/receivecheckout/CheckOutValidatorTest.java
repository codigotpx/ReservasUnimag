package edu.unimagdalena.reservasunimag.domain.usecase.receivecheckout;

import edu.unimagdalena.reservasunimag.domain.model.compliance.Absence;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutRejection;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutReport;
import edu.unimagdalena.reservasunimag.domain.model.compliance.Verdict;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.support.InMemoryAbsences;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.STUDENT;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

/** Las dos tablas de rechazo de UC12 (FR-008, FR-010, FR-011, FR-013): un caso por motivo. */
class CheckOutValidatorTest {

    private final InMemoryAbsences absences = new InMemoryAbsences();
    private final CheckOutValidator validator = new CheckOutValidator(absences);

    private static Reservation asset(Loan loan) {
        return Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), "ACT-000913", ResourceCategory.ACTIVO,
                at(10, 0), at(10, 0).plus(Duration.ofDays(3)), at(7, 0), loan);
    }

    private static Reservation space() {
        return Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), "ESP-0107", ResourceCategory.ESPACIO,
                at(10, 0), at(12, 0), at(7, 0), null);
    }

    private static CheckOutReport report(Reservation reservation, Instant occurredAt, Verdict verdict,
                                         boolean damage) {
        return new CheckOutReport(UUID.randomUUID(), reservation.id(), occurredAt, verdict, damage);
    }

    private Optional<CheckOutRejection> check(Reservation reservation, Instant occurredAt, Verdict verdict) {
        return validator.rejectionFor(reservation, report(reservation, occurredAt, verdict, false));
    }

    @Test
    void anAssetInHandsWithoutVerdictIsApplied() {
        assertThat(check(asset(new Loan(3, at(10, 5), null)), at(16, 0), null)).isEmpty();
    }

    @Test
    void anAssetReturnedTheSameDayItWasPickedUpIsAppliedToo() {
        // Edge case "Check-out el mismo día de la entrega": se procesa igual, sin contar retraso.
        assertThat(check(asset(new Loan(3, at(10, 5), null)), at(10, 20), null)).isEmpty();
    }

    @Test
    void aCancelledAssetReservationIsRejected() {
        Reservation cancelled = asset(Loan.reserved(3)).cancel(CancellationOrigin.HOLDER);

        assertThat(check(cancelled, at(16, 0), null)).contains(CheckOutRejection.RESERVATION_CANCELLED);
    }

    @Test
    void anAssetNeverPickedUpHasNothingToReturn() {
        assertThat(check(asset(Loan.reserved(3)), at(16, 0), null)).contains(CheckOutRejection.ASSET_NOT_PICKED_UP);
    }

    @Test
    void aVerdictOrADamageDescriptionOnAnAssetIsRejectedNotIgnored() {
        Reservation inHands = asset(new Loan(3, at(10, 5), null));

        assertThat(check(inHands, at(16, 0), Verdict.SIN_NOVEDAD)).contains(CheckOutRejection.VERDICT_REQUIRED);
        assertThat(validator.rejectionFor(inHands, report(inHands, at(16, 0), null, true)))
                .contains(CheckOutRejection.VERDICT_REQUIRED);
    }

    @Test
    void aFinishedSpaceWithItsVerdictIsApplied() {
        assertThat(check(space(), at(12, 0), Verdict.SIN_NOVEDAD)).isEmpty();
        assertThat(check(space(), at(12, 10), Verdict.REQUIERE_MANTENIMIENTO)).isEmpty();
    }

    @Test
    void aSpaceReviewBeforeTheSlotEndsIsRejected() {
        assertThat(check(space(), at(11, 59), Verdict.SIN_NOVEDAD)).contains(CheckOutRejection.SLOT_NOT_FINISHED);
    }

    @Test
    void aSpaceReviewWithoutVerdictIsRejected() {
        assertThat(check(space(), at(12, 10), null)).contains(CheckOutRejection.VERDICT_REQUIRED);
    }

    @Test
    void aSpaceThatWasCancelledOrHadAnAbsenceWasNotUsed() {
        Reservation cancelled = space().cancel(CancellationOrigin.ACADEMIC_PRIORITY);
        Reservation absent = space();
        absences.insert(Absence.registered(absent.id(), UUID.randomUUID(), at(10, 10), at(10, 11), true));

        assertThat(check(cancelled, at(12, 10), Verdict.SIN_NOVEDAD)).contains(CheckOutRejection.RESERVATION_NOT_USED);
        assertThat(check(absent, at(12, 10), Verdict.SIN_NOVEDAD)).contains(CheckOutRejection.RESERVATION_NOT_USED);
    }

    @Test
    void aVoidedAbsenceMeansThePersonDidUseTheSpace() {
        Reservation reservation = space();
        absences.insert(Absence.registered(reservation.id(), UUID.randomUUID(), at(10, 10), at(10, 11), true));
        absences.markVoided(reservation.id(), at(10, 40), "error");

        assertThat(check(reservation, at(12, 10), Verdict.SIN_NOVEDAD)).isEmpty();
    }
}
