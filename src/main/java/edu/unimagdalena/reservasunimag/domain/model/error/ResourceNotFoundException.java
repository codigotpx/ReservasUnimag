package edu.unimagdalena.reservasunimag.domain.model.error;

public class ResourceNotFoundException extends RuntimeException {

    private final String resourceId;

    public ResourceNotFoundException(String resourceId) {
        super("El recurso " + resourceId + " no existe en el inventario.");
        this.resourceId = resourceId;
    }

    public String resourceId() {
        return resourceId;
    }
}
