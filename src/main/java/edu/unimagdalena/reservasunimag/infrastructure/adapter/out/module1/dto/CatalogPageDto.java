package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.module1.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Una página de {@code GET /api/v1/resources} (UC1 Contratos §2.1). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogPageDto(List<ResourceDto> content, int page, int size, int totalPages, long total) {
}
