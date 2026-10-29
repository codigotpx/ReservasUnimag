package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Cancellation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.user.User;

/** UC11: dejar constancia de una cancelación ejecutada y encolar su reporte al Módulo 3. */
public interface ReportCancellationPort {

    /**
     * Debe llamarse dentro de la misma transacción que canceló la reserva. {@code resourceName} y {@code holder}
     * pueden ser {@code null}: el Módulo 1 no respondió, o la reserva era un bloqueo académico.
     *
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.ReservationAlreadyClosedException si la reserva ya tiene una ausencia registrada
     */
    void reportCancelled(Reservation cancelled, Cancellation cancellation, String resourceName, User holder);
}
