package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging;

import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowAck;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecord;
import edu.unimagdalena.reservasunimag.domain.port.out.Module3NotifierPort;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging.event.NoShowAckEvent;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging.event.ReservationCancelledEvent;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging.event.ReservationRecordEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Deja el evento en {@code outbox_message}, en la transacción que esté en curso: si la reserva se deshace, el
 * evento también. El {@code payload} es el JSON completo del contrato, así que republicar es reenviar el mismo texto.
 */
@Component
class OutboxNotifierAdapter implements Module3NotifierPort {

    private static final Logger log = LoggerFactory.getLogger(OutboxNotifierAdapter.class);

    static final String RESERVATION_RECORD = "RESERVATION_RECORD";
    static final String CANCELLATION = "CANCELLATION";
    static final String NO_SHOW_ACK = "NO_SHOW_ACK";

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
        insert(eventId, RESERVATION_RECORD, record.reservation().id(),
                objectMapper.writeValueAsString(ReservationRecordEvent.from(eventId, record)), record.occurredAt());
    }

    @Override
    public void reservationCancelled(CancellationReport report) {
        insert(report.eventId(), CANCELLATION, report.reservationId(),
                objectMapper.writeValueAsString(ReservationCancelledEvent.from(report)), report.cancelledAt());
        // Sin datos personales más allá del identificador (plan de UC4, T023).
        log.info("Cancelación de la reserva {} encolada: origen {}, antelación {} min, evento {}",
                report.reservationId(), report.origin(),
                report.noticeMinutes() == null ? "-" : report.noticeMinutes(), report.eventId());
    }

    @Override
    public void noShowAcknowledged(NoShowAck ack) {
        UUID eventId = UUID.randomUUID();
        insert(eventId, NO_SHOW_ACK, ack.reservation().id(),
                objectMapper.writeValueAsString(NoShowAckEvent.from(eventId, ack)), ack.occurredAt());
        // Sin el note ni el verifiedBy del Módulo 3 (plan de UC9, T023).
        log.info("No asistencia de la reserva {} (reporte {}): {}", ack.reservation().id(), ack.sourceEventId(),
                ack.accepted() ? (ack.duplicate() ? "repetida" : "registrada") : "rechazada, " + ack.rejection());
    }

    private void insert(UUID eventId, String type, UUID reservationId, String payload, Instant now) {
        jdbc.sql("""
                        INSERT INTO outbox_message (id, type, reservation_id, payload, created_at, next_attempt_at)
                        VALUES (:id, :type, :reservationId, CAST(:payload AS jsonb), :now, :now)""")
                .param("id", eventId)
                .param("type", type)
                .param("reservationId", reservationId)
                .param("payload", payload)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .update();
    }
}
