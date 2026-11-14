package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;

import java.time.Instant;
import java.util.UUID;

/**
 * Una reserva pendiente de check-out (UC12): las mismas columnas que {@link ReservationRow} más su titular, que es
 * {@code null} en un bloqueo académico.
 */
public record PendingCheckOutRow(
        UUID id,
        UUID userId,
        String resourceId,
        ResourceCategory resourceCategory,
        ReservationOrigin origin,
        ReservationStatus status,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt,
        Instant useStartedAt,
        Integer termBusinessDays,
        Instant pickedUpAt,
        Instant returnedAt,
        String holderCode,
        String holderName,
        Role holderRole) {

    public ReservationRow reservation() {
        return new ReservationRow(id, userId, resourceId, resourceCategory, origin, status, startsAt, endsAt,
                createdAt, useStartedAt, termBusinessDays, pickedUpAt, returnedAt);
    }
}
