package edu.unimagdalena.reservasunimag.domain.model.resource;

import java.time.Instant;

/** Un recurso con lo que se sabe de él en la franja consultada. */
public record ResourceWithStatus(Resource resource, Availability availability) {

    public ResourceWithStatus(Resource resource, DisplayedStatus status) {
        this(resource, Availability.of(status));
    }

    public DisplayedStatus status() {
        return availability.status();
    }

    public boolean selectable() {
        return availability.selectable();
    }

    /** {@code null} cuando es seleccionable. */
    public String reason() {
        return availability.reason();
    }

    /** Hasta cuándo está comprometido (UC8 FR-012); {@code null} si no hay una fecha que prometer. */
    public Instant occupiedUntil() {
        return availability.occupiedUntil();
    }
}
