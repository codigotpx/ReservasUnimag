package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Un evento del Módulo 3 ya procesado (la <em>inbox</em> de UC9 y UC12). Solo se escribe con el {@code INSERT ... ON
 * CONFLICT DO NOTHING} de {@code InboxMessageJpaRepository}, nunca con {@code save()}.
 */
@Entity
@Table(name = "inbox_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxMessageJpaEntity {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String type;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    /** Lo pone la base al insertar. */
    @Column(name = "processed_at", insertable = false, updatable = false)
    private Instant processedAt;
}
