package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Denial;
import edu.unimagdalena.reservasunimag.domain.port.out.DenialRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZoneOffset;

/**
 * Cada denegación queda en su propia transacción: una por RES-004 ocurre cuando la de la reserva ya está condenada,
 * y el registro tiene que sobrevivir a ese rollback (UC2 FR-005). Solo guarda el id del titular, no sus datos.
 */
@Component
class DenialPersistenceAdapter implements DenialRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(DenialPersistenceAdapter.class);

    private final JdbcClient jdbc;
    private final TransactionTemplate ownTransaction;

    DenialPersistenceAdapter(JdbcClient jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void record(Denial denial) {
        ownTransaction.executeWithoutResult(status -> jdbc.sql("""
                        INSERT INTO denial (id, user_id, resource_id, code, occurred_at)
                        VALUES (:id, :userId, :resourceId, :code, :occurredAt)""")
                .param("id", denial.id())
                .param("userId", denial.userId())
                .param("resourceId", denial.resourceId())
                .param("code", denial.code().code())
                .param("occurredAt", denial.occurredAt().atOffset(ZoneOffset.UTC))
                .update());
        log.info("Reserva denegada: código {} sobre {}", denial.code().code(), denial.resourceId());
    }
}
