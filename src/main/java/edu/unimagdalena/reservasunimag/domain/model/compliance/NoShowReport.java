package edu.unimagdalena.reservasunimag.domain.model.compliance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Lo que el Módulo 3 nos dice: que el titular de la reserva no se presentó, comprobado en sitio a {@code verifiedAt}. */
public record NoShowReport(UUID sourceEventId, UUID reservationId, Instant verifiedAt) {

    public NoShowReport {
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
    }
}
