package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging;

import edu.unimagdalena.reservasunimag.KafkaTestcontainersConfiguration;
import edu.unimagdalena.reservasunimag.TestcontainersConfiguration;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
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

/**
 * UC10 contra un Kafka de verdad, con los turnos del publicador a mano: la ficha llega tal como se guardó, no se pierde
 * con el broker caído y su historial cuenta cada intento (FR-004, FR-006, SC-004).
 */
@SpringBootTest(properties = {"reservations.outbox.enabled=true", "reservations.outbox.interval=1h",
        "reservations.outbox.publish-on-commit=false",
        "reservations.kafka.topic.record=module2.reservation.record.delivery-it"})
@ActiveProfiles("local")
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class RecordDeliveryIT {

    private static final CurrentUser STUDENT = new CurrentUser(UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "2021114001", Role.ESTUDIANTE);

    @Autowired
    private OutboxPublisher publisher;
    @Autowired
    private ReserveResourcesPort reserveResources;
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

    private Reservation reserve() {
        return reserveResources.reserve(ReservationRequest.forSpace(STUDENT, "ESP-0107",
                LocalDate.now(TimeSlot.ZONE).plusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0))).reservation();
    }

    @SuppressWarnings("unchecked")
    private OutboxPublisher publisherWithBrokerDown(int maxAttempts) {
        KafkaTemplate<String, String> broken = mock(KafkaTemplate.class);
        when(broken.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Connection refused")));
        var props = new ReservationProperties(
                properties.pageSize(), properties.nextSlotStep(), properties.maxQuota(),
                properties.maxSpaceDuration(), properties.minCancellationNotice(), properties.noShowGrace(),
                properties.loanLossThreshold(), properties.holidays(), properties.resourceTypes(),
                properties.module1(), properties.module3(), properties.jwt(), properties.kafka(),
                new ReservationProperties.Outbox(true, Duration.ofHours(1), 50, maxAttempts, Duration.ofMinutes(25),
                        false),
                properties.upload());
        return new OutboxPublisher(jdbc, broken, transactionManager, props);
    }

    private void makeDue() {
        jdbc.sql("UPDATE outbox_message SET next_attempt_at = now() - interval '1 second' WHERE status = 'PENDING'").update();
    }

    private Map<String, Object> history() {
        return jdbc.sql("""
                        SELECT delivery_status, attempts, last_error, first_attempt_at IS NOT NULL AS tried,
                               delivered_at IS NOT NULL AS delivered
                        FROM reservation_record""").query().singleRow();
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
            consumer.subscribe(List.of(properties.kafka().topic().record()));
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
    void theRecordReachesItsTopicAsStoredAndTheHistorySaysSent() {
        Reservation reservation = reserve();
        String stored = jdbc.sql("SELECT payload::text FROM outbox_message WHERE type = 'RESERVATION_RECORD'")
                .query(String.class).single();

        assertThat(publisher.publishPending()).isEqualTo(1);

        var received = consume(reservation.id().toString());
        assertThat(received).hasSize(1);
        JsonNode event = objectMapper.readTree(received.get(0).value());
        assertThat(event).isEqualTo(objectMapper.readTree(stored));
        assertThat(event.get("type").asString()).isEqualTo("ReservationRecordCreated");
        assertThat(event.get("data").get("sanctionable").asBoolean()).isTrue();
        // El eventId del evento es el de la constancia: con él el Módulo 3 descarta las copias (FR-005).
        assertThat(event.get("eventId").asString()).isEqualTo(
                jdbc.sql("SELECT event_id::text FROM reservation_record").query(String.class).single());

        var history = history();
        assertThat(history.get("delivery_status")).isEqualTo("SENT");
        assertThat(history.get("attempts")).isEqualTo(1);
        assertThat(history.get("delivered")).isEqualTo(true);
        assertThat(history.get("tried")).isEqualTo(true);
        assertThat(history.get("last_error")).isNull();
    }

    @Test
    void withTheBrokerDownTheReservationIsConfirmedAndTheRecordWaitsUntilItComesBack() {
        Reservation reservation = reserve();
        var down = publisherWithBrokerDown(10);

        // Una caída larga: varios turnos fallidos, ninguno pierde la ficha (FR-004, SC-004).
        for (int turn = 0; turn < 4; turn++) {
            makeDue();
            assertThat(down.publishPending()).isZero();
        }
        var waiting = history();
        assertThat(waiting.get("delivery_status")).isEqualTo("PENDING");
        assertThat(waiting.get("attempts")).isEqualTo(4);
        assertThat((String) waiting.get("last_error")).contains("Connection refused");
        assertThat(jdbc.sql("SELECT status FROM reservation WHERE id = :id").param("id", reservation.id())
                .query(String.class).single()).isEqualTo("CONFIRMADA");

        makeDue();
        assertThat(publisher.publishPending()).isEqualTo(1);

        assertThat(consume(reservation.id().toString())).hasSize(1);
        var delivered = history();
        assertThat(delivered.get("delivery_status")).isEqualTo("SENT");
        assertThat(delivered.get("attempts")).isEqualTo(5);
        assertThat(delivered.get("last_error")).isNull();
    }

    @Test
    void whenTheAttemptsRunOutTheHistorySaysFailedWithTheLastError() {
        reserve();
        var down = publisherWithBrokerDown(2);

        down.publishPending();
        makeDue();
        down.publishPending();

        var history = history();
        assertThat(history.get("delivery_status")).isEqualTo("FAILED");
        assertThat(history.get("attempts")).isEqualTo(2);
        assertThat((String) history.get("last_error")).contains("Connection refused");
    }
}
