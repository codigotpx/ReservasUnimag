package edu.unimagdalena.reservasunimag.domain.model.compliance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que el Módulo 3 nos dice: que el activo de la reserva volvió, o que su espacio se revisó, a {@code occurredAt}.
 * {@code verdict} es {@code null} si no vino. {@code carriesDamage} indica que el evento traía una descripción de daños,
 * que no se guarda en ningún caso: solo sirve para rechazarlo (FR-010).
 */
public record CheckOutReport(UUID sourceEventId, UUID reservationId, Instant occurredAt, Verdict verdict,
                             boolean carriesDamage) {

    public CheckOutReport {
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
