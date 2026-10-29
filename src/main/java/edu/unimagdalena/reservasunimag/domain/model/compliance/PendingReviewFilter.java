package edu.unimagdalena.reservasunimag.domain.model.compliance;

import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;

import java.time.LocalDate;

/** Filtros opcionales de las revisiones pendientes. Las fechas son días de Bogotá sobre el inicio, ambos incluidos. */
public record PendingReviewFilter(LocalDate from, LocalDate to, ReservationOrigin origin, String resourceId) {
}
