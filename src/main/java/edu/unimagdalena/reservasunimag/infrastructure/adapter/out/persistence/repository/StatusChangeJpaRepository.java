package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.StatusChangeJpaEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface StatusChangeJpaRepository extends JpaRepository<StatusChangeJpaEntity, UUID>,
        JpaSpecificationExecutor<StatusChangeJpaEntity> {

    Optional<StatusChangeJpaEntity> findByReservationIdAndReason(UUID reservationId, StatusChangeReason reason);

    /** Los filtros opcionales del historial (UC7 FR-009), como Criteria API: los valores van siempre como parámetros. */
    final class Filters {

        private Filters() {
        }

        public static Specification<StatusChangeJpaEntity> ofResource(String resourceId) {
            return (root, query, cb) -> cb.equal(root.get("resourceId"), resourceId);
        }

        public static Specification<StatusChangeJpaEntity> changedFrom(Instant from) {
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("changedAt"), from);
        }

        public static Specification<StatusChangeJpaEntity> changedBefore(Instant to) {
            return (root, query, cb) -> cb.lessThan(root.get("changedAt"), to);
        }

        public static Specification<StatusChangeJpaEntity> withReason(StatusChangeReason reason) {
            return (root, query, cb) -> cb.equal(root.get("reason"), reason);
        }
    }
}
