package edu.unimagdalena.reservasunimag.domain.model.reservation;

import java.time.LocalDate;

/** Filtros del listado de fichas, todos opcionales. Las fechas son días de Bogotá, ambos incluidos. */
public record ReservationRecordFilter(ReservationOrigin origin, DeliveryStatus delivery, LocalDate from,
                                      LocalDate to) {
}
