package edu.unimagdalena.reservasunimag.domain.model.compliance;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;

/**
 * Una reserva a la que le falta su check-out: un préstamo vencido sin devolver (FR-009) o un espacio terminado sin
 * revisar (FR-014). {@code holder} es {@code null} en un bloqueo académico y {@code resourceName} cuando el Módulo 1 no
 * respondió.
 */
public record PendingCheckOut(Reservation reservation, ReservationHolder holder, String resourceName) {

    public PendingCheckOut withResourceName(String name) {
        return new PendingCheckOut(reservation, holder, name);
    }
}
