package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.reservation.MyReservations;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;

public interface ListMyReservationsPort {

    MyReservations list(CurrentUser user, boolean onlyActive);
}
