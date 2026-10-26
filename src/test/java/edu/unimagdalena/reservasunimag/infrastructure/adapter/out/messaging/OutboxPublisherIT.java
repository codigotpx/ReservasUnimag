package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging;

import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRequest;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.ReserveResourcesPort;
import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;
import edu.unimagdalena.reservasunimag.KafkaTestcontainersConfiguration;
import edu.unimagdalena.reservasunimag.TestcontainersConfiguration;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import static edu.unimagdalena.reservasunimag.support.Database.clean;
import static edu.unimagdalena.reservasunimag.support.Database.reservationWithPendingEvent;
import static org.assertj.core.api.Assertions.assertThat;

/** El publicador de la outbox contra un Kafka de verdad (UC2 T019). */
@SpringBootTest(properties = {"reservations.outbox.enabled=true", "reservations.outbox.interval=1h",
        "reservations.kafka.topic.record=module2.reservation.record.it"})
@ActiveProfiles("local")
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class OutboxPublisherIT {

    @Autowired
    private OutboxPublisher publisher;
    @Autowired
    private ReserveResourcesPort reserveResources;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private KafkaTemplate<String, String> kafka;
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

    /** Lee del topic los mensajes que cumplen el filtro: el broker es compartido entre pruebas y conserva todo. */
    private List<ConsumerRecord<String, String>> consume(int expected, Predicate<ConsumerRecord<String, String>> mine) {
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
            while (received.size() < expected && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (mine.test(record)) {
                        received.add(record);
                    }
                });
            }
            // Un poco más, para detectar duplicados que lleguen después.
            consumer.poll(Duration.ofSeconds(1)).forEach(record -> {
                if (mine.test(record)) {
                    received.add(record);
                }
            });
        }
        return received;
    }

    @Test
    void aRealReservationReachesTheTopicWithItsContractEnvelopeAndKeyAndIsMarkedSent() throws Exception {
        var student = new CurrentUser(UUID.fromString("00000000-0000-0000-0000-000000000001"), "2021114001",
                Role.ESTUDIANTE);
        var reservation = reserveResources.reserve(ReservationRequest.forSpace(student, "ESP-0107",
                LocalDate.now().plusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0))).reservation();
        String stored = jdbc.sql("SELECT payload::text FROM outbox_message").query(String.class).single();

        assertThat(publisher.publishPending()).isEqualTo(1);

        var received = consume(1, record -> reservation.id().toString().equals(record.key()));
        assertThat(received).hasSize(1);
        ConsumerRecord<String, String> message = received.get(0);
        assertThat(message.key()).isEqualTo(reservation.id().toString());
        JsonNode event = objectMapper.readTree(message.value());
        assertThat(objectMapper.readTree(stored)).isEqualTo(event);
        assertThat(event.get("type").asString()).isEqualTo("ReservationRecordCreated");
        assertThat(event.get("version").asInt()).isEqualTo(1);
        assertThat(event.get("data").get("reservationId").asString()).isEqualTo(reservation.id().toString());
        assertThat(event.get("data").get("holder").get("code").asString()).isEqualTo("2021114001");
        assertThat(jdbc.sql("SELECT status FROM outbox_message").query(String.class).single()).isEqualTo("SENT");
        assertThat(jdbc.sql("SELECT sent_at IS NOT NULL FROM outbox_message").query(Boolean.class).single()).isTrue();
        // El eventId del evento es el id de la fila de la outbox.
        assertThat(jdbc.sql("SELECT id::text FROM outbox_message").query(String.class).single())
                .isEqualTo(event.get("eventId").asString());
    }

    @Test
    void whatWasSentIsNotSentAgain() {
        reservationWithPendingEvent(jdbc, 1);

        assertThat(publisher.publishPending()).isEqualTo(1);
        assertThat(publisher.publishPending()).isZero();
    }

    @Test
    void twoInstancesNeverPublishTheSameEvent() throws Exception {
        int total = 24;
        for (int i = 1; i <= total; i++) {
            reservationWithPendingEvent(jdbc, i);
        }
        // Lotes chicos para que las dos instancias de verdad se repartan los pendientes.
        var smallBatches = new ReservationProperties(properties.pageSize(), properties.nextSlotStep(),
                properties.maxQuota(), properties.maxSpaceDuration(), properties.minCancellationNotice(), properties.noShowGrace(), properties.holidays(),
                properties.resourceTypes(), properties.module1(), properties.module3(), properties.jwt(),
                properties.kafka(), new ReservationProperties.Outbox(true, Duration.ofHours(1), 4, 10, Duration.ofMinutes(25)));
        var first = new OutboxPublisher(jdbc, kafka, transactionManager, smallBatches);
        var second = new OutboxPublisher(jdbc, kafka, transactionManager, smallBatches);

        var pool = Executors.newFixedThreadPool(2);
        try {
            List<CompletableFuture<Integer>> runs = new ArrayList<>();
            for (var instance : List.of(first, second)) {
                runs.add(CompletableFuture.supplyAsync(() -> {
                    int published = 0;
                    int turn;
                    do {
                        turn = instance.publishPending();
                        published += turn;
                    } while (turn > 0);
                    return published;
                }, pool));
            }
            int published = runs.stream().mapToInt(CompletableFuture::join).sum();
            assertThat(published).isEqualTo(total);
        } finally {
            pool.shutdownNow();
        }

        Set<String> expectedIds = new HashSet<>(
                jdbc.sql("SELECT id::text FROM outbox_message").query(String.class).list());
        var received = consume(total, record -> {
            try {
                return expectedIds.contains(objectMapper.readTree(record.value()).get("eventId").asString());
            } catch (RuntimeException notJson) {
                return false;
            }
        });
        Set<String> eventIds = new HashSet<>();
        for (var message : received) {
            eventIds.add(objectMapper.readTree(message.value()).get("eventId").asString());
        }
        assertThat(received).hasSize(total);
        assertThat(eventIds).hasSize(total);
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_message WHERE status = 'SENT'").query(Long.class).single())
                .isEqualTo(total);
    }
}
