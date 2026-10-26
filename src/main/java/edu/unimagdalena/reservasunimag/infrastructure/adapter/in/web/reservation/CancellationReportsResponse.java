package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReportPage;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

import java.util.List;

/** El listado de auditoría de UC11 Contratos §3, con el resumen sobre el filtro completo. */
record CancellationReportsResponse(List<Item> reports, PaginationBody pagination, SummaryBody summary) {

    static CancellationReportsResponse from(CancellationReportPage result) {
        var page = result.page();
        var summary = result.summary();
        return new CancellationReportsResponse(
                page.items().stream().map(Item::from).toList(),
                new PaginationBody(page.page(), page.pageSize(), page.totalPages(), page.total()),
                new SummaryBody(summary.total(), summary.sent(), summary.pending(), summary.failed()));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Item(String reservationId, String eventId, String cancellationOrigin, String status,
                boolean attributableToPerson, String cancelledAt, CancellationReportResponse.ResourceBody resource,
                CancellationReportResponse.HolderBody holder, DeliveryBody delivery) {

        static Item from(CancellationReport report) {
            return new Item(report.reservationId().toString(), report.eventId().toString(), report.origin().name(),
                    report.status().name(), report.attributableToPerson(), IsoTimes.offset(report.cancelledAt()),
                    CancellationReportResponse.ResourceBody.from(report),
                    CancellationReportResponse.HolderBody.from(report),
                    new DeliveryBody(report.delivery().status().name(), report.delivery().attempts(),
                            report.delivery().lastError()));
        }
    }

    record DeliveryBody(String status, int attempts, String lastError) {
    }

    record PaginationBody(int page, int pageSize, int totalPages, long total) {
    }

    record SummaryBody(long total, long sent, long pending, long failed) {
    }
}
