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
 * Un evento que espera salir a Kafka. Su id es el {@code eventId} del evento. El {@code payload} es el JSON completo
 * del contrato, en {@code jsonb} como texto, así que republicar es reenviar el mismo texto. La entidad solo se
 * inserta; el publicador cambia el estado con {@code UPDATE} de JPQL.
 */
@Entity
@Table(name = "outbox_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxMessageJpaEntity extends AssignedIdEntity {

    public static final String PENDING = "PENDING";
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";

    @Id
    private UUID id;

    @Column(nullable = false)
    private String type;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    /** Un evento nuevo, listo para el próximo turno del publicador. */
    public static OutboxMessageJpaEntity pending(UUID id, String type, UUID reservationId, String payload,
                                                 Instant createdAt, Instant nextAttemptAt) {
        OutboxMessageJpaEntity message = new OutboxMessageJpaEntity();
        message.id = id;
        message.type = type;
        message.reservationId = reservationId;
        message.payload = payload;
        message.status = PENDING;
        message.createdAt = createdAt;
        message.nextAttemptAt = nextAttemptAt;
        return message;
    }
}
