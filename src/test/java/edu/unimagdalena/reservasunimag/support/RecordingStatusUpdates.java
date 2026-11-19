package edu.unimagdalena.reservasunimag.support;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.resource.DisplayedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.NotifiedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChange;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason;
import edu.unimagdalena.reservasunimag.domain.port.in.UpdateResourceStatusPort;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** El {@code <<include>>} de UC7 sin base: guarda qué reservas pasaron por él y con qué motivo. */
public final class RecordingStatusUpdates implements UpdateResourceStatusPort {

    public record Call(Reservation reservation, StatusChangeReason reason) {
    }

    public final List<Call> calls = new ArrayList<>();

    @Override
    public StatusChange record(Reservation reservation, StatusChangeReason reason) {
        calls.add(new Call(reservation, reason));
        UUID outbox = reason.notifiesInventory() ? UUID.randomUUID() : null;
        return new StatusChange(UUID.randomUUID(), reservation.resourceId(), reservation.category(), reservation.id(),
                reservation.startsAt(), reservation.endsAt(), DisplayedStatus.RESERVADO, reason.newStatus(), reason,
                reservation.useStartedAt() == null ? reservation.startsAt() : reservation.useStartedAt(),
                outbox == null ? NotifiedStatus.NOT_SENT : NotifiedStatus.PENDING, outbox);
    }

    public List<StatusChangeReason> reasons() {
        return calls.stream().map(Call::reason).toList();
    }
}
