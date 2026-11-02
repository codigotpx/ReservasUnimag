package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckOutcome;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.SanctionCheckJpaEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.UUID;

public interface SanctionCheckJpaRepository extends JpaRepository<SanctionCheckJpaEntity, UUID>,
        JpaSpecificationExecutor<SanctionCheckJpaEntity> {

    /** Los filtros opcionales de la auditoría (UC6 FR-009), como Criteria API: los valores van siempre como parámetros. */
    final class Filters {

        private Filters() {
        }

        public static Specification<SanctionCheckJpaEntity> ofPerson(String personCode) {
            return (root, query, cb) -> cb.equal(root.get("personCode"), personCode);
        }

        public static Specification<SanctionCheckJpaEntity> withOutcome(SanctionCheckOutcome outcome) {
            return (root, query, cb) -> cb.equal(root.get("outcome"), outcome);
        }

        public static Specification<SanctionCheckJpaEntity> askedFrom(Instant from) {
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("askedAt"), from);
        }

        public static Specification<SanctionCheckJpaEntity> askedBefore(Instant to) {
            return (root, query, cb) -> cb.lessThan(root.get("askedAt"), to);
        }
    }
}
