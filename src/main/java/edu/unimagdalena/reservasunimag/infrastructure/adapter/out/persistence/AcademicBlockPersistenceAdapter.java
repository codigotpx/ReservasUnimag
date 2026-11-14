package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicBlock;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.port.out.AcademicBlockRepositoryPort;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.AcademicBlockJpaEntity;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.AcademicBlockJpaRepository;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.AcademicBlockRow;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * La clase de cada bloqueo académico (UC3), con JPA. Participa en la transacción del caso de uso; no abre ninguna.
 * La inserción se envía a la base en el acto, porque otros adaptadores leen {@code academic_block} con SQL en la
 * misma transacción.
 */
@Component
class AcademicBlockPersistenceAdapter implements AcademicBlockRepositoryPort {

    private final AcademicBlockJpaRepository blocks;

    AcademicBlockPersistenceAdapter(AcademicBlockJpaRepository blocks) {
        this.blocks = blocks;
    }

    @Override
    public void insert(UUID reservationId, UUID scheduleId, AcademicBlock block) {
        AcademicData academic = block.academic();
        blocks.saveAndFlush(new AcademicBlockJpaEntity(reservationId, scheduleId, block.type(), academic.courseCode(),
                academic.course(), academic.group(), academic.program(), academic.instructor(), block.sessionKey()));
    }

    @Override
    public List<AcademicBlock> findConfirmed(Collection<String> resourceIds, Instant from, Instant to) {
        if (resourceIds.isEmpty()) {
            return List.of();
        }
        return blocks.findConfirmed(resourceIds, from, to).stream()
                .map(AcademicBlockPersistenceAdapter::toBlock)
                .toList();
    }

    @Override
    public List<AcademicBlock> findUpcomingRegular(String academicTerm, Instant now) {
        return blocks.findUpcomingRegular(academicTerm, now).stream()
                .map(AcademicBlockPersistenceAdapter::toBlock)
                .toList();
    }

    private static AcademicBlock toBlock(AcademicBlockRow row) {
        return new AcademicBlock(row.reservationId(), row.resourceId(), row.startsAt(), row.endsAt(),
                new AcademicData(row.courseCode(), row.course(), row.groupCode(), row.program(), row.instructor()),
                row.type(), row.academicTerm(), row.sessionKey());
    }
}
