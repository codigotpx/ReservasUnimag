package edu.unimagdalena.reservasunimag.domain.model.reservation;

/**
 * Cómo quedó una reserva tras pedir cancelarla. {@code alreadyCancelled} indica que ya lo estaba y no se tocó nada
 * (edge case <em>Doble cancelación</em>); entonces {@code cancellation} es la original, sin antelación ni motivo.
 * {@code quota} es el cupo del titular después de cancelar, y {@code null} en un bloqueo académico, que no tiene.
 */
public record CancellationResult(Reservation reservation, Cancellation cancellation, Quota quota,
                                 boolean alreadyCancelled) {

    public CancellationResult withQuota(Quota quota) {
        return new CancellationResult(reservation, cancellation, quota, alreadyCancelled);
    }
}
