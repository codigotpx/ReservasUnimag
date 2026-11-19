package edu.unimagdalena.reservasunimag.domain.usecase.importschedule;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.ClassSession;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;

import java.time.Instant;

/** Las reglas de cruce entre una sesión y lo que ya ocupa el recurso, compartidas por la carga y la necesidad. */
final class SlotConflicts {

    private SlotConflicts() {
    }

    /** Si la reserva ocupa algo de la sesión. Un préstamo en manos de alguien ocupa sin fin (UC7 FR-011). */
    static boolean occupies(Reservation reservation, ClassSession session) {
        Instant end = reservation.inHands() ? Instant.MAX : reservation.endsAt();
        return session.resourceId().equals(reservation.resourceId())
                && session.overlaps(reservation.startsAt(), end);
    }

    /**
     * Lo que la prioridad académica no puede quitarle a nadie: un activo ya entregado, o una reserva en la que ya se
     * registró la llegada (FR-010).
     */
    static boolean untouchable(Reservation reservation) {
        return reservation.inHands() || reservation.useStarted();
    }
}
