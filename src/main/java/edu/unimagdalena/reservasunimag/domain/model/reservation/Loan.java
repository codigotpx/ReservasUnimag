package edu.unimagdalena.reservasunimag.domain.model.reservation;

import java.time.Instant;

/** Detalle de un préstamo: período de un activo y estado de la entrega. */
public record Loan(Instant pickup, Instant dueAt, int termBusinessDays, boolean pickedUp) {
}
