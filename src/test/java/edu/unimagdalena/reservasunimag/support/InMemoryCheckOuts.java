package edu.unimagdalena.reservasunimag.support;

import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOut;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReturns;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReviewFilter;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReviews;
import edu.unimagdalena.reservasunimag.domain.port.out.CheckOutRepositoryPort;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Check-out en memoria, con la clave primaria por reserva. Las listas de pendientes se prueban contra PostgreSQL. */
public final class InMemoryCheckOuts implements CheckOutRepositoryPort {

    public final Map<UUID, CheckOut> stored = new LinkedHashMap<>();

    @Override
    public void insert(CheckOut checkOut) {
        if (stored.putIfAbsent(checkOut.reservationId(), checkOut) != null) {
            throw new IllegalStateException("check-out repetido para " + checkOut.reservationId());
        }
    }

    @Override
    public Optional<CheckOut> findByReservation(UUID reservationId) {
        return Optional.ofNullable(stored.get(reservationId));
    }

    @Override
    public PendingReturns pendingReturns(Instant now, Duration lossThreshold, PageRequest page) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PendingReviews pendingReviews(PendingReviewFilter filter, Instant now, PageRequest page) {
        throw new UnsupportedOperationException();
    }
}
