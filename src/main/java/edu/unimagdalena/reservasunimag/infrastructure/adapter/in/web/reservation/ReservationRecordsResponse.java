package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordPage;

import java.util.List;

/**
 * El listado de auditoría de UC10 Contratos §3. {@code summary.sanctionable} cuenta las sancionables del filtro: con
 * {@code origin=ACADEMICO} tiene que dar cero, y esa es la comprobación de SC-006.
 */
record ReservationRecordsResponse(List<ReservationRecordResponse.RecordBody> records,
                                  CancellationReportsResponse.PaginationBody pagination, SummaryBody summary) {

    static ReservationRecordsResponse from(ReservationRecordPage result) {
        var page = result.page();
        var summary = result.summary();
        return new ReservationRecordsResponse(
                page.items().stream().map(report -> ReservationRecordResponse.RecordBody.from(report, true)).toList(),
                new CancellationReportsResponse.PaginationBody(page.page(), page.pageSize(), page.totalPages(),
                        page.total()),
                new SummaryBody(summary.total(), summary.sent(), summary.pending(), summary.failed(),
                        summary.sanctionable()));
    }

    record SummaryBody(long total, long sent, long pending, long failed, long sanctionable) {
    }
}
