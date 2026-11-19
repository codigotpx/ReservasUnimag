package edu.unimagdalena.reservasunimag.domain.usecase.receivecheckout;

import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutAck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutKind;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutRejection;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutReport;
import edu.unimagdalena.reservasunimag.domain.model.compliance.Verdict;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.MaintenanceCancellation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.port.in.CancelForMaintenancePort;
import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;
import edu.unimagdalena.reservasunimag.support.CancellationFakes.InMemoryReservations;
import edu.unimagdalena.reservasunimag.support.CancellationFakes.RecordingNotifier;
import edu.unimagdalena.reservasunimag.support.InMemoryAbsences;
import edu.unimagdalena.reservasunimag.support.InMemoryCheckOuts;
import edu.unimagdalena.reservasunimag.support.RecordingStatusUpdates;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.STUDENT;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC12 con puertos falsos: cerrar un préstamo, registrar una revisión, la regla del spec 13 y el acuse. */
class ReceiveCheckOutUseCaseTest {

    private Instant now = at(16, 42);
    private final InMemoryReservations reservations = new InMemoryReservations();
    private final InMemoryCheckOuts checkOuts = new InMemoryCheckOuts();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final List<String> maintenanceCalls = new ArrayList<>();
    private boolean resourceUnknown;

    private final CancelForMaintenancePort cancelForMaintenance = new CancelForMaintenancePort() {
        @Override
        public MaintenanceCancellation preview(String resourceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MaintenanceCancellation apply(String resourceId, String reason) {
            if (resourceUnknown) {
                throw new ResourceNotFoundException(resourceId);
            }
            maintenanceCalls.add(resourceId + ": " + reason);
            return new MaintenanceCancellation(resourceId, null, List.of(), List.of(), now);
        }
    };

    private final TransactionPort transactions = new TransactionPort() {
        @Override
        public <T> T required(Supplier<T> work) {
            return work.get();
        }
    };

    private final RecordingStatusUpdates statusUpdates = new RecordingStatusUpdates();
    private final ReceiveCheckOutUseCase useCase = new ReceiveCheckOutUseCase(reservations, checkOuts,
            new CheckOutValidator(new InMemoryAbsences()), cancelForMaintenance, notifier, statusUpdates,
            transactions, () -> now);

    private Reservation loanInHands() {
        return reservations.add(Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), "ACT-000913",
                ResourceCategory.ACTIVO, at(10, 0), at(10, 0).plus(Duration.ofDays(3)), at(7, 0),
                new Loan(3, at(10, 5), null)));
    }

    private Reservation space() {
        return reservations.add(Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), "ESP-0107",
                ResourceCategory.ESPACIO, at(10, 0), at(12, 0), at(7, 0), null));
    }

    private static CheckOutReport report(Reservation reservation, Instant occurredAt, Verdict verdict) {
        return new CheckOutReport(UUID.randomUUID(), reservation.id(), occurredAt, verdict, false);
    }

    @Test
    void anAssetCheckOutClosesTheLoanAndReleasesTheQuota() {
        Reservation loan = loanInHands();
        assertThat(reservations.quotaOf(STUDENT.id(), now).used()).isEqualTo(1);

        CheckOutAck ack = useCase.receive(report(loan, at(16, 40), null));

        assertThat(ack.accepted()).isTrue();
        assertThat(ack.loanClosed()).isTrue();
        Reservation closed = reservations.stored.get(loan.id());
        assertThat(closed.status()).isEqualTo(ReservationStatus.FINALIZADA);
        assertThat(closed.loan().returnedAt()).isEqualTo(at(16, 40));
        assertThat(reservations.quotaOf(STUDENT.id(), now).used()).isZero();
        var stored = checkOuts.stored.get(loan.id());
        assertThat(stored.kind()).isEqualTo(CheckOutKind.ASSET_RETURN);
        assertThat(stored.occurredAt()).isEqualTo(at(16, 40));
        assertThat(stored.receivedAt()).isEqualTo(now);
        assertThat(stored.dueAtSnapshot()).isEqualTo(loan.endsAt());
        assertThat(notifier.checkOutAcks).containsExactly(ack);
    }

    @Test
    void aRepeatedCheckOutIsAcceptedAsDuplicateAndClosesNothing() {
        Reservation loan = loanInHands();
        CheckOutAck first = useCase.receive(report(loan, at(16, 40), null));
        now = at(17, 0);

        CheckOutAck second = useCase.receive(report(loan, at(16, 55), null));

        assertThat(second.accepted()).isTrue();
        assertThat(second.duplicate()).isTrue();
        assertThat(second.loanClosed()).isFalse();
        assertThat(second.checkOut()).isEqualTo(first.checkOut());
        assertThat(checkOuts.stored).hasSize(1);
        assertThat(reservations.stored.get(loan.id()).loan().returnedAt()).isEqualTo(at(16, 40));
    }

    @Test
    void aSpaceReviewRecordsTheVerdictAndReleasesNothing() {
        Reservation reservation = space();

        CheckOutAck ack = useCase.receive(report(reservation, at(12, 10), Verdict.SIN_NOVEDAD));

        assertThat(ack.accepted()).isTrue();
        assertThat(ack.loanClosed()).isFalse();
        assertThat(checkOuts.stored.get(reservation.id()).verdict()).isEqualTo(Verdict.SIN_NOVEDAD);
        // El estado no cambia: la franja se libera sola al terminar, no con la revisión (FR-012).
        assertThat(reservations.stored.get(reservation.id()).status()).isEqualTo(ReservationStatus.CONFIRMADA);
        assertThat(maintenanceCalls).isEmpty();
    }

    @Test
    void aMaintenanceVerdictCancelsWhatTheSpaceHadAhead() {
        Reservation reservation = space();

        useCase.receive(report(reservation, at(12, 10), Verdict.REQUIERE_MANTENIMIENTO));

        assertThat(maintenanceCalls).containsExactly("ESP-0107: " + ReceiveCheckOutUseCase.MAINTENANCE_REASON);
    }

    @Test
    void aMaintenanceVerdictOnAResourceWithNothingAheadStillRecordsTheReview() {
        Reservation reservation = space();
        resourceUnknown = true;

        CheckOutAck ack = useCase.receive(report(reservation, at(12, 10), Verdict.REQUIERE_MANTENIMIENTO));

        assertThat(ack.accepted()).isTrue();
        assertThat(checkOuts.stored).containsKey(reservation.id());
    }

    @Test
    void aReviewBeforeTheEndSaysFromWhenItIsAdmitted() {
        Reservation reservation = space();
        now = at(11, 50);

        CheckOutAck ack = useCase.receive(report(reservation, at(11, 50), Verdict.SIN_NOVEDAD));

        assertThat(ack.rejection()).isEqualTo(CheckOutRejection.SLOT_NOT_FINISHED);
        assertThat(ack.acceptedFrom()).isEqualTo(at(12, 0));
        assertThat(ack.detail()).isEqualTo("La franja de esta reserva termina a las 12:00. La revisión se admite "
                + "después de esa hora.");
        assertThat(checkOuts.stored).isEmpty();
        assertThat(notifier.checkOutAcks).containsExactly(ack);
    }

    @Test
    void aRejectedAssetCheckOutLeavesTheLoanOpen() {
        Reservation loan = loanInHands();

        CheckOutAck ack = useCase.receive(new CheckOutReport(UUID.randomUUID(), loan.id(), at(16, 40),
                Verdict.SIN_NOVEDAD, false));

        assertThat(ack.rejection()).isEqualTo(CheckOutRejection.VERDICT_REQUIRED);
        assertThat(ack.detail()).contains("no lleva dictamen");
        assertThat(reservations.stored.get(loan.id()).status()).isEqualTo(ReservationStatus.CONFIRMADA);
        assertThat(reservations.stored.get(loan.id()).loan().returned()).isFalse();
    }

    @Test
    void withoutACheckOutAnOverdueLoanStaysOpenForever() {
        // FR-006 y SC-004: por mucho que pase el tiempo, sin evento el préstamo sigue abierto y contando cupo.
        Reservation loan = loanInHands();
        now = loan.endsAt().plus(Duration.ofDays(30));

        assertThat(reservations.stored.get(loan.id()).status()).isEqualTo(ReservationStatus.CONFIRMADA);
        assertThat(reservations.quotaOf(STUDENT.id(), now).used()).isEqualTo(1);
        assertThat(reservations.stored.get(loan.id()).overdue(now)).isTrue();
    }

    @Test
    void aCheckOutOfAReservationThatDoesNotExistIsAnError() {
        assertThatThrownBy(() -> useCase.receive(new CheckOutReport(UUID.randomUUID(), UUID.randomUUID(), now, null,
                false))).isInstanceOf(ReservationNotFoundException.class);
        assertThat(notifier.checkOutAcks).isEmpty();
    }

    @Test
    void closingALoanGoesThroughUc7AndASpaceReviewDoesNot() {
        useCase.receive(report(loanInHands(), at(16, 40), null));
        useCase.receive(report(space(), at(12, 10), Verdict.SIN_NOVEDAD));

        assertThat(statusUpdates.reasons()).containsExactly(
                edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason.CHECK_OUT_RECEIVED);
    }
}
