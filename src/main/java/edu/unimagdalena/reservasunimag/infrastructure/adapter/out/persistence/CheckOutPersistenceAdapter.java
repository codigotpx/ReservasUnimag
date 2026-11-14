package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOut;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingCheckOut;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReturns;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReviewFilter;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReviews;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.port.out.CheckOutRepositoryPort;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.CheckOutJpaEntity;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.mapper.ReservationMapper;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.CheckOutJpaRepository;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.PendingCheckOutRow;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.PendingCounts;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Los check-out de UC12 y las dos listas de pendientes, que son consultas JPQL sobre las reservas y no tablas
 * propias. Participa en la transacción del caso de uso; no abre ninguna.
 */
@Component
class CheckOutPersistenceAdapter implements CheckOutRepositoryPort {

    private final CheckOutJpaRepository checkOuts;

    CheckOutPersistenceAdapter(CheckOutJpaRepository checkOuts) {
        this.checkOuts = checkOuts;
    }

    @Override
    public void insert(CheckOut checkOut) {
        checkOuts.saveAndFlush(new CheckOutJpaEntity(checkOut.reservationId(), checkOut.kind(),
                checkOut.sourceEventId(), checkOut.occurredAt(), checkOut.receivedAt(), checkOut.verdict(),
                checkOut.dueAtSnapshot()));
    }

    @Override
    public Optional<CheckOut> findByReservation(UUID reservationId) {
        return checkOuts.findById(reservationId).map(entity -> new CheckOut(entity.getReservationId(),
                entity.getKind(), entity.getSourceEventId(), entity.getOccurredAt(), entity.getReceivedAt(),
                entity.getVerdict(), entity.getDueAtSnapshot()));
    }

    @Override
    public PendingReturns pendingReturns(Instant now, Duration lossThreshold, PageRequest page) {
        // Cerca del umbral: le quedan menos de 48 horas, es decir vence antes de now - umbral + 48 h.
        PendingCounts counts = checkOuts.countPendingReturns(now,
                now.minus(lossThreshold).plus(PendingReturns.NEAR_THRESHOLD));
        List<PendingCheckOut> items = checkOuts.findPendingReturns(now, pageable(page)).stream()
                .map(CheckOutPersistenceAdapter::toPending)
                .toList();
        return new PendingReturns(new Page<>(items, page.page(), page.size(), counts.first()), counts.second(),
                lossThreshold, now);
    }

    @Override
    public PendingReviews pendingReviews(PendingReviewFilter filter, Instant now, PageRequest page) {
        // Días de Bogotá, ambos incluidos: desde el inicio de from hasta antes del inicio del día siguiente a to.
        Instant from = filter.from() == null ? null : filter.from().atStartOfDay(TimeSlot.ZONE).toInstant();
        Instant to = filter.to() == null ? null : filter.to().plusDays(1).atStartOfDay(TimeSlot.ZONE).toInstant();

        PendingCounts counts = checkOuts.countPendingReviews(now, from, to, filter.origin(), filter.resourceId());
        long student = counts.first();
        long academic = counts.second();
        List<PendingCheckOut> items = checkOuts.findPendingReviews(now, from, to, filter.origin(),
                        filter.resourceId(), pageable(page)).stream()
                .map(CheckOutPersistenceAdapter::toPending)
                .toList();
        return new PendingReviews(new Page<>(items, page.page(), page.size(), student + academic), student, academic,
                now);
    }

    private static Pageable pageable(PageRequest page) {
        return org.springframework.data.domain.PageRequest.of(page.page() - 1, page.size());
    }

    private static PendingCheckOut toPending(PendingCheckOutRow row) {
        ReservationHolder holder = row.userId() == null ? null
                : new ReservationHolder(row.userId(), row.holderCode(), row.holderName(), row.holderRole());
        return new PendingCheckOut(ReservationMapper.toDomain(row.reservation()), holder, null);
    }
}
