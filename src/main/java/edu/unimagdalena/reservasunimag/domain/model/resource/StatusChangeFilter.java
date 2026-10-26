package edu.unimagdalena.reservasunimag.domain.model.resource;

import java.time.LocalDate;

/** Filtros opcionales del historial de un recurso. Las fechas son días de Bogotá sobre {@code changedAt}. */
public record StatusChangeFilter(LocalDate from, LocalDate to, StatusChangeReason reason) {
}
