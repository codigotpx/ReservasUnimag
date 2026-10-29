package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging;

import edu.unimagdalena.reservasunimag.domain.model.reservation.UseStart;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryStatusNotifierPort;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging.event.ResourceUseStartedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.ZoneOffset;
import java.util.UUID;

/**
 * El aviso de inicio de uso al Módulo 1, por la misma outbox que los eventos del Módulo 3: la operación se completa
 * aunque el Módulo 1 esté caído y el aviso sale después del commit, con reintentos (UC7 FR-007, SC-004). Vive junto a
 * la outbox y no en {@code module1/} porque lo que hace es escribir en ella, igual que {@link OutboxNotifierAdapter}.
 */
@Component
class ResourceStatusOutboxAdapter implements InventoryStatusNotifierPort {

    static final String RESOURCE_USE_STARTED = "RESOURCE_USE_STARTED";

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    ResourceStatusOutboxAdapter(JdbcClient jdbc, ObjectMapper objectMapper, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.events = events;
    }

    @Override
    public UUID useStarted(UseStart useStart) {
        UUID eventId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO outbox_message (id, type, reservation_id, payload, created_at, next_attempt_at)
                        VALUES (:id, :type, :reservationId, CAST(:payload AS jsonb), :now, LEAST(:now, now()))""")
                .param("id", eventId)
                .param("type", RESOURCE_USE_STARTED)
                .param("reservationId", useStart.reservation().id())
                .param("payload", objectMapper.writeValueAsString(ResourceUseStartedEvent.from(eventId, useStart)))
                .param("now", useStart.startedAt().atOffset(ZoneOffset.UTC))
                .update();
        events.publishEvent(new OutboxMessageEnqueued(eventId));
        return eventId;
    }
}
