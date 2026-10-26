package edu.unimagdalena.reservasunimag.domain.usecase.receivenoshow;

import edu.unimagdalena.reservasunimag.domain.model.compliance.Absence;
import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowAck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowPolicy;
import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowRejection;
import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowReport;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason;
import edu.unimagdalena.reservasunimag.domain.model.user.User;
import edu.unimagdalena.reservasunimag.domain.port.in.ReceiveNoShowPort;
import edu.unimagdalena.reservasunimag.domain.port.in.UpdateResourceStatusPort;
import edu.unimagdalena.reservasunimag.domain.port.out.AbsenceRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.Module3NotifierPort;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;
import edu.unimagdalena.reservasunimag.domain.port.out.UserRepositoryPort;

import java.time.Instant;
import java.util.Optional;

/**
 * UC9. Con la fila de la reserva bloqueada decide entre registrar la ausencia o rechazar el reporte, y en los dos casos
 * deja el acuse en la outbox. Registrar es cerrar la reserva como ausencia —la tabla de cierres impide que conviva con
 * una cancelación (UC11 FR-011)—, guardar la constancia y dejar la reserva {@code FINALIZADA}, con lo que el recurso y
 * el cupo quedan libres (FR-004). No se aplica ninguna sanción: eso lo decide el Módulo 3 (FR-003).
 */
public class ReceiveNoShowUseCase implements ReceiveNoShowPort {

    private final ReservationRepositoryPort reservations;
    private final AbsenceRepositoryPort absences;
    private final UserRepositoryPort users;
    private final Module3NotifierPort notifier;
    private final UpdateResourceStatusPort updateStatus;
    private final NoShowPolicy policy;
    private final TransactionPort transactions;
    private final Clock clock;

    public ReceiveNoShowUseCase(ReservationRepositoryPort reservations, AbsenceRepositoryPort absences,
                                UserRepositoryPort users, Module3NotifierPort notifier,
                                UpdateResourceStatusPort updateStatus, NoShowPolicy policy,
                                TransactionPort transactions, Clock clock) {
        this.reservations = reservations;
        this.absences = absences;
        this.users = users;
        this.notifier = notifier;
        this.updateStatus = updateStatus;
        this.policy = policy;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public NoShowAck receive(NoShowReport report) {
        return transactions.required(() -> {
            Reservation reservation = reservations.lockById(report.reservationId())
                    .orElseThrow(() -> new ReservationNotFoundException(report.reservationId()));
            NoShowAck ack = decide(reservation, report, clock.now());
            notifier.noShowAcknowledged(ack);
            return ack;
        });
    }

    private NoShowAck decide(Reservation reservation, NoShowReport report, Instant now) {
        // Un reporte repetido no es un error: el estado que el Módulo 3 buscaba ya existe (FR-007).
        Optional<Absence> existing = absences.findByReservation(reservation.id());
        if (existing.isPresent() && !existing.get().voided()) {
            return NoShowAck.accepted(reservation, report.sourceEventId(), existing.get(),
                    holderCode(reservation), true, now);
        }
        Optional<NoShowRejection> rejection = rejectionFor(reservation);
        if (rejection.isPresent()) {
            return NoShowAck.rejected(reservation, report.sourceEventId(), rejection.get(), now);
        }
        if (!policy.admits(reservation, report.verifiedAt())) {
            return NoShowAck.tooEarly(reservation, report.sourceEventId(), policy.acceptedFrom(reservation), now);
        }

        // Si la franja ya terminó se registra igual, para el historial, pero no había nada que liberar (FR-008).
        Absence absence = Absence.registered(reservation.id(), report.sourceEventId(), report.verifiedAt(), now,
                now.isBefore(reservation.endsAt()));
        absences.insert(absence);
        reservations.markFinished(reservation.id());
        updateStatus.record(reservation, StatusChangeReason.NO_SHOW_REGISTERED);
        return NoShowAck.accepted(reservation, report.sourceEventId(), absence, holderCode(reservation), false, now);
    }

    private static Optional<NoShowRejection> rejectionFor(Reservation reservation) {
        if (reservation.status().isCancellation()) {
            return Optional.of(NoShowRejection.RESERVATION_CANCELLED);
        }
        if (reservation.origin() == ReservationOrigin.ACADEMICO) {
            return Optional.of(NoShowRejection.ACADEMIC_BLOCK);
        }
        if (reservation.loan() != null && reservation.loan().returned()) {
            return Optional.of(NoShowRejection.ALREADY_RETURNED);
        }
        if (reservation.status() == ReservationStatus.FINALIZADA) {
            return Optional.of(NoShowRejection.RESERVATION_FINISHED);
        }
        if (reservation.inHands()) {
            return Optional.of(NoShowRejection.ALREADY_PICKED_UP);
        }
        if (reservation.useStarted()) {
            return Optional.of(NoShowRejection.USE_ALREADY_STARTED);
        }
        return Optional.empty();
    }

    private String holderCode(Reservation reservation) {
        return users.findById(reservation.userId()).map(User::code).orElse(null);
    }
}
