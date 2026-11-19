package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.module1.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Ficha del Módulo 1. Las enumeraciones llegan como cadena para que un valor desconocido sea un error de contrato
 * que el adaptador detecta, y no una excepción de deserialización.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResourceDto(
        String id,
        String name,
        String category,
        String type,
        String operationalStatus,
        String statusReason,
        String location,
        String faculty,
        Integer maxCapacity,
        List<String> equipment,
        String assetTag,
        String condition,
        Integer loanTermBusinessDays) {
}
