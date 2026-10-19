package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;

import java.time.Instant;

/** La ficha de una reserva recién creada, que se le reporta al Módulo 3 (UC10). */
public record ReservationRecord(Reservation reservation, Resource resource, ReservationHolder holder,
                                Instant occurredAt) {
}
