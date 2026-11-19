package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;

import java.time.Instant;
import java.util.UUID;

/**
 * Una ficha leída con su titular, la clase de su bloqueo académico y el próximo intento de su evento pendiente. Es
 * una proyección porque {@code OutboxPublisher} actualiza la entrega con SQL. Las columnas del titular son
 * {@code null} en una ficha académica, y las de la clase en una estudiantil.
 */
public record ReservationRecordRow(
        UUID reservationId,
        UUID eventId,
        ReservationOrigin origin,
        UUID holderUserId,
        String resourceId,
        ResourceCategory resourceCategory,
        Instant occupancyStartsAt,
        Instant occupancyEndsAt,
        Integer loanTermBusinessDays,
        Instant sentAt,
        DeliveryStatus deliveryStatus,
        int attempts,
        Instant firstAttemptAt,
        Instant deliveredAt,
        String lastError,
        String holderCode,
        String holderName,
        Role holderRole,
        String courseCode,
        String course,
        String groupCode,
        String program,
        String instructor,
        Instant nextAttemptAt) {
}
