package edu.unimagdalena.reservasunimag.domain.model.resource;

/** Un recurso con el estado que se muestra en la franja consultada. */
public record ResourceWithStatus(Resource resource, DisplayedStatus status) {

    public boolean selectable() {
        return status.selectable();
    }

    /** {@code null} cuando es seleccionable. */
    public String reason() {
        return status.reason();
    }
}
