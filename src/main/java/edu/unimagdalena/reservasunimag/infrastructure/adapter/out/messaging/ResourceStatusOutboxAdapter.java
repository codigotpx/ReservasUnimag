package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging;

import edu.unimagdalena.reservasunimag.domain.model.reservation.UseStart;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryStatusNotifierPort;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging.event.ResourceUseStartedEvent;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * El aviso de inicio de uso al Módulo 1, por la misma outbox que los eventos del Módulo 3: la operación se completa
 * aunque el Módulo 1 esté caído y el aviso sale después del commit, con reintentos (UC7 FR-007, SC-004). Vive junto a
 * la outbox y no en {@code module1/} porque lo que hace es escribir en ella, igual que {@link OutboxNotifierAdapter}.
 */
@Component
class ResourceStatusOutboxAdapter implements InventoryStatusNotifierPort {

    static final String RESOURCE_USE_STARTED = "RESOURCE_USE_STARTED";

    private final OutboxQueue outbox;
    private final ObjectMapper objectMapper;

    ResourceStatusOutboxAdapter(OutboxQueue outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Override
    public UUID useStarted(UseStart useStart) {
        UUID eventId = UUID.randomUUID();
        outbox.enqueue(eventId, RESOURCE_USE_STARTED, useStart.reservation().id(),
                objectMapper.writeValueAsString(ResourceUseStartedEvent.from(eventId, useStart)), useStart.startedAt());
        return eventId;
    }
}
