package edu.unimagdalena.reservasunimag.domain.model.compliance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un check-out aplicado. Guarda los dos instantes: cuándo pasó y cuándo nos enteramos (FR-009). {@code verdict} solo
 * existe en una revisión de espacio y {@code dueAtSnapshot}, el vencimiento contra el que se cerró, solo en un activo.
 */
public record CheckOut(UUID reservationId, CheckOutKind kind, UUID sourceEventId, Instant occurredAt,
                       Instant receivedAt, Verdict verdict, Instant dueAtSnapshot) {

    public CheckOut {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
        if ((kind == CheckOutKind.SPACE_REVIEW) != (verdict != null)) {
            throw new IllegalArgumentException("solo la revisión de un espacio lleva dictamen, y siempre lo lleva");
        }
        if ((kind == CheckOutKind.ASSET_RETURN) != (dueAtSnapshot != null)) {
            throw new IllegalArgumentException("solo la devolución de un activo guarda el vencimiento");
        }
    }
}
