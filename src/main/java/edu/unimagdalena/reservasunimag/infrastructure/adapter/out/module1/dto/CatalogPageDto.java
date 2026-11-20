package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.module1.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Página de {@code GET /api/v1/recursos} del Módulo 1. {@code page} empieza en 0 y no trae {@code totalPages}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogPageDto(List<ResourceDto> content, int page, int size, long totalElements) {
}
