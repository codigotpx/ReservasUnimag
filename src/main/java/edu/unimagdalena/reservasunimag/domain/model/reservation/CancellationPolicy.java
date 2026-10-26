package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.error.CancellationDeniedException;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Cuándo se puede cancelar una reserva confirmada. El instante de referencia es su inicio, que en un activo es la
 * hora de recogida. El borde es inclusivo: una petición que llega exactamente {@code minNotice} antes se acepta, porque
 * favorece a quien libera el recurso (plan de UC4, Decisiones de diseño).
 */
public record CancellationPolicy(Duration minNotice) {

    public CancellationPolicy {
        Objects.requireNonNull(minNotice, "minNotice");
        if (minNotice.isNegative() || minNotice.isZero()) {
            throw new IllegalArgumentException("la antelación mínima debe ser positiva");
        }
    }

    /**
     * Lo que impide cancelar en cualquier rama, la pida quien la pida: una reserva que ya terminó, o un activo que
     * ya está en manos de alguien (UC4 FR-008). Se llama solo sobre una reserva que no esté ya cancelada.
     */
    public void requireOpen(Reservation reservation) {
        if (reservation.status() == ReservationStatus.FINALIZADA) {
            throw CancellationDeniedException.finished();
        }
        if (reservation.inHands()) {
            throw CancellationDeniedException.assetAlreadyPickedUp(reservation.loan().pickedUpAt());
        }
    }

    /** Las reglas de la rama del titular: además de {@link #requireOpen}, el plazo (UC4 FR-003, FR-007). */
    public void requireCancellableByHolder(Reservation reservation, Instant now) {
        requireOpen(reservation);
        if (now.isAfter(deadline(reservation))) {
            throw CancellationDeniedException.tooLate(minNotice, reservation.startsAt(),
                    reservation.category() == ResourceCategory.ACTIVO, !now.isBefore(reservation.startsAt()));
        }
    }

    /** Si el titular todavía puede cancelarla; es lo que ve la lista de mis reservas. */
    public boolean cancellableByHolder(Reservation reservation, Instant now) {
        return reservation.status() == ReservationStatus.CONFIRMADA
                && !reservation.inHands()
                && !now.isAfter(deadline(reservation));
    }

    /** Minutos completos entre {@code now} y el instante de referencia (UC11 FR-004). */
    public int noticeMinutes(Reservation reservation, Instant now) {
        return (int) Duration.between(now, reservation.startsAt()).toMinutes();
    }

    private Instant deadline(Reservation reservation) {
        return reservation.startsAt().minus(minNotice);
    }
}
