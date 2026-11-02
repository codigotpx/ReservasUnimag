package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChange;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeFilter;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason;
import edu.unimagdalena.reservasunimag.domain.port.out.StatusChangeRepositoryPort;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.StatusChangeJpaEntity;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.mapper.StatusChangeMapper;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.StatusChangeJpaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.StatusChangeJpaRepository.Filters.changedBefore;
import static edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.StatusChangeJpaRepository.Filters.changedFrom;
import static edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.StatusChangeJpaRepository.Filters.ofResource;
import static edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.StatusChangeJpaRepository.Filters.withReason;

/**
 * El historial de UC7 (FR-009), con JPA. Participa en la transacción de quien cambió la ocupación; no abre ninguna.
 * El {@code notified_status} lo actualiza después el publicador de la outbox, con SQL y en su propia transacción.
 */
@Component
class StatusChangePersistenceAdapter implements StatusChangeRepositoryPort {

    private static final Sort CHRONOLOGICAL = Sort.by("changedAt", "id");

    private final StatusChangeJpaRepository changes;

    StatusChangePersistenceAdapter(StatusChangeJpaRepository changes) {
        this.changes = changes;
    }

    @Override
    public void insert(StatusChange change) {
        changes.save(StatusChangeMapper.toEntity(change));
    }

    @Override
    public Optional<StatusChange> findUseStarted(UUID reservationId) {
        return changes.findByReservationIdAndReason(reservationId, StatusChangeReason.USE_STARTED)
                .map(StatusChangeMapper::toDomain);
    }

    @Override
    public Page<StatusChange> findByResource(String resourceId, StatusChangeFilter filter, PageRequest page) {
        Specification<StatusChangeJpaEntity> spec = ofResource(resourceId);
        // Días de Bogotá, ambos incluidos: desde el inicio de from hasta antes del inicio del día siguiente a to.
        if (filter.from() != null) {
            spec = spec.and(changedFrom(filter.from().atStartOfDay(TimeSlot.ZONE).toInstant()));
        }
        if (filter.to() != null) {
            spec = spec.and(changedBefore(filter.to().plusDays(1).atStartOfDay(TimeSlot.ZONE).toInstant()));
        }
        if (filter.reason() != null) {
            spec = spec.and(withReason(filter.reason()));
        }
        var result = changes.findAll(spec,
                org.springframework.data.domain.PageRequest.of(page.page() - 1, page.size(), CHRONOLOGICAL));
        return new Page<>(result.getContent().stream().map(StatusChangeMapper::toDomain).toList(), page.page(),
                page.size(), result.getTotalElements());
    }
}
