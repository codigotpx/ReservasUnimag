package edu.unimagdalena.reservasunimag.domain.model.reservation;

import java.time.Instant;

/**
 * Detalle de la reserva de un activo. La recogida y el vencimiento son la ocupación de la reserva
 * ({@code startsAt} y {@code endsAt}); aquí van el plazo aplicado y lo que pasó después.
 * {@code pickedUpAt} y {@code returnedAt} son {@code null} mientras no ocurran.
 */
public record Loan(int termBusinessDays, Instant pickedUpAt, Instant returnedAt) {

    public static Loan reserved(int termBusinessDays) {
        return new Loan(termBusinessDays, null, null);
    }

    public boolean pickedUp() {
        return pickedUpAt != null;
    }

    public boolean returned() {
        return returnedAt != null;
    }
}
