package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicBlock;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Los bloqueos académicos: la clase de cada reserva de origen académico. */
public interface AcademicBlockRepositoryPort {

    /** Guarda la clase de una reserva académica ya insertada, en la misma transacción. */
    void insert(UUID reservationId, UUID scheduleId, AcademicBlock block);

    /** Los bloqueos confirmados de esos recursos que se cruzan con {@code [from, to)}. */
    List<AcademicBlock> findConfirmed(Collection<String> resourceIds, Instant from, Instant to);

    /** Los bloqueos regulares confirmados del periodo que todavía no empezaron: lo que una recarga reconcilia. */
    List<AcademicBlock> findUpcomingRegular(String academicTerm, Instant now);
}
