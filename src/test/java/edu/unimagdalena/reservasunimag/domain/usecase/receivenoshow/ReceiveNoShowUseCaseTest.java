package edu.unimagdalena.reservasunimag.domain.usecase.receivenoshow;

import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowAck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowPolicy;
import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowRejection;
import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowReport;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationAlreadyClosedException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Cancellation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.model.user.User;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;
import edu.unimagdalena.reservasunimag.domain.port.out.UserRepositoryPort;
import edu.unimagdalena.reservasunimag.support.CancellationFakes.InMemoryReservations;
import edu.unimagdalena.reservasunimag.support.CancellationFakes.RecordingNotifier;
import edu.unimagdalena.reservasunimag.support.InMemoryAbsences;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.STUDENT;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC9 con puertos falsos: registrar, las razones para no hacerlo y los bordes. Ahora son las 10:11. */
class ReceiveNoShowUseCaseTest {

    private Instant now = at(10, 11);
    private final Clock clock = () -> now;

    private final UserRepositoryPort users = new UserRepositoryPort() {
        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findById(UUID id) {
            return Optional.of(new User(id, STUDENT.code(), "Estudiante de Prueba", "e@unimag.edu.co", "hash",
                    Role.ESTUDIANTE, true, at(8, 0)));
        }
    };

    private final InMemoryReservations reservations = new InMemoryReservations();
    private final InMemoryAbsences absences = new InMemoryAbsences();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private int transactions;
    private final TransactionPort transactionPort = new TransactionPort() {
        @Override
        public <T> T required(Supplier<T> work) {
            transactions++;
            return work.get();
        }
    };
    private final ReceiveNoShowUseCase useCase = new ReceiveNoShowUseCase(reservations, absences, users, notifier,
            new NoShowPolicy(Duration.ofMinutes(10)), transactionPort, clock);

