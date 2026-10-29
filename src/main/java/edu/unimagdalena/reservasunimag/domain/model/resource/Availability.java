package edu.unimagdalena.reservasunimag.domain.model.resource;

import java.time.Instant;

/**
 * El estado de un recurso en un intervalo y, si lo ocupa algo nuestro, hasta cuándo (UC8 FR-012).
 * {@code occupiedUntil} es {@code null} cuando está libre, cuando el estado lo fija solo el Módulo 1 o cuando la
 * ocupación no tiene fin (un préstamo vencido y sin devolver).
 */
public record Availability(DisplayedStatus status, Instant occupiedUntil) {

    public boolean selectable() {
        return status.selectable();
    }
}
