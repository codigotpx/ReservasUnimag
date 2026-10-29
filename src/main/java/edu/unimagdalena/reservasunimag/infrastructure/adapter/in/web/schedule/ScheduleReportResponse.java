package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.schedule;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.upload.AffectedReservation;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleUpload;
import edu.unimagdalena.reservasunimag.domain.model.upload.UploadSummary;
import edu.unimagdalena.reservasunimag.domain.model.upload.ValidatedRow;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

import java.util.List;

/**
 * El reporte de una carga, como lo fija UC3 Contratos §1. Es la única respuesta del módulo que nombra al titular de una
 * reserva: FR-008 pide mostrar cuáles se cancelarían, y solo Dirección de Programa la ve.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record ScheduleReportResponse(String uploadId, String academicTerm, TermBody term, String status, String validatedAt,
                              String expiresAt, boolean applicable, boolean requiresConfirmation,
                              UploadSummary summary, List<RowBody> rows) {

    static ScheduleReportResponse from(ScheduleUpload upload) {
        return new ScheduleReportResponse(upload.id().toString(), upload.academicTerm(),
                new TermBody(upload.termStart().toString(), upload.termEnd().toString()), upload.status().name(),
                IsoTimes.offset(upload.validatedAt()),
                upload.expiresAt() == null ? null : IsoTimes.offset(upload.expiresAt()), upload.applicable(),
                upload.requiresConfirmation(), upload.summary(), upload.rows().stream().map(RowBody::from).toList());
    }

    record TermBody(String start, String end) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record RowBody(int row, String resourceId, String resourceName, String course, String courseCode, String group,
                   String dayOfWeek, String start, String end, String verdict, int sessions, String problem,
                   String column, ConflictBody conflictingBlock, List<AffectedBody> affectedReservations) {

        static RowBody from(ValidatedRow row) {
            return new RowBody(row.row(), row.resourceId(), row.resourceName(), row.course(), row.courseCode(),
                    row.group(), row.dayOfWeek(), row.start(), row.end(), row.verdict().name(), row.sessions(),
                    row.problem(), row.column(), ConflictBody.of(row.conflictingBlock()),
                    row.affected().stream().map(AffectedBody::from).toList());
        }
    }

    record ConflictBody(String course, String courseCode, String group) {

        static ConflictBody of(AcademicData academic) {
            return academic == null ? null
                    : new ConflictBody(academic.course(), academic.courseCode(), academic.group());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AffectedBody(String reservationId, HolderBody holder, String start, String end, String category) {

        static AffectedBody from(AffectedReservation affected) {
            return new AffectedBody(affected.reservationId().toString(),
                    affected.holder() == null ? null
                            : new HolderBody(affected.holder().code(), affected.holder().name()),
                    IsoTimes.offset(affected.start()), IsoTimes.offset(affected.end()), affected.category().name());
        }
    }

    record HolderBody(String code, String name) {
    }
}
