package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckFilter;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckOutcome;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckPage;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.port.out.SanctionCheckRepositoryPort;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.SanctionCheckJpaEntity;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.mapper.SanctionCheckMapper;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.SanctionCheckJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;

import static edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.SanctionCheckJpaRepository.Filters.askedBefore;
import static edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.SanctionCheckJpaRepository.Filters.askedFrom;
import static edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.SanctionCheckJpaRepository.Filters.ofPerson;
import static edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.SanctionCheckJpaRepository.Filters.withOutcome;

/**
 * El registro de las consultas al Módulo 3 (UC6 FR-009), con JPA. Cada fila va en su propia transacción, como las
 * denegaciones de UC2: tiene que sobrevivir al rollback de la reserva que se denegó con RES-003. Si no se puede
 * escribir, la consulta sigue: auditar no puede impedir reservar.
 */
@Component
class SanctionCheckPersistenceAdapter implements SanctionCheckRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(SanctionCheckPersistenceAdapter.class);
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("askedAt"), Sort.Order.asc("id"));

    private final SanctionCheckJpaRepository checks;
    private final TransactionTemplate ownTransaction;

    SanctionCheckPersistenceAdapter(SanctionCheckJpaRepository checks, PlatformTransactionManager transactionManager) {
        this.checks = checks;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void record(SanctionCheck check) {
        try {
            ownTransaction.executeWithoutResult(status -> checks.save(SanctionCheckMapper.toEntity(check)));
        } catch (DataAccessException | TransactionException failure) {
            log.error("No se pudo registrar la consulta de sanciones {} ({}): {}", check.id(), check.outcome(),
                    failure.getMessage());
            return;
        }
        // Sin el motivo de la sanción ni el código de la persona: son datos personales y quedan en la tabla (T021).
        log.info("Consulta de sanciones {}: {} para {} en {} ms", check.id(), check.outcome(), check.purpose(),
                check.durationMs());
    }

    @Override
    public SanctionCheckPage search(SanctionCheckFilter filter, PageRequest page) {
        Specification<SanctionCheckJpaEntity> spec = Specification.unrestricted();
        if (filter.personCode() != null) {
            spec = spec.and(ofPerson(filter.personCode()));
        }
        if (filter.outcome() != null) {
            spec = spec.and(withOutcome(filter.outcome()));
        }
        // Días de Bogotá, ambos incluidos: desde el inicio de from hasta antes del inicio del día siguiente a to.
        if (filter.from() != null) {
            spec = spec.and(askedFrom(startOf(filter.from())));
        }
        if (filter.to() != null) {
            spec = spec.and(askedBefore(startOf(filter.to().plusDays(1))));
        }

        var result = checks.findAll(spec,
                org.springframework.data.domain.PageRequest.of(page.page() - 1, page.size(), NEWEST_FIRST));
        // El resumen cuenta cada resultado con los mismos filtros; la tabla es de auditoría y pequeña por persona.
        var summary = new SanctionCheckPage.Summary(result.getTotalElements(),
                count(spec, SanctionCheckOutcome.CLEAR), count(spec, SanctionCheckOutcome.SANCTIONED),
                count(spec, SanctionCheckOutcome.NO_HISTORY), count(spec, SanctionCheckOutcome.NOT_VERIFIED));
        return new SanctionCheckPage(new Page<>(result.getContent().stream().map(SanctionCheckMapper::toDomain)
                .toList(), page.page(), page.size(), summary.total()), summary);
    }

    private long count(Specification<SanctionCheckJpaEntity> spec, SanctionCheckOutcome outcome) {
        return checks.count(spec.and(withOutcome(outcome)));
    }

    private static Instant startOf(LocalDate day) {
        return day.atStartOfDay(TimeSlot.ZONE).toInstant();
    }
}
