package edu.unimagdalena.reservasunimag.domain.model.error;

/** Se pidió el vencimiento de un préstamo para un recurso que es un espacio. */
public class NotAnAssetException extends RuntimeException {

    private final String resourceId;

    public NotAnAssetException(String resourceId) {
        super("El recurso " + resourceId + " es un espacio y no se presta");
        this.resourceId = resourceId;
    }

    public String resourceId() {
        return resourceId;
    }
}
