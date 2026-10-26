package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationResult;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

/**
 * La reserva cancelada, como la fija UC4 Contratos §1. En una segunda petición sobre la misma reserva se repite el
 * {@code cancelledAt} original con {@code alreadyCancelled: true}, sin antelación ni tiempo liberado: esta vez no se
 * liberó nada.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record CancellationResponse(
        String reservationId,
        String status,
        String cancellationOrigin,
        String cancelledAt,
        Integer noticeMinutes,
        TimeBody releasedTime,
        Boolean alreadyCancelled,
        ReservationResponse.QuotaBody quota) {

    static CancellationResponse from(CancellationResult result) {
        var reservation = result.reservation();
        var cancellation = result.cancellation();
        boolean again = result.alreadyCancelled();
        return new CancellationResponse(
                reservation.id().toString(),
                reservation.status().name(),
                cancellation.origin().name(),
                IsoTimes.offset(cancellation.cancelledAt()),
                again ? null : cancellation.noticeMinutes(),
                again ? null : new TimeBody(IsoTimes.offset(reservation.startsAt()),
                        IsoTimes.offset(reservation.endsAt())),
                again ? Boolean.TRUE : null,
                result.quota() == null ? null : ReservationResponse.QuotaBody.from(result.quota()));
    }

    /** La ocupación liberada completa: la franja de un espacio o el periodo entero del préstamo (UC4 FR-002). */
    record TimeBody(String start, String end) {
    }
}
