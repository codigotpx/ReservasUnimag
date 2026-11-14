package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Occupancy;
import edu.unimagdalena.reservasunimag.domain.model.reservation.OccupancyType;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.port.out.OccupancyRepositoryPort;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.ReservationJpaRepository;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.ReservationRow;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Lectura pura de ocupaciones con una proyección JPQL, sin entidades porque UC1 no modifica nada. Las reservas
 * canceladas no ocupan; un préstamo entregado y sin devolver sí, aunque haya vencido.
 */
@Component
class OccupancyPersistenceAdapter implements OccupancyRepositoryPort {

    private final ReservationJpaRepository reservations;

    OccupancyPersistenceAdapter(ReservationJpaRepository reservations) {
        this.reservations = reservations;
    }

    @Override
    public List<Occupancy> findOccupancies(Collection<String> resourceIds, Instant from, Instant to) {
        if (resourceIds.isEmpty()) {
            return List.of();
        }
        return reservations.findOccupying(resourceIds, from, to).stream()
                .map(OccupancyPersistenceAdapter::toOccupancy)
                .toList();
    }

    private static Occupancy toOccupancy(ReservationRow row) {
        if (row.origin() == ReservationOrigin.ACADEMICO) {
            return new Occupancy(row.resourceId(), OccupancyType.ACADEMIC_BLOCK, row.startsAt(), row.endsAt());
        }
        if (row.resourceCategory() == ResourceCategory.ESPACIO) {
            return new Occupancy(row.resourceId(), OccupancyType.SPACE_RESERVATION, row.startsAt(), row.endsAt());
        }
        boolean outstanding = row.pickedUpAt() != null && row.returnedAt() == null;
        return outstanding
                ? new Occupancy(row.resourceId(), OccupancyType.LOAN_PICKED_UP, row.startsAt(), null, row.endsAt())
                : new Occupancy(row.resourceId(), OccupancyType.LOAN_HELD, row.startsAt(), row.endsAt());
    }
}
