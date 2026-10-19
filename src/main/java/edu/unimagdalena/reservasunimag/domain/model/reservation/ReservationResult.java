package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;

/** La reserva confirmada con la ficha del recurso y cómo quedó el cupo después de confirmarla. */
public record ReservationResult(Reservation reservation, Resource resource, Quota quota) {
}
