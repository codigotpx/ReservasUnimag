package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging;

import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.NotifiedStatus;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.OutboxMessageJpaEntity;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.CancellationReportJpaRepository;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.OutboxMessageJpaRepository;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.PendingOutboxMessage;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.ReservationRecordJpaRepository;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.StatusChangeJpaRepository;
import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
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
 * {@code send} y el {@code UPDATE}, el evento se republica y el {@code eventId} permite descartar la copia. Las horas
 * salen del reloj de la aplicación, el mismo con el que {@link OutboxQueue} fija el primer intento.
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

    private static final int MAX_ERROR = 500;

    private final OutboxMessageJpaRepository messages;
    private final StatusChangeJpaRepository statusChanges;
    private final CancellationReportJpaRepository cancellations;
    private final ReservationRecordJpaRepository records;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transaction;
    private final ReservationProperties properties;
    /** El hilo del disparo tras el commit: la reserva no espera a Kafka para responder. */
    private final ExecutorService wakeUps = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("outbox-wake-up").daemon().factory());
    /** Hay un turno pedido que todavía no empezó; los commits que llegan mientras tanto caben en él. */
    private final AtomicBoolean wakeUpQueued = new AtomicBoolean();

    OutboxPublisher(OutboxMessageJpaRepository messages, StatusChangeJpaRepository statusChanges,
                    CancellationReportJpaRepository cancellations, ReservationRecordJpaRepository records,
                    KafkaTemplate<String, String> kafka, PlatformTransactionManager transactionManager,
                    ReservationProperties properties) {
        this.messages = messages;
        this.statusChanges = statusChanges;
        this.cancellations = cancellations;
        this.records = records;
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
            for (PendingOutboxMessage message : lockPending()) {
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

    private List<PendingOutboxMessage> lockPending() {
        return messages.lockPending(Instant.now(), PageRequest.of(0, properties.outbox().batch()));
    }

    /** Cuántos pendientes llevan más de {@code alert-after} sin salir; los advierte antes de incumplir UC11 SC-005. */
    public long warnAboutStalePending() {
        long stale = messages.countPendingCreatedBefore(Instant.now().minus(properties.outbox().alertAfter()));
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

    private void markSent(PendingOutboxMessage message) {
        Instant now = Instant.now();
        messages.markSent(message.id(), now);
        // El historial de UC7 dice si el aviso al Módulo 1 llegó (FR-009).
        statusChanges.markNotified(message.id(), NotifiedStatus.SENT);
        // Los historiales de UC11 y UC10 llevan su propia cuenta, que incluye el intento que salió bien.
        cancellations.markDelivered(message.id(), now);
        records.markDelivered(message.id(), now);
        log.info("Outbox: evento {} publicado", message.id());
    }

    private void markFailedAttempt(PendingOutboxMessage message, Exception failure) {
        int attempts = message.attempts() + 1;
        boolean exhausted = attempts >= properties.outbox().maxAttempts();
        Duration wait = BACKOFF.get(Math.min(attempts, BACKOFF.size()) - 1);
        String error = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        String kept = error.length() > MAX_ERROR ? error.substring(0, MAX_ERROR) : error;
        Instant now = Instant.now();
        messages.markFailedAttempt(message.id(), attempts,
                exhausted ? OutboxMessageJpaEntity.FAILED : OutboxMessageJpaEntity.PENDING, now.plus(wait), kept);
        // Los dos historiales hacia el Módulo 3 llevan la misma cuenta (UC10 FR-006, UC11 FR-010).
        DeliveryStatus delivery = exhausted ? DeliveryStatus.FAILED : DeliveryStatus.PENDING;
        cancellations.markFailedAttempt(message.id(), delivery, attempts, kept, now);
        records.markFailedAttempt(message.id(), delivery, attempts, kept, now);
        if (exhausted) {
            statusChanges.markNotified(message.id(), NotifiedStatus.FAILED);
        }
        log.warn("Outbox: evento {} falló (intento {}){}: {}", message.id(), attempts,
                exhausted ? ", queda FAILED" : "", failure.getMessage());
    }
}
