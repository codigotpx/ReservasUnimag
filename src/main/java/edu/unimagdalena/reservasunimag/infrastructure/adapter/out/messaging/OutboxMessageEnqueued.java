package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging;

import java.util.UUID;

/**
 * Se dejó un mensaje en la outbox dentro de la transacción en curso. {@link OutboxPublisher} lo escucha después del
 * commit para publicar sin esperar a su próximo turno (UC10 SC-002); si la transacción se deshace, nadie lo oye.
 */
record OutboxMessageEnqueued(UUID eventId) {
}
