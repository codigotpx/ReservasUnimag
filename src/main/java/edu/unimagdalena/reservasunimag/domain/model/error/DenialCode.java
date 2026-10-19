package edu.unimagdalena.reservasunimag.domain.model.error;

/** Diccionario de errores de UC2: cada negativa de negocio sale con uno, nunca un "no se pudo" (SC-003). */
public enum DenialCode {
    /** Conflicto académico: el recurso está bloqueado por actividad docente. */
    RES_001("RES-001"),
    /** Cupo de reservas vigentes lleno. */
    RES_002("RES-002"),
    /** Sanción vigente. */
    RES_003("RES-003"),
    /** El recurso ya está tomado, o está en mantenimiento (provisional hasta que exista RES-005). */
    RES_004("RES-004");

    private final String code;

    DenialCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
