package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowAck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowReport;

/**
 * UC9: el Módulo 3 reporta que alguien no se presentó. Se registra la ausencia y se libera el recurso, o se explica
 * por qué no; en los dos casos el acuse queda en la outbox. El Módulo 2 nunca declara ausencias por su cuenta (FR-001).
 */
public interface ReceiveNoShowPort {

    /**
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException si la reserva no existe: el evento no se puede aplicar a nadie
     */
    NoShowAck receive(NoShowReport report);
}
