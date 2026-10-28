package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.usecase.reserveresources.ReservationResult;

/** Persistencia de reservas: la confirmación hace la transacción atómica (bloqueo, cupo, ocupación, INSERT). */
public interface ReservationRepositoryPort {

    /** Confirma la reserva y devuelve el resultado del dominio (FR-004, SC-004). */
    ReservationResult confirm(Reservation reservation);
}
