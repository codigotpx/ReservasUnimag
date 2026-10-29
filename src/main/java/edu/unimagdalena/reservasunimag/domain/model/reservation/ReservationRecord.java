package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * La ficha de una reserva recién creada, que se le reporta al Módulo 3 (UC10). {@code eventId} identifica el hecho y
 * no el intento: es el mismo en todos los reenvíos, y con él el Módulo 3 descarta las copias (FR-005). {@code holder}
 * y {@code academic} son excluyentes: una reserva estudiantil tiene titular y un bloqueo académico tiene su clase
 * (FR-008).
 */
public record ReservationRecord(UUID eventId, Reservation reservation, Resource resource, ReservationHolder holder,
                                AcademicData academic, Instant occurredAt) {

    public ReservationRecord {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(occurredAt, "occurredAt");
        boolean academicOrigin = reservation.origin() == ReservationOrigin.ACADEMICO;
        if (academicOrigin == (holder != null) || academicOrigin != (academic != null)) {
            throw new IllegalArgumentException("una ficha estudiantil lleva titular y una académica lleva su clase");
        }
    }

    public static ReservationRecord student(UUID eventId, Reservation reservation, Resource resource,
                                            ReservationHolder holder, Instant occurredAt) {
        return new ReservationRecord(eventId, reservation, resource, holder, null, occurredAt);
    }

    public static ReservationRecord academic(UUID eventId, Reservation reservation, Resource resource,
                                             AcademicData academic, Instant occurredAt) {
        return new ReservationRecord(eventId, reservation, resource, null, academic, occurredAt);
    }

    /** Solo lo que apartó una persona se le puede anotar a alguien; una clase nunca (FR-008, SC-006). */
    public boolean sanctionable() {
        return reservation.origin() == ReservationOrigin.ESTUDIANTIL;
    }
}
