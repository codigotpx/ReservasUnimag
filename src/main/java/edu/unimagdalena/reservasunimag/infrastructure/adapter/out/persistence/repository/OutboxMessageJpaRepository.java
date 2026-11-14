package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.OutboxMessageJpaEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** La <em>outbox</em> en JPQL: lo que lee y marca el publicador. */
public interface OutboxMessageJpaRepository extends JpaRepository<OutboxMessageJpaEntity, UUID> {

    /**
     * Los pendientes cuyo turno llegó, del más viejo al más nuevo, bloqueados y saltando los que ya bloqueó otra
     * instancia: así dos instancias no publican el mismo evento. El tiempo de espera {@code -2} es como Hibernate pide
     * {@code SKIP LOCKED}; la página da el tamaño del lote.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT new edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.PendingOutboxMessage(
                       o.id, o.type, o.reservationId, o.payload, o.attempts)
            FROM OutboxMessageJpaEntity o
            WHERE o.status = 'PENDING' AND o.nextAttemptAt <= :now
            ORDER BY o.createdAt""")
    List<PendingOutboxMessage> lockPending(@Param("now") Instant now, Pageable batch);

    @Query("SELECT count(o) FROM OutboxMessageJpaEntity o WHERE o.status = 'PENDING' AND o.createdAt < :limit")
    long countPendingCreatedBefore(@Param("limit") Instant limit);

    @Modifying
    @Query("""
            UPDATE OutboxMessageJpaEntity o SET o.status = 'SENT', o.sentAt = :now, o.lastError = NULL
            WHERE o.id = :id""")
    void markSent(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE OutboxMessageJpaEntity o
            SET o.attempts = :attempts, o.status = :status, o.nextAttemptAt = :next, o.lastError = :error
            WHERE o.id = :id""")
    void markFailedAttempt(@Param("id") UUID id, @Param("attempts") int attempts, @Param("status") String status,
                           @Param("next") Instant next, @Param("error") String error);
}
