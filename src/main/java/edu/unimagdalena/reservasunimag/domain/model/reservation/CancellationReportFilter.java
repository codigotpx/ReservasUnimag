package edu.unimagdalena.reservasunimag.domain.model.reservation;

import java.time.LocalDate;

/** Filtros del listado de auditoría, todos opcionales. Las fechas son días de Bogotá, ambos incluidos. */
public record CancellationReportFilter(CancellationOrigin origin, DeliveryStatus delivery, LocalDate from,
                                       LocalDate to) {
}
