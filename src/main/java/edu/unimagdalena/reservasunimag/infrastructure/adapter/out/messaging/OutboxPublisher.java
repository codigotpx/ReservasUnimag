package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging;

import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Lleva {@code outbox_message} a Kafka después del commit. Lee los pendientes con {@code FOR UPDATE SKIP LOCKED}, así
 * dos instancias no publican el mismo evento; publica con {@code reservation_id} como clave, para que todo lo de una
 * reserva caiga en la misma partición y en orden; y ante un fallo sube {@code attempts} y espera cada vez más, hasta
 * quedar {@code FAILED} al agotar {@code max-attempts}. La entrega es <em>at-least-once</em>: si cae entre el
 * {@code send} y el {@code UPDATE}, el evento se republica y el {@code eventId} permite descartar la copia.
 */
@Component
@ConditionalOnProperty(name = "reservations.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    /** Espera tras el 1.º, 2.º, 3.º… fallo; el último valor se repite. */
    private static final List<Duration> BACKOFF = List.of(
            Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10));
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private record Pending(UUID id, String type, UUID reservationId, String payload, int attempts) {
    }

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transaction;
    private final ReservationProperties properties;

    OutboxPublisher(JdbcClient jdbc, KafkaTemplate<String, String> kafka,
                    PlatformTransactionManager transactionManager, ReservationProperties properties) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.transaction = new TransactionTemplate(transactionManager);
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${reservations.outbox.interval}")
    public void publishPendingOnSchedule() {
        try {
            publishPending();
        } catch (RuntimeException failure) {
            // Una caída de la base no debe matar el planificador: se vuelve a intentar en el siguiente turno.
            log.error("Outbox: no se pudo procesar el lote: {}", failure.getMessage());
        }
    }

    /** Un turno del publicador; devuelve cuántos mensajes salieron. */
    public int publishPending() {
        Integer sent = transaction.execute(status -> {
            int published = 0;
            Set<UUID> blockedReservations = new HashSet<>();
            for (Pending message : lockPending()) {
                // Si falló un evento de esta reserva, los siguientes esperan: no se publica fuera de orden.
                if (blockedReservations.contains(message.reservationId())) {
                    continue;
                }
                try {
                    kafka.send(topicFor(message.type()), message.reservationId().toString(), message.payload())
                            .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    markSent(message);
                    published++;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    blockedReservations.add(message.reservationId());
                } catch (Exception failure) {
                    markFailedAttempt(message, failure);
                    blockedReservations.add(message.reservationId());
                }
            }
            return published;
        });
        return sent == null ? 0 : sent;
    }

    private List<Pending> lockPending() {
        return jdbc.sql("""
                        SELECT id, type, reservation_id, payload::text AS payload, attempts
                        FROM outbox_message
                        WHERE status = 'PENDING' AND next_attempt_at <= now()
                        ORDER BY created_at
                        LIMIT :batch
                        FOR UPDATE SKIP LOCKED""")
                .param("batch", properties.outbox().batch())
                .query((row, n) -> new Pending(row.getObject("id", UUID.class), row.getString("type"),
                        row.getObject("reservation_id", UUID.class), row.getString("payload"),
                        row.getInt("attempts")))
                .list();
    }

    private String topicFor(String type) {
        if (OutboxNotifierAdapter.RESERVATION_RECORD.equals(type)) {
            return properties.kafka().topic().record();
        }
        throw new IllegalStateException("tipo de evento sin topic configurado: " + type);
    }

    private void markSent(Pending message) {
        jdbc.sql("UPDATE outbox_message SET status = 'SENT', sent_at = now(), last_error = NULL WHERE id = :id")
                .param("id", message.id())
                .update();
        log.info("Outbox: evento {} publicado", message.id());
    }

    private void markFailedAttempt(Pending message, Exception failure) {
        int attempts = message.attempts() + 1;
        boolean exhausted = attempts >= properties.outbox().maxAttempts();
        Duration wait = BACKOFF.get(Math.min(attempts, BACKOFF.size()) - 1);
        String error = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        jdbc.sql("""
                        UPDATE outbox_message
                        SET attempts = :attempts, status = :status, next_attempt_at = :next, last_error = :error
                        WHERE id = :id""")
                .param("attempts", attempts)
                .param("status", exhausted ? "FAILED" : "PENDING")
                .param("next", OffsetDateTime.now(ZoneOffset.UTC).plus(wait))
                .param("error", error.length() > 500 ? error.substring(0, 500) : error)
                .param("id", message.id())
                .update();
        log.warn("Outbox: evento {} falló (intento {}){}: {}", message.id(), attempts,
                exhausted ? ", queda FAILED" : "", failure.getMessage());
    }
}