    private Reservation space(int startHour, int endHour) {
        return reservations.add(Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), "ESP-0107",
                ResourceCategory.ESPACIO, at(startHour, 0), at(endHour, 0), at(7, 0), null));
    }

    private Reservation asset(Loan loan) {
        return reservations.add(Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), "ACT-000913",
                ResourceCategory.ACTIVO, at(10, 0), at(10, 0).plus(Duration.ofDays(3)), at(7, 0), loan));
    }

    private static NoShowReport report(Reservation reservation, Instant verifiedAt) {
        return new NoShowReport(UUID.randomUUID(), reservation.id(), verifiedAt);
    }

    @Test
    void scenario1TheAbsenceIsRecordedTheReservationFinishesAndTheAckIsQueued() {
        Reservation reservation = space(10, 12);
        NoShowReport report = report(reservation, at(10, 10).plusSeconds(30));

        NoShowAck ack = useCase.receive(report);

        assertThat(ack.accepted()).isTrue();
        assertThat(ack.duplicate()).isFalse();
        assertThat(ack.holderCode()).isEqualTo(STUDENT.code());
        assertThat(ack.absence().resourceReleased()).isTrue();
        assertThat(ack.absence().reportedAt()).isEqualTo(now);
        assertThat(ack.absence().verifiedAt()).isEqualTo(report.verifiedAt());
        assertThat(absences.stored.get(reservation.id()).sourceEventId()).isEqualTo(report.sourceEventId());
        // FINALIZADA: deja de ocupar el recurso y el cupo sin decir que se canceló (FR-004).
        assertThat(reservations.stored.get(reservation.id()).status()).isEqualTo(ReservationStatus.FINALIZADA);
        assertThat(reservations.quotaOf(STUDENT.id(), now).used()).isZero();
        assertThat(notifier.noShowAcks).containsExactly(ack);
        assertThat(notifier.cancelled).isEmpty();
    }

    @Test
    void anAssetNobodyPickedUpReleasesTheWholeLoanAtOnce() {
        Reservation loan = asset(Loan.reserved(3));

        NoShowAck ack = useCase.receive(report(loan, at(10, 10)));

        assertThat(ack.accepted()).isTrue();
        assertThat(reservations.stored.get(loan.id()).status()).isEqualTo(ReservationStatus.FINALIZADA);
        assertThat(reservations.quotaOf(STUDENT.id(), now).used()).isZero();
    }

    @Test
    void aReportBeforeTheTenMinutesIsTooEarlyAndSaysFromWhen() {
        Reservation reservation = space(10, 12);
        now = at(10, 4);

        NoShowAck ack = useCase.receive(report(reservation, at(10, 4)));

        assertThat(ack.accepted()).isFalse();
        assertThat(ack.rejection()).isEqualTo(NoShowRejection.TOO_EARLY);
        assertThat(ack.acceptedFrom()).isEqualTo(at(10, 10));
        assertThat(ack.detail()).isEqualTo("El reporte se admite a partir de las 10:10. La persona todavía está a "
                + "tiempo de llegar.");
        assertThat(absences.stored).isEmpty();
        assertThat(reservations.stored.get(reservation.id()).status()).isEqualTo(ReservationStatus.CONFIRMADA);
        assertThat(notifier.noShowAcks).containsExactly(ack);
    }

    @Test
    void aCancelledReservationNeverGetsAnAbsenceWhoeverCancelledIt() {
        for (CancellationOrigin origin : CancellationOrigin.values()) {
            Reservation reservation = space(10, 12);
            reservations.markCancelled(reservation.id(), new Cancellation(origin, "x",
                    origin == CancellationOrigin.HOLDER ? 40 : null, at(9, 0)));

            NoShowAck ack = useCase.receive(report(reservation, at(10, 11)));

            assertThat(ack.rejection()).isEqualTo(NoShowRejection.RESERVATION_CANCELLED);
            assertThat(ack.reservationStatus()).isEqualTo(origin.status());
        }
        assertThat(absences.stored).isEmpty();
        assertThat(notifier.noShowAcks).extracting(NoShowAck::detail).containsExactly(
                "La reserva la canceló su titular a tiempo, así que no corresponde ninguna ausencia.",
                "La reserva se canceló por prioridad académica, así que no corresponde ninguna ausencia.",
                "La reserva se canceló porque el recurso quedó fuera de servicio, así que no corresponde ninguna "
                        + "ausencia.");
    }

    @Test
    void aRepeatedReportIsAcceptedAsDuplicateWithTheFirstAbsenceAndNoSecondRow() {
        Reservation reservation = space(10, 12);
        NoShowAck first = useCase.receive(report(reservation, at(10, 10)));
        now = at(10, 30);

        NoShowAck second = useCase.receive(report(reservation, at(10, 25)));

        assertThat(second.accepted()).isTrue();
        assertThat(second.duplicate()).isTrue();
        assertThat(second.absence()).isEqualTo(first.absence());
        assertThat(absences.stored).hasSize(1);
        assertThat(notifier.noShowAcks).hasSize(2);
    }

    @Test
    void aReservationOfSeveralHoursGetsASingleAbsence() {
        Reservation fourHours = space(10, 14);
        useCase.receive(report(fourHours, at(10, 10)));
        now = at(12, 0);
        useCase.receive(report(fourHours, at(11, 55)));

        assertThat(absences.stored).hasSize(1);
    }

    @Test
    void aLateReportIsRecordedAnywayButThereIsNothingToRelease() {
        Reservation reservation = space(10, 12);
        now = at(15, 0);

        NoShowAck ack = useCase.receive(report(reservation, at(10, 12)));

        assertThat(ack.accepted()).isTrue();
        assertThat(ack.absence().resourceReleased()).isFalse();
        assertThat(absences.stored).hasSize(1);
    }

    @Test
    void anAssetAlreadyPickedUpOrReturnedHasNoAbsence() {
        Reservation inHands = asset(new Loan(3, at(10, 2), null));
        Reservation returned = asset(new Loan(3, at(10, 2), at(10, 50)));

        assertThat(useCase.receive(report(inHands, at(10, 10))).rejection())
                .isEqualTo(NoShowRejection.ALREADY_PICKED_UP);
        assertThat(useCase.receive(report(returned, at(10, 10))).rejection())
                .isEqualTo(NoShowRejection.ALREADY_RETURNED);
        assertThat(absences.stored).isEmpty();
    }

    @Test
    void anAcademicBlockHasNoHolderToBlame() {
        Reservation block = reservations.add(new Reservation(UUID.randomUUID(), null, "ESP-0107",
                ResourceCategory.ESPACIO, ReservationOrigin.ACADEMICO, ReservationStatus.CONFIRMADA, at(10, 0),
                at(12, 0), at(7, 0), null));

        assertThat(useCase.receive(report(block, at(10, 10))).rejection()).isEqualTo(NoShowRejection.ACADEMIC_BLOCK);
    }

    @Test
    void afterAVoidedAbsenceANewReportFindsTheReservationAlreadyFinished() {
        Reservation reservation = space(10, 12);
        useCase.receive(report(reservation, at(10, 10)));
        absences.markVoided(reservation.id(), at(10, 30), "error");

        assertThat(useCase.receive(report(reservation, at(10, 40))).rejection())
                .isEqualTo(NoShowRejection.RESERVATION_FINISHED);
    }

    @Test
    void aReservationThatDoesNotExistIsNotAcknowledgedItIsAnError() {
        assertThatThrownBy(() -> useCase.receive(new NoShowReport(UUID.randomUUID(), UUID.randomUUID(), now)))
                .isInstanceOf(ReservationNotFoundException.class);
        assertThat(notifier.noShowAcks).isEmpty();
    }

    @Test
    void ifTheReservationWasClosedByACancellationInTheMeantimeTheClosureWins() {
        Reservation reservation = space(10, 12);
        absences.closed.add(reservation.id());

        assertThatThrownBy(() -> useCase.receive(report(reservation, at(10, 10))))
                .isInstanceOf(ReservationAlreadyClosedException.class);
    }

    @Test
    void everythingHappensInOneTransaction() {
        useCase.receive(report(space(10, 12), at(10, 10)));

        assertThat(transactions).isEqualTo(1);
    }

    @Test
    void theModuleNeverDeclaresAnAbsenceByItself() {
        // Escenario 2: sin reporte, la reserva sigue a nombre de su titular aunque pasen las horas.
        Reservation reservation = space(10, 12);
        now = at(18, 0);

        assertThat(reservations.stored.get(reservation.id()).status()).isEqualTo(ReservationStatus.CONFIRMADA);
        assertThat(absences.stored).isEmpty();
        assertThat(absences.findActiveByReservations(java.util.List.of(reservation.id()))).isEmpty();
    }
}
