package edu.unimagdalena.reservasunimag.domain.model.academicblock;

import java.time.Instant;
import java.util.Objects;

/**
 * Una ocurrencia concreta de una clase, con fecha. {@code sessionKey} es su huella (FR-007); es {@code null} en una
 * necesidad extraordinaria, que no se reconcilia con ninguna recarga.
 */
public record ClassSession(String resourceId, Instant startsAt, Instant endsAt, AcademicData academic, BlockType type,
                           String sessionKey) {

    public ClassSession {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        Objects.requireNonNull(academic, "academic");
        Objects.requireNonNull(type, "type");
    }

    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return startsAt.isBefore(otherEnd) && otherStart.isBefore(endsAt);
    }
}
