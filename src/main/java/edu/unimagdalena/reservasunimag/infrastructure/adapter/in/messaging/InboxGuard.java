package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.messaging;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * La <em>inbox</em>: Kafka entrega <em>at-least-once</em>, así que un mismo evento puede llegar dos veces. Se llama
 * dentro de la transacción que aplica el evento; si esa transacción se deshace, el evento tampoco queda como
 * procesado y la reentrega lo vuelve a intentar. Es compartida por los consumidores del Módulo 3 (UC9 y UC12).
 */
@Component
class InboxGuard {

    private final JdbcClient jdbc;

    InboxGuard(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Registra el evento y dice si es la primera vez que llega. Una reserva inexistente queda con
     * {@code reservation_id} nulo en vez de romper la clave foránea: el caso de uso es quien decide qué hacer con ella.
     */
    boolean firstTime(UUID eventId, String topic, String type, UUID reservationId, String payload) {
        return jdbc.sql("""
                        INSERT INTO inbox_message (event_id, topic, type, reservation_id, payload)
                        VALUES (:eventId, :topic, :type, (SELECT id FROM reservation WHERE id = :reservationId),
                                CAST(:payload AS jsonb))
                        ON CONFLICT (event_id) DO NOTHING""")
                .param("eventId", eventId)
                .param("topic", topic)
                .param("type", type)
                .param("reservationId", reservationId)
                .param("payload", payload)
                .update() == 1;
    }
}
