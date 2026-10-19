package edu.unimagdalena.reservasunimag.domain.model.reservation;

import java.time.Instant;
import java.util.Objects;

/**
 * Lo que ocupa un recurso en el tiempo. {@code endsAt} es {@code null} cuando no tiene fin: un préstamo entregado
 * y sin devolver sigue ocupando el recurso hasta que vuelva.
 */
public record Occupancy(String resourceId, OccupancyType type, Instant startsAt, Instant endsAt) {

    public Occupancy {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(startsAt, "startsAt");
    }

    /** Cruce de intervalos semiabiertos {@code [from, to)}. */
    public boolean overlaps(Instant from, Instant to) {
        return startsAt.isBefore(to) && (endsAt == null || from.isBefore(endsAt));
    }
}
