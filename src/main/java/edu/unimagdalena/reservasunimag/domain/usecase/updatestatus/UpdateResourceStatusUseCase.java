package edu.unimagdalena.reservasunimag.domain.usecase.updatestatus;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.UseStart;
import edu.unimagdalena.reservasunimag.domain.model.resource.DisplayedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.NotifiedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChange;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason;
import edu.unimagdalena.reservasunimag.domain.port.in.UpdateResourceStatusPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryStatusNotifierPort;
import edu.unimagdalena.reservasunimag.domain.port.out.StatusChangeRepositoryPort;

import java.util.UUID;

/**
 * UC7: el registro de cada cambio de ocupación (FR-009) y, solo en el inicio de uso, el aviso al Módulo 1 (FR-004,
 * FR-012). El estado anterior se calcula con los datos del Módulo 2, sin preguntarle nada al Módulo 1: un registro de
 * auditoría no justifica una llamada a otro sistema dentro de la transacción de una reserva.
 */
public class UpdateResourceStatusUseCase implements UpdateResourceStatusPort {

    private final StatusChangeRepositoryPort statusChanges;
    private final InventoryStatusNotifierPort inventoryNotifier;
    private final Clock clock;
    private final boolean notifyUseStarted;

    /** {@code notifyUseStarted} es la válvula de operación: apagada, el inicio de uso se registra sin aviso. */
    public UpdateResourceStatusUseCase(StatusChangeRepositoryPort statusChanges,
                                       InventoryStatusNotifierPort inventoryNotifier, Clock clock,
                                       boolean notifyUseStarted) {
        this.statusChanges = statusChanges;
        this.inventoryNotifier = inventoryNotifier;
        this.clock = clock;
        this.notifyUseStarted = notifyUseStarted;
    }

    @Override
    public StatusChange record(Reservation reservation, StatusChangeReason reason) {
        var now = clock.now();
        UUID outboxMessageId = null;
        if (reason.notifiesInventory() && notifyUseStarted) {
            var startedAt = reservation.useStartedAt() == null ? now : reservation.useStartedAt();
            outboxMessageId = inventoryNotifier.useStarted(new UseStart(reservation, startedAt));
        }
        StatusChange change = new StatusChange(UUID.randomUUID(), reservation.resourceId(), reservation.category(),
                reservation.id(), reservation.startsAt(), reservation.endsAt(), previousStatus(reservation, reason),
                reason.newStatus(), reason, now,
                outboxMessageId == null ? NotifiedStatus.NOT_SENT : NotifiedStatus.PENDING, outboxMessageId);
        statusChanges.insert(change);
        return change;
    }

    private static DisplayedStatus previousStatus(Reservation reservation, StatusChangeReason reason) {
        if (reason.createsOccupancy()) {
            return DisplayedStatus.DISPONIBLE;
        }
        return reservation.origin() == ReservationOrigin.ACADEMICO
                ? DisplayedStatus.BLOQUEO_ACADEMICO
                : DisplayedStatus.RESERVADO;
    }
}
