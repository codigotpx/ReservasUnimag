package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;

/** Caso de uso UC10: reportar la información de la reserva hacia el Módulo 3. */
public interface ReportReservationPort {

    /** Arma la ficha de la reserva y la entrega al Módulo 3. */
    void report(Reservation reservation);
}
