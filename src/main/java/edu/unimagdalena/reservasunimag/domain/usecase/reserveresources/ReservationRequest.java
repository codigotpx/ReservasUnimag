package edu.unimagdalena.reservasunimag.domain.usecase.reserveresources;

import edu.unimagdalena.reservasunimag.domain.model.reservation.LoanPeriod;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.user.User;

/** Comando de UC2: el recurso, la franja (espacio) o la recogida (activo), el origen y el titular. */
public record ReservationRequest(String resourceId, ReservationOrigin origin, User holder, TimeSlot timeSlot, LoanPeriod loanPeriod) {

    public boolean isAcademic() {
        return origin == ReservationOrigin.ACADEMICO;
    }
}
