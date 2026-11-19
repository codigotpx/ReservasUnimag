package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging;

import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.OutboxMessageJpaEntity;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.OutboxMessageJpaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Deja un evento en {@code outbox_message}, en la transacción que esté en curso: si esa transacción se deshace, el
 * evento también. Al confirmar, {@link OutboxPublisher} se despierta para mandarlo.
 */
@Component
class OutboxQueue {

    private final OutboxMessageJpaRepository messages;
    private final ApplicationEventPublisher events;

    OutboxQueue(OutboxMessageJpaRepository messages, ApplicationEventPublisher events) {
        this.messages = messages;
        this.events = events;
    }

    /**
     * {@code occurredAt} viene del reloj del dominio, que en las pruebas puede ir adelantado. El primer intento no se
     * deja para después de ahora: si no, el evento esperaría a que el reloj real lo alcance.
     */
    void enqueue(UUID eventId, String type, UUID reservationId, String payload, Instant occurredAt) {
        Instant now = Instant.now();
        messages.saveAndFlush(OutboxMessageJpaEntity.pending(eventId, type, reservationId, payload, occurredAt,
                occurredAt.isBefore(now) ? occurredAt : now));
        events.publishEvent(new OutboxMessageEnqueued(eventId));
    }
}
