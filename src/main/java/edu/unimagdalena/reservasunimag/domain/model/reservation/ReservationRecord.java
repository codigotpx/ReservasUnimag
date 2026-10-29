package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;

import java.time.Instant;

/**
 * La ficha de una reserva recién creada, que se le reporta al Módulo 3 (UC10). {@code holder} y {@code academic} son
 * excluyentes: una reserva estudiantil tiene titular y un bloqueo académico tiene su clase (UC10 FR-009).
 */
public record ReservationRecord(Reservation reservation, Resource resource, ReservationHolder holder,
                                AcademicData academic, Instant occurredAt) {

    public ReservationRecord(Reservation reservation, Resource resource, ReservationHolder holder, Instant occurredAt) {
        this(reservation, resource, holder, null, occurredAt);
    }
}
