package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Denial;
import edu.unimagdalena.reservasunimag.domain.port.out.DenialRepositoryPort;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.DenialJpaEntity;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.DenialJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cada denegación queda en su propia transacción: una por RES-004 ocurre cuando la de la reserva ya está condenada,
 * y el registro tiene que sobrevivir a ese rollback (UC2 FR-005). Solo guarda el id del titular, no sus datos.
 */
@Component
class DenialPersistenceAdapter implements DenialRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(DenialPersistenceAdapter.class);

    private final DenialJpaRepository denials;
    private final TransactionTemplate ownTransaction;

    DenialPersistenceAdapter(DenialJpaRepository denials, PlatformTransactionManager transactionManager) {
        this.denials = denials;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void record(Denial denial) {
        ownTransaction.executeWithoutResult(status -> denials.save(new DenialJpaEntity(denial.id(), denial.userId(),
                denial.resourceId(), denial.code().code(), denial.occurredAt())));
        log.info("Reserva denegada: código {} sobre {}", denial.code().code(), denial.resourceId());
    }
}
