package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging;

import edu.unimagdalena.reservasunimag.KafkaTestcontainersConfiguration;
import edu.unimagdalena.reservasunimag.TestcontainersConfiguration;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.CancelReservationPort;
import edu.unimagdalena.reservasunimag.domain.port.in.RegisterUseStartedPort;
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
import static edu.unimagdalena.reservasunimag.support.Database.startedReservation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** El aviso de inicio de uso contra un Kafka de verdad, y que nada más sale hacia el Módulo 1 (UC7 T014). */
@SpringBootTest(properties = {"reservations.outbox.enabled=true", "reservations.outbox.interval=1h",
        "reservations.outbox.publish-on-commit=false",
        "reservations.academic.use-start-enabled=false",
        "reservations.kafka.topic.resource-status=module2.resource.status.it"})
@ActiveProfiles("local")
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class UseStartedNotificationIT {

    private static final CurrentUser STUDENT = new CurrentUser(UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "2021114001", Role.ESTUDIANTE);

    @Autowired
    private OutboxPublisher publisher;
    @Autowired
    private RegisterUseStartedPort registerUseStarted;
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

    private List<ConsumerRecord<String, String>> consume(String key, Duration wait) {
        var config = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (var consumer = new KafkaConsumer<String, String>(config)) {
            consumer.subscribe(List.of(properties.kafka().topic().resourceStatus()));
            long deadline = System.nanoTime() + wait.toNanos();
            while (System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (key.equals(record.key())) {
                        received.add(record);
                    }
                });
                if (!received.isEmpty()) {
                    break;
                }
            }
        }
        return received;
    }

    @Test
    void theArrivalReachesModule1AndTheHistorySaysSent() throws Exception {
        UUID reservation = startedReservation(jdbc, STUDENT.id(), "ESP-0107", 5);
        registerUseStarted.startByHolder(STUDENT, reservation);
        String stored = jdbc.sql("SELECT payload::text FROM outbox_message").query(String.class).single();

        assertThat(publisher.publishPending()).isEqualTo(1);

        var received = consume(reservation.toString(), Duration.ofSeconds(30));
        assertThat(received).hasSize(1);
        JsonNode event = objectMapper.readTree(received.get(0).value());
        assertThat(event).isEqualTo(objectMapper.readTree(stored));
        assertThat(event.get("type").asString()).isEqualTo("ResourceUseStarted");
        assertThat(event.get("data").get("newStatus").asString()).isEqualTo("EN_USO");
        assertThat(jdbc.sql("SELECT notified_status FROM status_change").query(String.class).single())
                .isEqualTo("SENT");
    }

    @Test
    void reservingAndCancellingSendNothingToModule1() {
        var reservation = reserveResources.reserve(ReservationRequest.forSpace(STUDENT, "ESP-0107",
                LocalDate.now(TimeSlot.ZONE).plusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0))).reservation();
        cancelReservation.cancelByHolder(STUDENT, reservation.id());

        publisher.publishPending();

        assertThat(consume(reservation.id().toString(), Duration.ofSeconds(5))).isEmpty();
        assertThat(jdbc.sql("SELECT count(*) FROM status_change WHERE notified_status <> 'NOT_SENT'")
                .query(Long.class).single()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void whenTheAttemptsRunOutTheHistorySaysFailed() {
        UUID reservation = startedReservation(jdbc, STUDENT.id(), "ESP-0107", 5);
        registerUseStarted.startByHolder(STUDENT, reservation);
        KafkaTemplate<String, String> broken = mock(KafkaTemplate.class);
        when(broken.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Módulo 1 caído")));
        var props = new ReservationProperties(
                properties.pageSize(), properties.nextSlotStep(), properties.maxQuota(),
                properties.maxSpaceDuration(), properties.minCancellationNotice(), properties.noShowGrace(),
                properties.loanLossThreshold(), properties.holidays(), properties.resourceTypes(),
                properties.module1(), properties.module3(), properties.jwt(), properties.kafka(),
                new ReservationProperties.Outbox(true, Duration.ofHours(1), 50, 1, Duration.ofMinutes(25), false),
                properties.upload());

        new OutboxPublisher(jdbc, broken, transactionManager, props).publishPending();

        assertThat(jdbc.sql("SELECT notified_status FROM status_change").query(String.class).single())
                .isEqualTo("FAILED");
    }
}
