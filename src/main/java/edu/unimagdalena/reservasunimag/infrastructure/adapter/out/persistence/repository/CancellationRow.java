package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;

import java.time.Instant;

/** Lo que queda escrito de una cancelación; {@code origin} es {@code null} en una fila antigua. */
public record CancellationRow(ReservationStatus status, CancellationOrigin origin, Instant cancelledAt) {
}
