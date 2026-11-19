package edu.unimagdalena.reservasunimag.domain.model.error;

/** Combinación de filtros que no tiene sentido, p. ej. aforo mínimo sobre activos (UC1 Contratos §1). */
public class InvalidFilterException extends RuntimeException {

    private final String field;

    public InvalidFilterException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
