package edu.unimagdalena.reservasunimag.domain.model.error;

/**
 * La base rechazó la ocupación porque se cruza con otra confirmada: la última garantía contra dos reservas
 * simultáneas (UC2 FR-004). El caso de uso la traduce a RES-004.
 */
public class ResourceTakenException extends RuntimeException {

    public ResourceTakenException(String resourceId, Throwable cause) {
        super("El recurso " + resourceId + " ya tiene una ocupación confirmada que se cruza", cause);
    }
}
