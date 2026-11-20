package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.module1.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * Un recurso del Módulo 1, tal como lo sirven {@code GET /api/v1/recursos} y {@code GET /api/v1/recursos/{id}}.
 * {@code equipamiento} solo viene en la ficha; se lee como JSON porque el Módulo 1 no publicó su forma.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResourceDto(
        String id,
        String codigo,
        String nombre,
        String tipo,
        String estado,
        String estadoFisico,
        String facultad,
        String ubicacion,
        Integer aforoMaximo,
        Integer plazoDevolucion,
        String tipoActivo,
        List<JsonNode> equipamiento) {
}
