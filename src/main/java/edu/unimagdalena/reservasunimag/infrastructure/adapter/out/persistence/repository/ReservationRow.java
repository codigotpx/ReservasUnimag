package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;

import java.time.Instant;
import java.util.UUID;

/**
 * Una reserva leída con su préstamo, si lo tiene. Es una proyección y no una entidad a propósito: los cambios de
 * estado van por {@code UPDATE} de JPQL o por adaptadores que aún usan SQL, y una entidad cargada antes en la misma
 * transacción devolvería los valores viejos. Las columnas del préstamo son {@code null} cuando no hay préstamo.
 */
public record ReservationRow(
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
        Instant returnedAt) {
}
