package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.user.User;

/** UC10: reportarle al Módulo 3 la ficha de una reserva recién creada. */
public interface ReportReservationPort {

    /** Debe llamarse dentro de la misma transacción que confirmó la reserva. */
    void reportCreated(Reservation reservation, Resource resource, User holder);

    /** La ficha de un bloqueo académico: sin titular, con su clase y marcada como no sancionable. */
    void reportAcademicCreated(Reservation reservation, Resource resource, AcademicData academic);
}
