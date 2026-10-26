package edu.unimagdalena.reservasunimag.domain.usecase.receivecheckout;

import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOut;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutAck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutKind;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutRejection;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutReport;
import edu.unimagdalena.reservasunimag.domain.model.compliance.Verdict;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.port.in.CancelForMaintenancePort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReceiveCheckOutPort;
import edu.unimagdalena.reservasunimag.domain.port.out.CheckOutRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.Module3NotifierPort;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;

import java.time.Instant;
import java.util.Optional;

/**
 * UC12. Con la fila de la reserva bloqueada aplica el check-out o explica por qué no, y deja el acuse en la outbox.
 * <ul>
 *   <li>Un activo cierra su préstamo: la devolución real y la reserva {@code FINALIZADA}, con lo que el periodo y el
 *   cupo quedan libres (FR-003). No se calcula mora ni se fija el estado físico (FR-004, FR-005).</li>
 *   <li>Un espacio solo registra el dictamen; su franja ya se liberó sola al terminar (FR-012). Si el dictamen es
 *   {@code REQUIERE_MANTENIMIENTO}, se cancelan en la misma transacción las reservas que el espacio tenía por delante
 *   (spec 13).</li>
 * </ul>
 */
public class ReceiveCheckOutUseCase implements ReceiveCheckOutPort {

    static final String MAINTENANCE_REASON = "La revisión del espacio después de su uso dictaminó que requiere "
            + "mantenimiento.";

    private final ReservationRepositoryPort reservations;
    private final CheckOutRepositoryPort checkOuts;
    private final CheckOutValidator validator;
    private final CancelForMaintenancePort cancelForMaintenance;
    private final Module3NotifierPort notifier;
    private final TransactionPort transactions;
    private final Clock clock;

    public ReceiveCheckOutUseCase(ReservationRepositoryPort reservations, CheckOutRepositoryPort checkOuts,
                                  CheckOutValidator validator, CancelForMaintenancePort cancelForMaintenance,
                                  Module3NotifierPort notifier, TransactionPort transactions, Clock clock) {
        this.reservations = reservations;
        this.checkOuts = checkOuts;
        this.validator = validator;
        this.cancelForMaintenance = cancelForMaintenance;
        this.notifier = notifier;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public CheckOutAck receive(CheckOutReport report) {
        return transactions.required(() -> {
            Reservation reservation = reservations.lockById(report.reservationId())
                    .orElseThrow(() -> new ReservationNotFoundException(report.reservationId()));
            CheckOutAck ack = apply(reservation, report, clock.now());
            notifier.checkOutAcknowledged(ack);
            return ack;
        });
    }

    private CheckOutAck apply(Reservation reservation, CheckOutReport report, Instant now) {
        CheckOutKind kind = CheckOutKind.of(reservation.category());
        // Un check-out repetido no cierra dos veces ni libera dos veces el cupo (FR-007, SC-003).
        Optional<CheckOut> existing = checkOuts.findByReservation(reservation.id());
        if (existing.isPresent()) {
            return CheckOutAck.accepted(reservation, report.sourceEventId(), existing.get(), true, now);
        }
        Optional<CheckOutRejection> rejection = validator.rejectionFor(reservation, report);
        if (rejection.isPresent()) {
            return rejection.get() == CheckOutRejection.SLOT_NOT_FINISHED
                    ? CheckOutAck.slotNotFinished(reservation, report.sourceEventId(), now)
                    : CheckOutAck.rejected(reservation, report.sourceEventId(), rejection.get(), kind, now);
        }

        CheckOut checkOut;
        if (kind == CheckOutKind.ASSET_RETURN) {
            checkOut = new CheckOut(reservation.id(), kind, report.sourceEventId(), report.occurredAt(), now, null,
                    reservation.endsAt());
            checkOuts.insert(checkOut);
            reservations.markReturned(reservation.id(), report.occurredAt());
        } else {
            checkOut = new CheckOut(reservation.id(), kind, report.sourceEventId(), report.occurredAt(), now,
                    report.verdict(), null);
            checkOuts.insert(checkOut);
            if (report.verdict() == Verdict.REQUIERE_MANTENIMIENTO) {
                cancelUpcoming(reservation.resourceId());
            }
        }
        return CheckOutAck.accepted(reservation, report.sourceEventId(), checkOut, false, now);
    }

    private void cancelUpcoming(String resourceId) {
        try {
            cancelForMaintenance.apply(resourceId, MAINTENANCE_REASON);
        } catch (ResourceNotFoundException nothingAhead) {
            // Sin reservas por delante y sin ficha en el Módulo 1: no hay nada que cancelar.
        }
    }
}
