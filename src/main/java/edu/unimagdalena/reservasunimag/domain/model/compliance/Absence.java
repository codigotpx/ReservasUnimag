package edu.unimagdalena.reservasunimag.domain.model.compliance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * La constancia de que alguien no se presentó, tal como la reportó el Módulo 3 (UC9 FR-002). La persona, el recurso y
 * el tiempo apartado son los de la reserva y no se repiten aquí. {@code resourceReleased} es {@code false} cuando el
 * reporte llegó con la franja ya terminada (FR-008). Una ausencia anulada (FR-012) sigue guardada, con su fecha y su
 * motivo.
 */
public record Absence(UUID reservationId, UUID sourceEventId, Instant verifiedAt, Instant reportedAt,
                      boolean resourceReleased, boolean voided, Instant voidedAt, String voidedReason) {

    public Absence {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
        Objects.requireNonNull(reportedAt, "reportedAt");
        if (voided != (voidedAt != null)) {
            throw new IllegalArgumentException("una ausencia anulada lleva la fecha de la anulación, y solo ella");
        }
    }

    public static Absence registered(UUID reservationId, UUID sourceEventId, Instant verifiedAt, Instant reportedAt,
                                     boolean resourceReleased) {
        return new Absence(reservationId, sourceEventId, verifiedAt, reportedAt, resourceReleased, false, null, null);
    }
}
