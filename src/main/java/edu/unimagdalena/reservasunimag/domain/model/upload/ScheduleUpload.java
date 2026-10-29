package edu.unimagdalena.reservasunimag.domain.model.upload;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Una carga del horario y su reporte, guardada entre validar y confirmar (FR-009). {@code expiresAt} existe solo si
 * quedó {@code VALIDATED}; {@code appliedAt} y {@code result}, solo si se aplicó. {@code rows} puede venir vacía en un
 * listado, donde no hace falta.
 */
public record ScheduleUpload(
        UUID id,
        String academicTerm,
        LocalDate termStart,
        LocalDate termEnd,
        String fileName,
        UUID uploadedBy,
        String uploadedByName,
        UploadStatus status,
        Instant validatedAt,
        Instant expiresAt,
        Instant appliedAt,
        UploadSummary summary,
        UploadImpact impact,
        ApplyResult result,
        List<ValidatedRow> rows) {

    public ScheduleUpload {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        rows = List.copyOf(rows);
    }

    public boolean applicable() {
        return summary.rowsWithBlockingVerdict() == 0;
    }

    /** Si al confirmar se cancelaría algo: es lo que la pantalla tiene que advertir antes (FR-008). */
    public boolean requiresConfirmation() {
        return applicable() && (summary.reservationsToCancel() > 0 || summary.blocksToWithdraw() > 0);
    }

    public ScheduleUpload applied(Instant at, ApplyResult applyResult) {
        return new ScheduleUpload(id, academicTerm, termStart, termEnd, fileName, uploadedBy, uploadedByName,
                UploadStatus.APPLIED, validatedAt, expiresAt, at, summary, impact, applyResult, rows);
    }
}
