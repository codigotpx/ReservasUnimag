package edu.unimagdalena.reservasunimag.domain.model.reservation;

import java.time.Instant;
import java.util.Objects;

/**
 * Una cancelación ejecutada. {@code noticeMinutes} existe solo cuando cancela el titular: en una cancelación que la
 * persona no pidió no significa nada (UC11 edge case <em>Antelación en la cancelación automática</em>).
 * {@code reason} es {@code null} solo cuando se reconstruye una cancelación ya guardada para responder una
 * segunda petición.
 */
public record Cancellation(CancellationOrigin origin, String reason, Integer noticeMinutes, Instant cancelledAt) {

    public Cancellation {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(cancelledAt, "cancelledAt");
        if (origin != CancellationOrigin.HOLDER && noticeMinutes != null) {
            throw new IllegalArgumentException("solo la cancelación del titular lleva antelación");
        }
    }

    public ReservationStatus status() {
        return origin.status();
    }

    public boolean attributableToPerson() {
        return origin.attributableToPerson();
    }
}
