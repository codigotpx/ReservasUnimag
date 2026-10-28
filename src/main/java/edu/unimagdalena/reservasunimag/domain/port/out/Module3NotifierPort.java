package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;

/** Entrega la reserva creada hacia el Módulo 3 (UC10: outbox; por ahora el aviso directo). */
public interface Module3NotifierPort {

    /** Notifica la ficha de la reserva creada hacia el Módulo 3. */
    void notifyReservationCreated(Reservation reservation);
}
