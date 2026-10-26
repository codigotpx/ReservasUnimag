package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging;

import edu.unimagdalena.reservasunimag.KafkaTestcontainersConfiguration;
import edu.unimagdalena.reservasunimag.TestcontainersConfiguration;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.CancelReservationPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReserveResourcesPort;
import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static edu.unimagdalena.reservasunimag.support.Database.clean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** UC11 contra un Kafka de verdad: el reporte llega, no se pierde con el broker caído y su historial lo cuenta. */
@SpringBootTest(properties = {"reservations.outbox.enabled=true", "reservations.outbox.interval=1h",
        "reservations.kafka.topic.cancellation=module2.reservation.cancellation.it"})
@ActiveProfiles("local")
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class CancellationDeliveryIT {

    private static final CurrentUser STUDENT = new CurrentUser(UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "2021114001", Role.ESTUDIANTE);

    @Autowired
    private OutboxPublisher publisher;
    @Autowired
    private ReserveResourcesPort reserveResources;
    @Autowired
    private CancelReservationPort cancelReservation;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ReservationProperties properties;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private KafkaContainer kafkaContainer;

    @BeforeEach
    void emptyTables() {
        clean(jdbc);
    }

    private Reservation reserveAndCancel() {
        Reservation reservation = reserveResources.reserve(ReservationRequest.forSpace(STUDENT, "ESP-0107",
                LocalDate.now(TimeSlot.ZONE).plusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0))).reservation();
        cancelReservation.cancelByHolder(STUDENT, reservation.id());
        return reservation;
    }

    @SuppressWarnings("unchecked")
    private OutboxPublisher publisherWithBrokerDown(int maxAttempts) {
        KafkaTemplate<String, String> broken = mock(KafkaTemplate.class);
        when(broken.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Connection refused")));
        var props = new ReservationProperties(properties.pageSize(), properties.nextSlotStep(), properties.maxQuota(),
                properties.maxSpaceDuration(), properties.minCancellationNotice(), properties.holidays(),
                properties.resourceTypes(), properties.module1(), properties.module3(), properties.jwt(),
                properties.kafka(), new ReservationProperties.Outbox(true, Duration.ofHours(1), 50, maxAttempts,
                Duration.ofMinutes(25)));
        return new OutboxPublisher(jdbc, broken, transactionManager, props);
    }

    private void makeDue() {
        jdbc.sql("UPDATE outbox_message SET next_attempt_at = now() - interval '1 second' WHERE status = 'PENDING'").update();
    }

    private Map<String, Object> report() {
        return jdbc.sql("""
                        SELECT delivery_status, attempts, last_error, first_attempt_at IS NOT NULL AS tried,
                               delivered_at IS NOT NULL AS delivered
                        FROM cancellation_report""").query().singleRow();
    }

    private List<ConsumerRecord<String, String>> consume(String key) {
        var config = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (var consumer = new KafkaConsumer<String, String>(config)) {
            consumer.subscribe(List.of(properties.kafka().topic().cancellation()));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (received.isEmpty() && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (key.equals(record.key())) {
                        received.add(record);
                    }
                });
            }
            consumer.poll(Duration.ofSeconds(1)).forEach(record -> {
                if (key.equals(record.key())) {
                    received.add(record);
                }
            });
        }
        return received;
    }

    @Test
    void theCancellationReachesItsTopicAsStoredAndTheHistorySaysSent() throws Exception {
        Reservation reservation = reserveAndCancel();
        String stored = jdbc.sql("SELECT payload::text FROM outbox_message WHERE type = 'CANCELLATION'")
                .query(String.class).single();

        // La ficha y la cancelación salen en el mismo turno, en el orden en que ocurrieron.
        assertThat(publisher.publishPending()).isEqualTo(2);

        var received = consume(reservation.id().toString());
        assertThat(received).hasSize(1);
        JsonNode event = objectMapper.readTree(received.get(0).value());
        assertThat(event).isEqualTo(objectMapper.readTree(stored));
        assertThat(event.get("type").asString()).isEqualTo("ReservationCancelled");
        assertThat(event.get("data").get("cancellationOrigin").asString()).isEqualTo("HOLDER");
        assertThat(event.get("data").get("attributableToPerson").asBoolean()).isTrue();
        assertThat(event.get("data").get("resource").get("name").asString()).isEqualTo("Sala de Estudio 3");
        assertThat(event.get("eventId").asString()).isEqualTo(
                jdbc.sql("SELECT event_id::text FROM cancellation_report").query(String.class).single());

        var report = report();
        assertThat(report.get("delivery_status")).isEqualTo("SENT");
        assertThat(report.get("attempts")).isEqualTo(1);
        assertThat(report.get("delivered")).isEqualTo(true);
        assertThat(report.get("tried")).isEqualTo(true);
        assertThat(report.get("last_error")).isNull();
    }

    @Test
    void withTheBrokerDownTheReportWaitsAndIsDeliveredWhenItComesBack() {
        Reservation reservation = reserveAndCancel();
        var down = publisherWithBrokerDown(10);

        // Una caída larga: varios turnos fallidos, ninguno pierde el reporte (UC11 escenario 4, SC-005).
        for (int turn = 0; turn < 4; turn++) {
            makeDue();
            assertThat(down.publishPending()).isZero();
        }
        var waiting = report();
        assertThat(waiting.get("delivery_status")).isEqualTo("PENDING");
        // La cancelación ni se intenta mientras la ficha de su misma reserva no salga: no adelanta a la ficha.
        assertThat(jdbc.sql("SELECT attempts FROM outbox_message WHERE type = 'RESERVATION_RECORD'")
                .query(Integer.class).single()).isEqualTo(4);
        int failedAttempts = (int) waiting.get("attempts");

        makeDue();
        assertThat(publisher.publishPending()).isEqualTo(2);

        assertThat(consume(reservation.id().toString())).hasSize(1);
        var delivered = report();
        assertThat(delivered.get("delivery_status")).isEqualTo("SENT");
        assertThat(delivered.get("attempts")).isEqualTo(failedAttempts + 1);
        assertThat(delivered.get("last_error")).isNull();
    }

    @Test
    void whenTheAttemptsRunOutTheHistorySaysFailedWithTheLastError() {
        reserveAndCancel();
        // La ficha va primero y bloquea a la cancelación de su misma reserva: se quitan del camino.
        jdbc.sql("DELETE FROM outbox_message WHERE type = 'RESERVATION_RECORD'").update();
        var down = publisherWithBrokerDown(2);

        down.publishPending();
        makeDue();
        down.publishPending();

        var report = report();
        assertThat(report.get("delivery_status")).isEqualTo("FAILED");
        assertThat(report.get("attempts")).isEqualTo(2);
        assertThat((String) report.get("last_error")).contains("Connection refused");
    }

    @Test
    void aReportStuckLongerThanTheAlertThresholdIsWarned() {
        reserveAndCancel();
        assertThat(publisher.warnAboutStalePending()).isZero();

        jdbc.sql("UPDATE outbox_message SET created_at = now() - interval '26 minutes'").update();

        assertThat(publisher.warnAboutStalePending()).isEqualTo(2);
    }
}
