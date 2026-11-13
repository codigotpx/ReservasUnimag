package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.BlockType;

import java.time.Instant;
import java.util.UUID;

/**
 * Un bloqueo académico leído con su reserva y el periodo de su carga. {@code academicTerm} es {@code null} en una
 * necesidad extraordinaria. Es una proyección por lo mismo que {@link ReservationRow}.
 */
public record AcademicBlockRow(
        UUID reservationId,
        String resourceId,
        Instant startsAt,
        Instant endsAt,
        String courseCode,
        String course,
        String groupCode,
        String program,
        String instructor,
        BlockType type,
        String academicTerm,
        String sessionKey) {
}
