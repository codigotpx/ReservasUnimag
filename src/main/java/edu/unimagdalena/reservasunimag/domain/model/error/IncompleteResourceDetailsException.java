package edu.unimagdalena.reservasunimag.domain.model.error;

/**
 * La ficha del Módulo 1 no trae el plazo de préstamo de un activo. No es culpa de la persona ni una regla de
 * negocio: el contrato del Módulo 1 está incompleto, y el plazo nunca se inventa aquí (UC2 FR-012, P-16).
 */
public class IncompleteResourceDetailsException extends RuntimeException {

    private final String resourceId;

    public IncompleteResourceDetailsException(String resourceId) {
        super("La ficha del activo " + resourceId + " no trae loanTermBusinessDays");
        this.resourceId = resourceId;
    }

    public String resourceId() {
        return resourceId;
    }
}
