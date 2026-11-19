package edu.unimagdalena.reservasunimag.domain.model.reservation;

import java.time.Instant;
import java.util.Objects;

/**
 * Lo que ocupa un recurso en el tiempo. {@code endsAt} es {@code null} cuando no tiene fin: un préstamo entregado
 * y sin devolver sigue ocupando el recurso hasta que vuelva. {@code dueAt} es hasta cuándo está comprometido según
 * lo apartado —el fin de la franja o el vencimiento del préstamo— y es lo que se le dice a quien pregunta (UC8
 * FR-012); en un préstamo entregado puede haber pasado ya.
 */
public record Occupancy(String resourceId, OccupancyType type, Instant startsAt, Instant endsAt, Instant dueAt) {

    public Occupancy {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(startsAt, "startsAt");
    }

    /** Una ocupación que termina cuando vence. */
    public Occupancy(String resourceId, OccupancyType type, Instant startsAt, Instant endsAt) {
        this(resourceId, type, startsAt, endsAt, endsAt);
    }

    /** Cruce de intervalos semiabiertos {@code [from, to)}. */
    public boolean overlaps(Instant from, Instant to) {
        return startsAt.isBefore(to) && (endsAt == null || from.isBefore(endsAt));
    }

    /** Un préstamo entregado cuyo vencimiento ya pasó, o que no tiene vencimiento conocido. */
    public boolean overdueAt(Instant now) {
        return type == OccupancyType.LOAN_PICKED_UP && (dueAt == null || !now.isBefore(dueAt));
    }
}
