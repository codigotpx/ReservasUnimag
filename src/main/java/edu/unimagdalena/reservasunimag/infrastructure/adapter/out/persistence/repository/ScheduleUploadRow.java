package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.domain.model.upload.UploadStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Una carga del horario leída con el nombre de quien la subió. {@code summary}, {@code impact} y {@code result} son
 * el JSON tal como se guardó; {@code result} es {@code null} mientras no se aplique.
 */
public record ScheduleUploadRow(
        UUID id,
        String academicTerm,
        LocalDate termStartsOn,
        LocalDate termEndsOn,
        String fileName,
        UUID uploadedBy,
        String uploadedByName,
        UploadStatus status,
        Instant uploadedAt,
        Instant expiresAt,
        Instant appliedAt,
        String summary,
        String impact,
        String result) {
}
