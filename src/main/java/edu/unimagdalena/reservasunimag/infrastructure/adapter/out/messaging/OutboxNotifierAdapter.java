package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging;

import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecord;
import edu.unimagdalena.reservasunimag.domain.port.out.Module3NotifierPort;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging.event.ReservationRecordEvent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Deja el evento en {@code outbox_message}, en la transacción que esté en curso: si la reserva se deshace, el
 * evento también. El {@code payload} es el JSON completo del contrato, así que republicar es reenviar el mismo texto.
 */
@Component
class OutboxNotifierAdapter implements Module3NotifierPort {

    static final String RESERVATION_RECORD = "RESERVATION_RECORD";

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    OutboxNotifierAdapter(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void reservationCreated(ReservationRecord record) {
        // El id de la fila es el eventId: identifica el hecho, no el intento de envío.
        UUID eventId = UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(ReservationRecordEvent.from(eventId, record));
        jdbc.sql("""
                        INSERT INTO outbox_message (id, type, reservation_id, payload, created_at, next_attempt_at)
                        VALUES (:id, :type, :reservationId, CAST(:payload AS jsonb), :now, :now)""")
                .param("id", eventId)
                .param("type", RESERVATION_RECORD)
                .param("reservationId", record.reservation().id())
                .param("payload", payload)
                .param("now", record.occurredAt().atOffset(ZoneOffset.UTC))
                .update();
    }
}
