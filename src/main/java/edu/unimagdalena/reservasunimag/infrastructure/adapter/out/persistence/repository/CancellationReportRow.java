package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;

import java.time.Instant;
import java.util.UUID;

/**
 * Una cancelación leída con su titular y el próximo intento de su evento pendiente. Es una proyección por lo mismo
 * que {@link ReservationRecordRow}. Las columnas del titular son {@code null} en un bloqueo académico.
 */
public record CancellationReportRow(
        UUID reservationId,
        UUID eventId,
        CancellationOrigin origin,
        String reason,
        Integer noticeMinutes,
        Instant cancelledAt,
        String resourceId,
        ResourceCategory resourceCategory,
        UUID holderUserId,
        Instant releasedStartsAt,
        Instant releasedEndsAt,
        DeliveryStatus deliveryStatus,
        int attempts,
        Instant firstAttemptAt,
        Instant deliveredAt,
        String lastError,
        String holderCode,
        String holderName,
        Role holderRole,
        Instant nextAttemptAt) {
}
