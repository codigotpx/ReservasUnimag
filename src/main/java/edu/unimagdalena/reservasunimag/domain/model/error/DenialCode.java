package edu.unimagdalena.reservasunimag.domain.model.error;

/**
 * Diccionario de errores: cada negativa de negocio sale con uno, nunca un "no se pudo" (UC2 SC-003). Los
 * {@code RES} son de reservar y los {@code CAN} de cancelar.
 */
public enum DenialCode {
    /** Conflicto académico: el recurso está bloqueado por actividad docente. */
    RES_001("RES-001"),
    /** Cupo de reservas vigentes lleno. */
    RES_002("RES-002"),
    /** Sanción vigente. */
    RES_003("RES-003"),
    /** El recurso ya está tomado, o está en mantenimiento (provisional hasta que exista RES-005). */
    RES_004("RES-004"),
    /** La reserva ya no es cancelable: fuera de plazo o con el activo ya entregado (UC4 FR-003, FR-008). */
    CAN_001("CAN-001"),
    /**
     * Declarado pero sin emitir. El consolidado lo asigna a "no es el titular", y ese caso se responde 404 para no
     * confirmar que la reserva existe (plan de UC4, Decisiones de diseño).
     */
    CAN_002("CAN-002");

    private final String code;

    DenialCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
