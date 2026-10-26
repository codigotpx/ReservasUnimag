package edu.unimagdalena.reservasunimag.domain.model.compliance;

/**
 * Por qué un reporte de no asistencia bien formado no deja ausencia. Es una respuesta de negocio al Módulo 3, no un
 * evento roto: va en el acuse y nunca al DLT (plan de UC9, Decisiones de diseño).
 */
public enum NoShowRejection {
    /** Llegó antes de los 10 minutos: la persona todavía está a tiempo (FR-011). */
    TOO_EARLY,
    /** La reserva ya estaba cancelada, por quien sea (FR-005, FR-006, SC-003). */
    RESERVATION_CANCELLED,
    /** El activo ya se recogió: la persona sí se presentó. */
    ALREADY_PICKED_UP,
    /** El activo ya se devolvió: no se puede estar ausente de algo que se usó. */
    ALREADY_RETURNED,
    /** La reserva ya está cerrada por otra vía, por ejemplo una ausencia que luego se anuló. */
    RESERVATION_FINISHED,
    /** Un bloqueo académico no tiene titular al que anotarle la ausencia. */
    ACADEMIC_BLOCK
}
