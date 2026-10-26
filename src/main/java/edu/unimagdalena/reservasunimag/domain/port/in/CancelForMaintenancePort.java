package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.reservation.MaintenanceCancellation;

/**
 * UC4 FR-010: cancelar las reservas futuras de un recurso que queda fuera de servicio, sin penalizar a nadie. Hoy
 * nadie nos avisa de ese cambio de estado (P-20); el día que llegue el aviso, quien lo reciba llamará a este puerto.
 */
public interface CancelForMaintenancePort {

    /**
     * Lo que se cancelaría, sin tocar nada.
     *
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException si no hay reservas futuras y el Módulo 1 no lo conoce
     */
    MaintenanceCancellation preview(String resourceId);

    /** Cancela todo en una transacción, con un evento por reserva. {@code reason} va al Módulo 3 y a la auditoría. */
    MaintenanceCancellation apply(String resourceId, String reason);
}
