package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.module3.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Reporte de {@code GET /api/v1/compliance/people/{code}} (UC1 Contratos §3.1). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ComplianceReportDto(
        List<SanctionDto> activeSanctions,
        int absenceCount,
        int lateReturns,
        OffsetDateTime generatedAt) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SanctionDto(String reason, LocalDate start, LocalDate end, String scope) {
    }
}
