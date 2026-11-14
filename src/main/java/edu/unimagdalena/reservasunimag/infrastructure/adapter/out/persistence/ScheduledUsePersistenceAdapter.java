package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.BlockType;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.resource.ScheduledUse;
import edu.unimagdalena.reservasunimag.domain.port.out.ScheduledUseRepositoryPort;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.ReservationJpaRepository;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.ScheduledUseRow;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * El calendario de ocupación, con una proyección JPQL: las reservas confirmadas de un recurso que se cruzan con el
 * rango, con la asignatura y el programa de las académicas. No lee al titular de las estudiantiles: el calendario no
 * lo muestra (UC8 FR-005).
 */
@Component
class ScheduledUsePersistenceAdapter implements ScheduledUseRepositoryPort {

    private final ReservationJpaRepository reservations;

    ScheduledUsePersistenceAdapter(ReservationJpaRepository reservations) {
        this.reservations = reservations;
    }

    @Override
    public List<ScheduledUse> findByResource(String resourceId, Instant from, Instant to) {
        return reservations.findScheduledUses(resourceId, from, to).stream()
                .map(ScheduledUsePersistenceAdapter::toUse)
                .toList();
    }

    private static ScheduledUse toUse(ScheduledUseRow row) {
        if (row.origin() != ReservationOrigin.ACADEMICO) {
            return new ScheduledUse(ScheduledUse.Kind.STUDENT_RESERVATION, row.startsAt(), row.endsAt(), null, null);
        }
        ScheduledUse.Kind kind = row.blockType() == BlockType.EXTRAORDINARIO
                ? ScheduledUse.Kind.EXTRAORDINARY : ScheduledUse.Kind.CLASS;
        return new ScheduledUse(kind, row.startsAt(), row.endsAt(), row.course(), row.program());
    }
}
