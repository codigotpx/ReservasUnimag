package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutAck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutReport;

/**
 * UC12: el Módulo 3 reporta que un activo volvió o que un espacio se revisó. Es lo único que cierra un préstamo
 * (FR-006). Aplicado o no, el acuse queda en la outbox.
 */
public interface ReceiveCheckOutPort {

    /**
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException si la reserva no existe: el evento no se puede aplicar a nada
     */
    CheckOutAck receive(CheckOutReport report);
}
