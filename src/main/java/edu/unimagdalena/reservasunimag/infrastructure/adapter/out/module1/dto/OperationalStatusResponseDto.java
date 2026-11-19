package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.module1.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Respuesta de {@code POST /api/v1/resources/operational-status} (UC1 Contratos §2.2). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OperationalStatusResponseDto(List<Entry> statuses, List<String> notFound) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(String resourceId, String operationalStatus, String statusReason) {
    }
}
