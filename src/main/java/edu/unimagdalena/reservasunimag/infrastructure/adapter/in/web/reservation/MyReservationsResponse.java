package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.reservation.MyReservations;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

import java.util.List;

/**
 * Mis reservas, como las fija UC2 Contratos §3, con el {@code cancellable} que añadió UC4 Contratos §4 y la
 * {@code absence} de UC9 Contratos §4. {@code name} se omite si el Módulo 1 no respondió.
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
                String end, ReservationResponse.LoanBody loan, boolean cancellable, String useStartedAt,
                AbsenceBody absence) {

        static Item from(MyReservations.Item item) {
            var reservation = item.reservation();
            return new Item(reservation.id().toString(), reservation.resourceId(), item.name(),
                    reservation.category().name(), reservation.status().name(),
                    IsoTimes.offset(reservation.startsAt()), IsoTimes.offset(reservation.endsAt()),
                    ReservationResponse.LoanBody.of(reservation, item.overdue()), item.cancellable(),
                    reservation.useStartedAt() == null ? null : IsoTimes.offset(reservation.useStartedAt()),
                    AbsenceBody.of(item.absence()));
        }
    }

    /**
     * La marca de FR-010 de UC9. El mensaje no promete ni niega una sanción: esa decisión es del Módulo 3 (FR-003).
     * Una ausencia anulada no llega aquí.
     */
    record AbsenceBody(String registeredAt, String message) {

        static final String MESSAGE = "Se registró una ausencia: no se presentó a esta reserva. La consecuencia, "
                + "si la hay, la decide la oficina de cumplimiento.";

        static AbsenceBody of(edu.unimagdalena.reservasunimag.domain.model.compliance.Absence absence) {
            return absence == null ? null : new AbsenceBody(IsoTimes.offset(absence.reportedAt()), MESSAGE);
        }
    }
}
