package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationAlreadyClosedException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReportFilter;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReportPage;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Delivery;
import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.port.out.CancellationReportRepositoryPort;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.CancellationReportJpaEntity;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.ReservationClosureJpaEntity;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.CancellationReportJpaRepository;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.CancellationReportRow;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.DeliveryCounts;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.ReservationClosureJpaRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * El historial de cancelaciones reportadas (UC11 FR-010) y el cierre de la reserva que impide que conviva con una
 * ausencia (FR-011), con JPA. Participa en la transacción de la cancelación; no abre ninguna.
 */
@Component
class CancellationReportPersistenceAdapter implements CancellationReportRepositoryPort {

    private final CancellationReportJpaRepository reports;
    private final ReservationClosureJpaRepository closures;

    CancellationReportPersistenceAdapter(CancellationReportJpaRepository reports,
                                         ReservationClosureJpaRepository closures) {
        this.reports = reports;
        this.closures = closures;
    }

    @Override
    public void insert(CancellationReport report) {
        try {
            // El flush hace que el cierre repetido falle aquí, antes de escribir la constancia.
            closures.saveAndFlush(new ReservationClosureJpaEntity(report.reservationId(),
                    ReservationClosureJpaEntity.Type.CANCELLATION));
        } catch (DataAccessException failure) {
            if (SqlStates.has(failure, SqlStates.UNIQUE_VIOLATION)) {
                throw new ReservationAlreadyClosedException(report.reservationId(), failure);
            }
            throw failure;
        }
        reports.saveAndFlush(new CancellationReportJpaEntity(report.reservationId(), report.eventId(),
                report.origin(), report.status(), report.reason(), report.attributableToPerson(),
                report.noticeMinutes(), report.cancelledAt(), report.resourceId(), report.category(),
                report.holder() == null ? null : report.holder().userId(), report.releasedStart(),
                report.releasedEnd()));
    }

    @Override
    public Optional<CancellationReport> findByReservation(UUID reservationId) {
        return reports.findRow(reservationId).map(CancellationReportPersistenceAdapter::toReport);
    }

    @Override
    public CancellationReportPage search(CancellationReportFilter filter, PageRequest page) {
        // Días de Bogotá, ambos incluidos: desde el inicio de from hasta antes del inicio del día siguiente a to.
        Instant from = filter.from() == null ? null : filter.from().atStartOfDay(TimeSlot.ZONE).toInstant();
        Instant to = filter.to() == null ? null : filter.to().plusDays(1).atStartOfDay(TimeSlot.ZONE).toInstant();

        DeliveryCounts counts = reports.summarize(filter.origin(), filter.delivery(), from, to);
        var summary = new CancellationReportPage.Summary(counts.total(), counts.sent(), counts.pending(),
                counts.failed());
        List<CancellationReport> items = reports.search(filter.origin(), filter.delivery(), from, to,
                        org.springframework.data.domain.PageRequest.of(page.page() - 1, page.size())).stream()
                .map(CancellationReportPersistenceAdapter::toReport)
                .toList();
        return new CancellationReportPage(new Page<>(items, page.page(), page.size(), summary.total()), summary);
    }

    private static CancellationReport toReport(CancellationReportRow row) {
        ReservationHolder holder = row.holderUserId() == null ? null
                : new ReservationHolder(row.holderUserId(), row.holderCode(), row.holderName(), row.holderRole());
        return new CancellationReport(row.reservationId(), row.eventId(), row.origin(), row.reason(),
                row.noticeMinutes(), row.cancelledAt(), row.resourceId(), null, row.resourceCategory(), holder,
                row.releasedStartsAt(), row.releasedEndsAt(),
                new Delivery(row.deliveryStatus(), row.attempts(), row.firstAttemptAt(), row.deliveredAt(),
                        row.deliveryStatus() == DeliveryStatus.PENDING ? row.nextAttemptAt() : null,
                        row.lastError()));
    }
}
