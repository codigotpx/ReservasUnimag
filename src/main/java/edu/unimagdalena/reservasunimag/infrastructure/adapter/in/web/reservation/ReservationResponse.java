package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationResult;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Quota;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

/** La reserva recién confirmada, como la fija UC2 Contratos §1. Lo que no aplica se omite. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record ReservationResponse(
        String id,
        String resourceId,
        String name,
        String category,
        String status,
        String origin,
        String start,
        String end,
        LoanBody loan,
        String createdAt,
        QuotaBody quota) {

    static ReservationResponse from(ReservationResult result) {
        Reservation reservation = result.reservation();
        return new ReservationResponse(
                reservation.id().toString(),
                reservation.resourceId(),
                result.resource().name(),
                reservation.category().name(),
                reservation.status().name(),
                reservation.origin().name(),
                IsoTimes.offset(reservation.startsAt()),
                IsoTimes.offset(reservation.endsAt()),
                LoanBody.of(reservation, null),
                IsoTimes.offset(reservation.createdAt()),
                QuotaBody.from(result.quota()));
    }

    /** Un préstamo con nombres de negocio. {@code overdue} solo viaja en la lista de mis reservas. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record LoanBody(String pickup, String dueAt, int termBusinessDays, boolean pickedUp, Boolean overdue) {

        static LoanBody of(Reservation reservation, Boolean overdue) {
            if (reservation.loan() == null) {
                return null;
            }
            return new LoanBody(IsoTimes.offset(reservation.startsAt()), IsoTimes.offset(reservation.endsAt()),
                    reservation.loan().termBusinessDays(), reservation.loan().pickedUp(), overdue);
        }
    }

    record QuotaBody(int used, int max) {

        static QuotaBody from(Quota quota) {
            return new QuotaBody(quota.used(), quota.max());
        }
    }
}
