package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOut;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReturns;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReviewFilter;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReviews;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Los check-out de UC12 y las dos consultas de pendientes. Las escrituras van dentro de {@link TransactionPort#required}. */
public interface CheckOutRepositoryPort {

    void insert(CheckOut checkOut);

    Optional<CheckOut> findByReservation(UUID reservationId);

    /** Préstamos entregados, sin devolver y ya vencidos a {@code now}; sin nombre de recurso. */
    PendingReturns pendingReturns(Instant now, Duration lossThreshold, PageRequest page);

    /** Reservas de espacio terminadas a {@code now}, sin check-out y sin ausencia vigente; sin nombre de recurso. */
    PendingReviews pendingReviews(PendingReviewFilter filter, Instant now, PageRequest page);
}
