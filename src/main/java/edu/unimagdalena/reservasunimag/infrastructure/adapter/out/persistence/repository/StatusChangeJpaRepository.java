package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.domain.model.resource.NotifiedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.StatusChangeJpaEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface StatusChangeJpaRepository extends JpaRepository<StatusChangeJpaEntity, UUID>,
        JpaSpecificationExecutor<StatusChangeJpaEntity> {

    Optional<StatusChangeJpaEntity> findByReservationIdAndReason(UUID reservationId, StatusChangeReason reason);

    /** Si el aviso al Módulo 1 llegó (UC7 FR-009); lo marca el publicador de la <em>outbox</em>. */
    @Modifying
    @Query("UPDATE StatusChangeJpaEntity c SET c.notifiedStatus = :status WHERE c.outboxMessageId = :outboxId")
    void markNotified(@Param("outboxId") UUID outboxMessageId, @Param("status") NotifiedStatus status);

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
