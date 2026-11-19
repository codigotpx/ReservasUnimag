package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.reservation.AcademicStarts;
import edu.unimagdalena.reservasunimag.domain.model.reservation.UseStartResult;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;

import java.util.UUID;

/**
 * UC7 FR-004: el uso de un recurso empezó. Tiene dos disparadores: la persona que registra su llegada —entrada
 * provisional mientras P-02 siga abierto— y la clase que llega a su hora. Registrar dos veces no registra ni avisa
 * nada nuevo (FR-008).
 */
public interface RegisterUseStartedPort {

    /**
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException si no existe o es de otra persona
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.UseStartRejectedException    USO-001 o USO-002
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.ReservationDeniedException   RES-004 si el Módulo 1 lo tiene en mantenimiento
     */
    UseStartResult startByHolder(CurrentUser user, UUID reservationId);

    /** Empieza el uso de los bloqueos académicos que ya llegaron a su hora; cada uno en su propia transacción. */
    AcademicStarts startDueAcademicBlocks();
}
