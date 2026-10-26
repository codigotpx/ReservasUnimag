package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

/**
 * {@code ReservationCancelled} tal como viaja al Módulo 3 (UC3 Contratos §7 y UC4 Contratos §3). Nunca lleva un
 * {@code null}: {@code noticeMinutes} va solo cuando cancela el titular, {@code holder} falta en un bloqueo
 * académico y {@code resource.name} cuando el Módulo 1 no respondió al cancelar.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReservationCancelledEvent(String eventId, String type, int version, String occurredAt, Data data) {

    public static final String TYPE = "ReservationCancelled";

    public static ReservationCancelledEvent from(CancellationReport report) {
        var holder = report.holder();
        String cancelledAt = IsoTimes.offset(report.cancelledAt());
        return new ReservationCancelledEvent(report.eventId().toString(), TYPE, 1, cancelledAt,
                new Data(
                        report.reservationId().toString(),
                        report.status().name(),
                        report.origin().name(),
                        report.reason(),
                        report.attributableToPerson(),
                        report.noticeMinutes(),
                        cancelledAt,
                        new ResourceBody(report.resourceId(), report.resourceName(), report.category().name()),
                        holder == null ? null : new HolderBody(holder.userId().toString(), holder.code(),
                                holder.name(), holder.role().name()),
                        new TimeBody(IsoTimes.offset(report.releasedStart()), IsoTimes.offset(report.releasedEnd()))));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Data(String reservationId, String status, String cancellationOrigin, String reason,
                       boolean attributableToPerson, Integer noticeMinutes, String cancelledAt,
                       ResourceBody resource, HolderBody holder, TimeBody releasedTime) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResourceBody(String id, String name, String category) {
    }

    public record HolderBody(String userId, String code, String name, String role) {
    }

    public record TimeBody(String start, String end) {
    }
}
