package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.module3.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Reporte de {@code GET /api/v1/compliance/people/{code}} (UC1 Contratos §3.1 con los campos de UC6 Contratos §1).
 * Las fechas de la sanción se llaman {@code startsOn} y {@code endsOn}, como en el contrato acordado; se aceptan
 * también {@code start} y {@code end}, que es como las nombraba el borrador de UC1.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ComplianceReportDto(
        PersonDto person,
        List<SanctionDto> activeSanctions,
        int absenceCount,
        int lateReturns,
        OffsetDateTime generatedAt) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PersonDto(String code) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SanctionDto(String reason, @JsonAlias("start") LocalDate startsOn,
                              @JsonAlias("end") LocalDate endsOn, String scope) {
    }
}
