package edu.unimagdalena.reservasunimag.domain.model.upload;

import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;

import java.time.Instant;
import java.util.UUID;

/**
 * Una reserva estudiantil que una clase desplazaría. Lleva el titular con nombre porque FR-008 pide mostrar cuáles se
 * cancelarían, y solo Dirección de Programa lo ve. {@code holder} es {@code null} si la persona ya no está en la base.
 */
public record AffectedReservation(UUID reservationId, ReservationHolder holder, Instant start, Instant end,
                                  ResourceCategory category) {
}
