package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.schedule;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleUpload;
import edu.unimagdalena.reservasunimag.domain.model.upload.UploadStatus;
import edu.unimagdalena.reservasunimag.domain.model.upload.UploadSummary;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

import java.util.List;

/**
 * Una carga del histórico, como la fija UC3 Contratos §4: el mismo objeto del listado más el periodo, el resumen y las
 * filas del reporte, para volver a mirarlo sin repetir la carga. Como el reporte, nombra a los titulares de las reservas
 * desplazadas, y solo Dirección de Programa lo ve.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record ScheduleUploadDetailResponse(String uploadId, String academicTerm, ScheduleReportResponse.TermBody term,
                                    String status, String fileName,
                                    ScheduleConfirmationResponse.UserBody uploadedBy, String validatedAt,
                                    String expiresAt, String appliedAt, ScheduleUploadsResponse.ResultBody result,
                                    boolean applicable, UploadSummary summary,
                                    List<ScheduleReportResponse.RowBody> rows) {

    static ScheduleUploadDetailResponse from(ScheduleUpload upload) {
        var item = ScheduleUploadsResponse.Item.from(upload);
        return new ScheduleUploadDetailResponse(item.uploadId(), item.academicTerm(),
                new ScheduleReportResponse.TermBody(upload.termStart().toString(), upload.termEnd().toString()),
                item.status(), item.fileName(), item.uploadedBy(), item.validatedAt(),
                // El vencimiento solo dice algo mientras la carga espera confirmación.
                upload.status() == UploadStatus.VALIDATED && upload.expiresAt() != null
                        ? IsoTimes.offset(upload.expiresAt()) : null,
                item.appliedAt(),
                item.result(), upload.applicable(), upload.summary(),
                upload.rows().stream().map(ScheduleReportResponse.RowBody::from).toList());
    }
}
