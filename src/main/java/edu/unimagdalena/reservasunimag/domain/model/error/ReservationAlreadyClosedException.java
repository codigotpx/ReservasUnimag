package edu.unimagdalena.reservasunimag.domain.model.error;

import java.util.UUID;

/**
 * La reserva ya tiene un cierre registrado de otro tipo: no se puede cancelar una reserva con ausencia, ni registrar
 * la ausencia de una cancelada (UC11 FR-011).
 */
public class ReservationAlreadyClosedException extends RuntimeException {

    private final UUID reservationId;

    public ReservationAlreadyClosedException(UUID reservationId, Throwable cause) {
        super("La reserva " + reservationId + " ya está cerrada.", cause);
        this.reservationId = reservationId;
    }

    public UUID reservationId() {
        return reservationId;
    }
}
