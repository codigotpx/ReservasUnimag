package edu.unimagdalena.reservasunimag.domain.model.resource;

import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;

import java.time.Instant;
import java.util.Objects;

/**
 * La {@code RespuestaDeDisponibilidad} del spec de UC8: un recurso, la franja preguntada, si está libre, por qué no
 * y hasta cuándo. Vale para {@code answeredAt} y nada más (FR-006).
 */
public record AvailabilityAnswer(Resource resource, TimeSlot slot, Availability availability, Instant answeredAt) {

    public AvailabilityAnswer {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(availability, "availability");
        Objects.requireNonNull(answeredAt, "answeredAt");
    }

    public boolean available() {
        return availability.selectable();
    }

    public DisplayedStatus status() {
        return availability.status();
    }

    public String reason() {
        return availability.reason();
    }

    public Instant occupiedUntil() {
        return availability.occupiedUntil();
    }
}
