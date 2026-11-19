package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Delivery;
import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordFilter;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordPage;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRecordRepositoryPort;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.ReservationRecordJpaEntity;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.DeliveryCounts;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.ReservationRecordJpaRepository;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.ReservationRecordRow;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * El historial de fichas reportadas (UC10 FR-006), con JPA. La clase de una ficha académica se lee de
 * {@code academic_block} en vez de copiarse. Participa en la transacción de la reserva; no abre ninguna.
 */
@Component
class ReservationRecordPersistenceAdapter implements ReservationRecordRepositoryPort {

    private final ReservationRecordJpaRepository records;

    ReservationRecordPersistenceAdapter(ReservationRecordJpaRepository records) {
        this.records = records;
    }

    @Override
    public void insert(ReservationRecordReport report) {
        try {
            records.saveAndFlush(new ReservationRecordJpaEntity(report.reservationId(), report.eventId(),
                    report.origin(), report.sanctionable(), report.holder() == null ? null : report.holder().userId(),
                    report.resourceId(), report.category(), report.startsAt(), report.endsAt(),
                    report.loanTermBusinessDays(), report.sentAt()));
        } catch (DataAccessException failure) {
            if (SqlStates.has(failure, SqlStates.UNIQUE_VIOLATION)) {
                throw new IllegalStateException("la reserva " + report.reservationId() + " ya tiene ficha", failure);
            }
            throw failure;
        }
    }

    @Override
    public Optional<ReservationRecordReport> findByReservation(UUID reservationId) {
        return records.findRow(reservationId).map(ReservationRecordPersistenceAdapter::toReport);
    }

    @Override
    public ReservationRecordPage search(ReservationRecordFilter filter, PageRequest page) {
        // Días de Bogotá, ambos incluidos: desde el inicio de from hasta antes del inicio del día siguiente a to.
        Instant from = filter.from() == null ? null : filter.from().atStartOfDay(TimeSlot.ZONE).toInstant();
        Instant to = filter.to() == null ? null : filter.to().plusDays(1).atStartOfDay(TimeSlot.ZONE).toInstant();

        DeliveryCounts counts = records.summarize(filter.origin(), filter.delivery(), from, to);
        var summary = new ReservationRecordPage.Summary(counts.total(), counts.sent(), counts.pending(),
                counts.failed(), counts.sanctionable());
        List<ReservationRecordReport> items = records.search(filter.origin(), filter.delivery(), from, to,
                        org.springframework.data.domain.PageRequest.of(page.page() - 1, page.size())).stream()
                .map(ReservationRecordPersistenceAdapter::toReport)
                .toList();
        return new ReservationRecordPage(new Page<>(items, page.page(), page.size(), summary.total()), summary);
    }

    private static ReservationRecordReport toReport(ReservationRecordRow row) {
        ReservationHolder holder = row.holderUserId() == null ? null
                : new ReservationHolder(row.holderUserId(), row.holderCode(), row.holderName(), row.holderRole());
        AcademicData academic = row.courseCode() == null ? null : new AcademicData(row.courseCode(), row.course(),
                row.groupCode(), row.program(), row.instructor());
        return new ReservationRecordReport(row.reservationId(), row.eventId(), row.origin(), row.resourceId(), null,
                row.resourceCategory(), holder, academic, row.occupancyStartsAt(), row.occupancyEndsAt(),
                row.loanTermBusinessDays(), row.sentAt(),
                new Delivery(row.deliveryStatus(), row.attempts(), row.firstAttemptAt(), row.deliveredAt(),
                        row.deliveryStatus() == DeliveryStatus.PENDING ? row.nextAttemptAt() : null,
                        row.lastError()));
    }
}
