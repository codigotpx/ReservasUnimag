package edu.unimagdalena.reservasunimag.domain.model.reservation;

import java.time.Instant;
import java.util.Objects;

/**
 * Cómo acabaron los envíos de un reporte al Módulo 3: la misma forma en el historial de fichas (UC10 FR-006) y en el
 * de cancelaciones (UC11 FR-010). {@code nextAttemptAt} solo existe mientras está {@code PENDING} y quede un intento
 * programado; {@code lastError} es {@code null} si el último intento no falló.
 */
public record Delivery(DeliveryStatus status, int attempts, Instant firstAttemptAt, Instant deliveredAt,
                       Instant nextAttemptAt, String lastError) {

    public static final Delivery PENDING = new Delivery(DeliveryStatus.PENDING, 0, null, null, null, null);

    public Delivery {
        Objects.requireNonNull(status, "status");
    }
}
