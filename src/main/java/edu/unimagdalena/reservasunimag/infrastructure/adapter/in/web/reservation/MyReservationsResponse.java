package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.reservation.MyReservations;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

import java.util.List;

/**
 * Mis reservas, como las fija UC2 Contratos §3, con el {@code cancellable} que añadió UC4 Contratos §4. {@code name}
 * se omite si el Módulo 1 no respondió.
 */
record MyReservationsResponse(List<Item> reservations, ReservationResponse.QuotaBody quota,
                              boolean catalogUnavailable) {

    static MyReservationsResponse from(MyReservations mine) {
        return new MyReservationsResponse(
                mine.reservations().stream().map(Item::from).toList(),
                ReservationResponse.QuotaBody.from(mine.quota()),
                mine.catalogUnavailable());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Item(String id, String resourceId, String name, String category, String status, String start,
                String end, ReservationResponse.LoanBody loan, boolean cancellable) {

        static Item from(MyReservations.Item item) {
            var reservation = item.reservation();
            return new Item(reservation.id().toString(), reservation.resourceId(), item.name(),
                    reservation.category().name(), reservation.status().name(),
                    IsoTimes.offset(reservation.startsAt()), IsoTimes.offset(reservation.endsAt()),
                    ReservationResponse.LoanBody.of(reservation, item.overdue()), item.cancellable());
        }
    }
}
