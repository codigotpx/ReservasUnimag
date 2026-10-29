package edu.unimagdalena.reservasunimag.domain.model.error;

/** El cuerpo no corresponde a la categoría del recurso: franja para un activo o recogida para un espacio. */
public class ReservationShapeException extends RuntimeException {

    public ReservationShapeException(String message) {
        super(message);
    }
}
