package edu.unimagdalena.reservasunimag.domain.model.reservation;

import java.time.Instant;

/** Período de préstamo de un activo: recogida → vencimiento, con el plazo aplicado. */
public record LoanPeriod(Instant pickup, Instant dueAt, int termBusinessDays) {
}
