package edu.unimagdalena.reservasunimag.domain.model.error;

import java.util.UUID;

/**
 * La reserva no existe o no es de quien pregunta. Las dos situaciones son la misma excepción a propósito: distinguirlas
 * confirmaría que un identificador existe y es de otra persona (plan de UC4, Decisiones de diseño).
 */
public class ReservationNotFoundException extends RuntimeException {

    private final UUID reservationId;

    public ReservationNotFoundException(UUID reservationId) {
        super("La reserva no existe.");
        this.reservationId = reservationId;
    }

    public UUID reservationId() {
        return reservationId;
    }
}
