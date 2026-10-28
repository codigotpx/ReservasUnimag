package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;

/** Deja escrita la ocupación / estado de un recurso; UC2 solo lo usa como <<include>> al confirmar (UC7). */
public interface UpdateResourceStatusPort {

    /** Registra el cambio de estado que representa la ocupación escrita. */
    void updateStatus(Reservation reservation, ReservationStatus status);
}
