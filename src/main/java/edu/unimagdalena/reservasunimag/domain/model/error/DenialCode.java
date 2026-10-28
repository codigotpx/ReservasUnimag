package edu.unimagdalena.reservasunimag.domain.model.error;

/** Códigos de denegación de reserva del diccionario de errores (UC2): RES-001 a RES-005. */
public enum DenialCode {
    RES_001("RES-001"),
    RES_002("RES-002"),
    RES_003("RES-003"),
    RES_004("RES-004"),
    RES_005("RES-005");

    private final String code;

    DenialCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
