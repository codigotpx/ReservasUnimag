package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.messaging;

import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.InboxMessageJpaRepository;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.ReservationJpaRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * La <em>inbox</em>: Kafka entrega <em>at-least-once</em>, así que un mismo evento puede llegar dos veces. Se llama
 * dentro de la transacción que aplica el evento; si esa transacción se deshace, el evento tampoco queda como
 * procesado y la reentrega lo vuelve a intentar. Es compartida por los consumidores del Módulo 3 (UC9 y UC12).
 */
@Component
class InboxGuard {

    private final InboxMessageJpaRepository inbox;
    private final ReservationJpaRepository reservations;

    InboxGuard(InboxMessageJpaRepository inbox, ReservationJpaRepository reservations) {
        this.inbox = inbox;
        this.reservations = reservations;
    }

    /**
     * Registra el evento y dice si es la primera vez que llega. Una reserva inexistente queda con
     * {@code reservation_id} nulo en vez de romper la clave foránea: el caso de uso es quien decide qué hacer con ella.
     */
    boolean firstTime(UUID eventId, String topic, String type, UUID reservationId, String payload) {
        UUID existing = reservationId != null && reservations.existsById(reservationId) ? reservationId : null;
        return inbox.insertIfAbsent(eventId, topic, type, existing, payload) == 1;
    }
}
