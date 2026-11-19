package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationResult;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;

import java.util.UUID;

/**
 * UC4: deshacer una reserva confirmada, liberar su ocupación y reportarlo al Módulo 3 (UC11). Cancelar una reserva ya
 * cancelada no es un error: responde con lo que ya había, sin volver a reportar (edge case <em>Doble cancelación</em>).
 */
public interface CancelReservationPort {

    /**
     * La rama del titular, con su plazo.
     *
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException si no existe o es de otra persona
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.CancellationDeniedException  si ya no es cancelable (CAN-001)
     */
    CancellationResult cancelByHolder(CurrentUser user, UUID reservationId);

    /**
     * Las ramas que no pide nadie: la prioridad académica (UC3) y el recurso fuera de servicio. No comprueban
     * titular ni plazo, y nunca penalizan (UC4 FR-005). {@code reason} es el texto legible que va al Módulo 3.
     *
     * @throws IllegalArgumentException                                                        si {@code origin} es {@code HOLDER}
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.CancellationDeniedException si el activo ya se entregó
     */
    CancellationResult cancelBySystem(UUID reservationId, CancellationOrigin origin, String reason);
}
