package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.BlockType;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;

import java.time.Instant;

/**
 * Una entrada del calendario de ocupación. {@code blockType}, {@code course} y {@code program} son {@code null} en
 * una reserva estudiantil; el titular no se lee, porque el calendario no lo muestra (UC8 FR-005).
 */
public record ScheduledUseRow(
        ReservationOrigin origin,
        Instant startsAt,
        Instant endsAt,
        BlockType blockType,
        String course,
        String program) {
}
