package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.loan;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingCheckOut;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReturns;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

import java.util.List;

/** Los préstamos vencidos sin check-out, como los fija UC12 Contratos §3. */
record PendingReturnsResponse(List<Item> loans, PaginationBody pagination, SummaryBody summary) {

    static PendingReturnsResponse from(PendingReturns result) {
        var page = result.page();
        return new PendingReturnsResponse(
                page.items().stream().map(item -> Item.from(item, result)).toList(),
                PaginationBody.of(page),
                // Todos los de la lista están vencidos: por eso total y overdue coinciden.
                new SummaryBody(page.total(), page.total(), result.nearLossThreshold()));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Item(String reservationId, String resourceId, String resourceName, HolderBody holder, String pickup,
                String dueAt, long overdueDays, String lossThresholdAt) {

        static Item from(PendingCheckOut pending, PendingReturns result) {
            var reservation = pending.reservation();
            return new Item(reservation.id().toString(), reservation.resourceId(), pending.resourceName(),
                    HolderBody.of(pending), IsoTimes.offset(reservation.startsAt()),
                    IsoTimes.offset(reservation.endsAt()), result.overdueDays(pending),
                    IsoTimes.offset(result.lossThresholdAt(pending)));
        }
    }

    record SummaryBody(long total, long overdue, long nearLossThreshold) {
    }
}
