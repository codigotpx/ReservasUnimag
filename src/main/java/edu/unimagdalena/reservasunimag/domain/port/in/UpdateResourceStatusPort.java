package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChange;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason;

/**
 * UC7, el {@code <<include>>} de quien cambia una ocupación: deja el registro de FR-009 y, solo en el inicio de uso, el
 * aviso al Módulo 1. La ocupación en sí ya la escribió quien invoca; el estado de un recurso no se guarda, se deduce de
 * las ocupaciones.
 */
public interface UpdateResourceStatusPort {

    /** Debe llamarse dentro de la transacción que cambió la ocupación, con la reserva ya en su estado nuevo. */
    StatusChange record(Reservation reservation, StatusChangeReason reason);
}
