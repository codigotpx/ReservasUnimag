package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging;

import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lleva {@code outbox_message} a Kafka después del commit. Lee los pendientes con {@code FOR UPDATE SKIP LOCKED}, así
 * dos instancias no publican el mismo evento; publica con {@code reservation_id} como clave, para que todo lo de una
 * reserva caiga en la misma partición y en orden; y ante un fallo sube {@code attempts} y espera cada vez más, hasta
 * quedar {@code FAILED} al agotar {@code max-attempts}. La entrega es <em>at-least-once</em>: si cae entre el
 * {@code send} y el {@code UPDATE}, el evento se republica y el {@code eventId} permite descartar la copia.
 * <p>
 * Además del turno del reloj, se despierta en cuanto confirma una transacción que dejó algo en la outbox, para que la
 * ficha salga en menos de 5 segundos (UC10 SC-002). El reloj se queda como red de seguridad: es lo que sigue
 * entregando lo que el disparo no alcanzó y lo que reintenta. Los dos turnos no corren a la vez, para no publicar
 * fuera de orden los eventos de una misma reserva.
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
    /** El hilo del disparo tras el commit: la reserva no espera a Kafka para responder. */
    private final ExecutorService wakeUps = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("outbox-wake-up").daemon().factory());
    /** Hay un turno pedido que todavía no empezó; los commits que llegan mientras tanto caben en él. */
    private final AtomicBoolean wakeUpQueued = new AtomicBoolean();

    OutboxPublisher(JdbcClient jdbc, KafkaTemplate<String, String> kafka,
                    PlatformTransactionManager transactionManager, ReservationProperties properties) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.transaction = new TransactionTemplate(transactionManager);
        this.properties = properties;
    }

    /**
     * Un mensaje quedó en la outbox y su transacción confirmó. Una carga de horario deja miles en un solo commit: se
     * piden en un solo turno, no en miles.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnqueued(OutboxMessageEnqueued enqueued) {
        if (!properties.outbox().publishOnCommit() || !wakeUpQueued.compareAndSet(false, true)) {
            return;
        }
        wakeUps.execute(() -> {
            wakeUpQueued.set(false);
            try {
                publishPending();
            } catch (RuntimeException failure) {
                log.error("Outbox: el turno tras el commit no pudo procesar el lote: {}", failure.getMessage());
            }
        });
    }

    @PreDestroy
    void stopWakeUps() {
        wakeUps.shutdownNow();
    }

    @Scheduled(fixedDelayString = "${reservations.outbox.interval}")
    public void publishPendingOnSchedule() {
        try {
            publishPending();
            warnAboutStalePending();
        } catch (RuntimeException failure) {
            // Una caída de la base no debe matar el planificador: se vuelve a intentar en el siguiente turno.
            log.error("Outbox: no se pudo procesar el lote: {}", failure.getMessage());
        }
    }

    /** Un turno del publicador; devuelve cuántos mensajes salieron. */
    public synchronized int publishPending() {
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

    /** Cuántos pendientes llevan más de {@code alert-after} sin salir; los advierte antes de incumplir UC11 SC-005. */
    public long warnAboutStalePending() {
        long stale = jdbc.sql("SELECT count(*) FROM outbox_message WHERE status = 'PENDING' AND created_at < :limit")
                .param("limit", OffsetDateTime.now(ZoneOffset.UTC).minus(properties.outbox().alertAfter()))
                .query(Long.class)
                .single();
        if (stale > 0) {
            log.warn("Outbox: {} evento(s) llevan más de {} sin publicarse", stale, properties.outbox().alertAfter());
        }
        return stale;
    }

    private String topicFor(String type) {
        return switch (type) {
            case OutboxNotifierAdapter.RESERVATION_RECORD -> properties.kafka().topic().record();
            case OutboxNotifierAdapter.CANCELLATION -> properties.kafka().topic().cancellation();
            case OutboxNotifierAdapter.NO_SHOW_ACK -> properties.kafka().topic().noShowAck();
            case OutboxNotifierAdapter.CHECK_OUT_ACK -> properties.kafka().topic().checkOutAck();
            case ResourceStatusOutboxAdapter.RESOURCE_USE_STARTED -> properties.kafka().topic().resourceStatus();
            default -> throw new IllegalStateException("tipo de evento sin topic configurado: " + type);
        };
    }

    private void markSent(Pending message) {
        jdbc.sql("UPDATE outbox_message SET status = 'SENT', sent_at = now(), last_error = NULL WHERE id = :id")
                .param("id", message.id())
                .update();
        // El historial de UC7 dice si el aviso al Módulo 1 llegó (FR-009).
        jdbc.sql("UPDATE status_change SET notified_status = 'SENT' WHERE outbox_message_id = :id")
                .param("id", message.id())
                .update();
        // El historial de UC11 lleva su propia cuenta, que incluye el intento que salió bien (FR-010).
        jdbc.sql("""
                        UPDATE cancellation_report
                        SET delivery_status = 'SENT', delivered_at = now(), attempts = attempts + 1,
                            first_attempt_at = COALESCE(first_attempt_at, now()), last_error = NULL
                        WHERE event_id = :id""")
                .param("id", message.id())
                .update();
        // Y el de UC10, con la misma cuenta (FR-006).
        jdbc.sql("""
                        UPDATE reservation_record
                        SET delivery_status = 'SENT', delivered_at = now(), attempts = attempts + 1,
                            first_attempt_at = COALESCE(first_attempt_at, now()), last_error = NULL
                        WHERE event_id = :id""")
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
        // Los dos historiales hacia el Módulo 3 llevan la misma cuenta (UC10 FR-006, UC11 FR-010).
        for (String history : List.of("cancellation_report", "reservation_record")) {
            jdbc.sql("""
                            UPDATE %s
                            SET delivery_status = :status, attempts = :attempts,
                                first_attempt_at = COALESCE(first_attempt_at, now()), last_error = :error
                            WHERE event_id = :id""".formatted(history))
                    .param("status", exhausted ? "FAILED" : "PENDING")
                    .param("attempts", attempts)
                    .param("error", error.length() > 500 ? error.substring(0, 500) : error)
                    .param("id", message.id())
                    .update();
        }
        if (exhausted) {
            jdbc.sql("UPDATE status_change SET notified_status = 'FAILED' WHERE outbox_message_id = :id")
                    .param("id", message.id())
                    .update();
        }
        log.warn("Outbox: evento {} falló (intento {}){}: {}", message.id(), attempts,
                exhausted ? ", queda FAILED" : "", failure.getMessage());
    }
}
