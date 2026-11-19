package edu.unimagdalena.reservasunimag.domain.model.academicblock;

import java.time.Instant;
import java.util.UUID;

/**
 * Un bloqueo académico vigente: la reserva de origen académico y su clase. {@code academicTerm} y
 * {@code sessionKey} son {@code null} en una necesidad extraordinaria.
 */
public record AcademicBlock(UUID reservationId, String resourceId, Instant startsAt, Instant endsAt,
                            AcademicData academic, BlockType type, String academicTerm, String sessionKey) {

    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return startsAt.isBefore(otherEnd) && otherStart.isBefore(endsAt);
    }
}
