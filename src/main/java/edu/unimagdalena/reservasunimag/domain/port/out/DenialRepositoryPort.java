package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Denial;

public interface DenialRepositoryPort {

    /** Se guarda en su propia transacción: tiene que sobrevivir al rollback de la reserva que se negó. */
    void record(Denial denial);
}
