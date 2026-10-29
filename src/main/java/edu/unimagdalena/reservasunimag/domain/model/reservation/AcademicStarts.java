package edu.unimagdalena.reservasunimag.domain.model.reservation;

/**
 * Lo que hizo una pasada sobre las clases que llegaron a su hora: cuántas empezaron, cuántas se dejaron fuera porque el
 * Módulo 1 tiene el salón {@code EN_MANTENIMIENTO} (FR-005) y cuántas fallaron, sin frenar a las demás.
 */
public record AcademicStarts(int started, int skippedForMaintenance, int failed) {
}
