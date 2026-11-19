package edu.unimagdalena.reservasunimag.domain.model.compliance;

/** El dictamen de la revisión de un espacio (UC12 FR-011). Un activo nunca lleva dictamen: eso va al Módulo 1. */
public enum Verdict {
    SIN_NOVEDAD,
    /** Dispara la cancelación de las reservas que el espacio tenía por delante (spec 13). */
    REQUIERE_MANTENIMIENTO
}
