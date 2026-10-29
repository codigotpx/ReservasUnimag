package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

import java.time.Instant;

/**
 * El reporte de una cancelación con su entrega (UC11 Contratos §2). {@code delivery.lastError} es el único campo de
 * la API que viaja como {@code null}: en una pantalla de auditoría "no hubo error" y "no se sabe" no son lo mismo.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record CancellationReportResponse(
        String reservationId,
        String eventId,
        String cancellationOrigin,
        String status,
        String reason,
        boolean attributableToPerson,
        Integer noticeMinutes,
        String cancelledAt,
        ResourceBody resource,
        HolderBody holder,
        CancellationResponse.TimeBody releasedTime,
        DeliveryBody delivery) {

    static CancellationReportResponse from(CancellationReport report) {
        var delivery = report.delivery();
        return new CancellationReportResponse(
                report.reservationId().toString(),
                report.eventId().toString(),
                report.origin().name(),
                report.status().name(),
                report.reason(),
                report.attributableToPerson(),
                report.noticeMinutes(),
                IsoTimes.offset(report.cancelledAt()),
                ResourceBody.from(report),
                HolderBody.from(report),
                new CancellationResponse.TimeBody(IsoTimes.offset(report.releasedStart()),
                        IsoTimes.offset(report.releasedEnd())),
                new DeliveryBody(delivery.status().name(), delivery.attempts(), offset(delivery.firstAttemptAt()),
                        offset(delivery.deliveredAt()), offset(delivery.nextAttemptAt()), delivery.lastError()));
    }

    private static String offset(Instant instant) {
        return instant == null ? null : IsoTimes.offset(instant);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ResourceBody(String id, String name, String category) {

        static ResourceBody from(CancellationReport report) {
            return new ResourceBody(report.resourceId(), report.resourceName(), report.category().name());
        }
    }

    record HolderBody(String code, String name) {

        static HolderBody from(CancellationReport report) {
            return report.holder() == null ? null : new HolderBody(report.holder().code(), report.holder().name());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DeliveryBody(String status, int attempts, String firstAttemptAt, String deliveredAt, String nextAttemptAt,
                        @JsonInclude(JsonInclude.Include.ALWAYS) String lastError) {
    }
}
