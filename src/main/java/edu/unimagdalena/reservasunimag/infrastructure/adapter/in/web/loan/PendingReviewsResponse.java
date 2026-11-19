package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.loan;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingCheckOut;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReviews;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

import java.util.List;

/** Las reservas de espacio terminadas sin revisión, como las fija UC12 Contratos §4. */
record PendingReviewsResponse(List<Item> reservations, PaginationBody pagination, SummaryBody summary) {

    static PendingReviewsResponse from(PendingReviews result) {
        var page = result.page();
        return new PendingReviewsResponse(
                page.items().stream().map(item -> Item.from(item, result)).toList(),
                PaginationBody.of(page),
                new SummaryBody(page.total(), result.student(), result.academic()));
    }

    /** {@code holder} falta en un bloqueo académico. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Item(String reservationId, String resourceId, String resourceName, String origin, HolderBody holder,
                String start, String end, long finishedDaysAgo) {

        static Item from(PendingCheckOut pending, PendingReviews result) {
            var reservation = pending.reservation();
            return new Item(reservation.id().toString(), reservation.resourceId(), pending.resourceName(),
                    reservation.origin().name(), HolderBody.of(pending), IsoTimes.offset(reservation.startsAt()),
                    IsoTimes.offset(reservation.endsAt()), result.finishedDaysAgo(pending));
        }
    }

    record SummaryBody(long total, long student, long academic) {
    }
}
