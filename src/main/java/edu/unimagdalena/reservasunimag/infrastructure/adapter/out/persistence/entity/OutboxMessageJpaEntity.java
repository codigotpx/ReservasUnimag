package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Solo lo que los historiales de fichas y cancelaciones leen de la <em>outbox</em>: cuándo es el próximo intento de
 * un evento pendiente. Es de solo lectura porque la <em>outbox</em> la escriben {@code OutboxNotifierAdapter},
 * {@code ResourceStatusOutboxAdapter} y {@code OutboxPublisher} con SQL; cuando pasen a JPA, esta entidad se completa
 * con el resto de columnas.
 */
@Entity
@Immutable
@Table(name = "outbox_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxMessageJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String status;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
}
