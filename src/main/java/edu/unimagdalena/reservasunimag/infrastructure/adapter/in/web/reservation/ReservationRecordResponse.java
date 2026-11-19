package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordReport;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

import java.time.Instant;

/**
 * La ficha de una reserva con su entrega (UC10 Contratos §2): un objeto y no una lista, porque una reserva produce
 * una sola. Una ficha estudiantil lleva {@code holder} y una académica {@code academic}, sin el docente: en la
 * auditoría no hace falta y es una persona que no es parte de la reserva. {@code delivery.lastError} viaja como
 * {@code null}, igual que en UC11.
 */
record ReservationRecordResponse(String reservationId, RecordBody record) {

    static ReservationRecordResponse from(ReservationRecordReport report) {
        return new ReservationRecordResponse(report.reservationId().toString(), RecordBody.from(report, false));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RecordBody(String reservationId, String eventId, String origin, boolean sanctionable, String sentAt,
                      CancellationReportResponse.ResourceBody resource, CancellationReportResponse.HolderBody holder,
                      AcademicBody academic, CancellationResponse.TimeBody occupancy, LoanBody loan,
                      CancellationReportResponse.DeliveryBody delivery) {

        /** {@code withReservationId} es para el listado, donde cada fila dice de qué reserva es. */
        static RecordBody from(ReservationRecordReport report, boolean withReservationId) {
            var delivery = report.delivery();
            var academic = report.academic();
            String start = IsoTimes.offset(report.startsAt());
            String end = IsoTimes.offset(report.endsAt());
            return new RecordBody(
                    withReservationId ? report.reservationId().toString() : null,
                    report.eventId().toString(),
                    report.origin().name(),
                    report.sanctionable(),
                    IsoTimes.offset(report.sentAt()),
                    new CancellationReportResponse.ResourceBody(report.resourceId(), report.resourceName(),
                            report.category().name()),
                    report.holder() == null ? null
                            : new CancellationReportResponse.HolderBody(report.holder().code(), report.holder().name()),
                    academic == null ? null
                            : new AcademicBody(academic.course(), academic.courseCode(), academic.program()),
                    new CancellationResponse.TimeBody(start, end),
                    report.loanTermBusinessDays() == null ? null
                            : new LoanBody(start, end, report.loanTermBusinessDays()),
                    new CancellationReportResponse.DeliveryBody(delivery.status().name(), delivery.attempts(),
                            offset(delivery.firstAttemptAt()), offset(delivery.deliveredAt()),
                            offset(delivery.nextAttemptAt()), delivery.lastError()));
        }

        private static String offset(Instant instant) {
            return instant == null ? null : IsoTimes.offset(instant);
        }
    }

    record AcademicBody(String course, String courseCode, String program) {
    }

    record LoanBody(String pickup, String dueAt, int termBusinessDays) {
    }
}
