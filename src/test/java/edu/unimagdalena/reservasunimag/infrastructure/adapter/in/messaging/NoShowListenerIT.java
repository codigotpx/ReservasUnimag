package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.messaging;

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
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static edu.unimagdalena.reservasunimag.support.ContractFixtures.read;
import static edu.unimagdalena.reservasunimag.support.Database.clean;
import static edu.unimagdalena.reservasunimag.support.Database.startedReservation;
import static org.assertj.core.api.Assertions.assertThat;

/** El consumidor de UC9 contra un Kafka de verdad: evento nuevo, repetido, roto y rechazado (plan de UC9 T014). */
@SpringBootTest(properties = {"reservations.outbox.enabled=false",
        "reservations.kafka.topic.no-show=module3.reservation.no-show.it"})
@ActiveProfiles("local")
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class NoShowListenerIT {

    private static final String TOPIC = "module3.reservation.no-show.it";
    private static final String DLT = "module2.dlt." + TOPIC;
    private static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private KafkaTemplate<String, String> kafka;
    @Autowired
    private KafkaContainer kafkaContainer;
    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyTables() {
        clean(jdbc);
    }

    private static String reported(UUID eventId, UUID reservationId, OffsetDateTime verifiedAt) {
        return read("event-no-show-reported.json")
                .replace("6a1f3b84-2c57-4e90-81d6-9f4e0a7c3b25", eventId.toString())
                .replace("9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45", reservationId.toString())
                .replace("2026-09-01T10:10:30-05:00", verifiedAt.toString());
    }

    private void publish(UUID key, String json) throws Exception {
        kafka.send(TOPIC, key.toString(), json).get();
    }

    private long count(String sql, UUID reservationId) {
        return jdbc.sql(sql).param("id", reservationId).query(Long.class).single();
    }

    private static void eventually(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("la condición no se cumplió a tiempo");
            }
            Thread.sleep(200);
        }
    }

    private List<ConsumerRecord<String, String>> deadLetters(String key) {
        var config = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (var consumer = new KafkaConsumer<String, String>(config)) {
            consumer.subscribe(List.of(DLT));
            long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
            while (received.isEmpty() && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (key.equals(record.key())) {
                        received.add(record);
                    }
                });
            }
        }
        return received;
    }

    @Test
    void aNewReportIsAppliedOnceEvenIfItArrivesTwice() throws Exception {
        UUID reservation = startedReservation(jdbc, STUDENT, "ESP-0107", 15);
        UUID eventId = UUID.randomUUID();
        String json = reported(eventId, reservation, OffsetDateTime.now(ZoneOffset.UTC));

        publish(reservation, json);
        publish(reservation, json);

        eventually(() -> count("SELECT count(*) FROM absence WHERE reservation_id = :id", reservation) == 1);
        // La segunda copia la descarta la inbox: ni otra ausencia ni otro acuse.
        Thread.sleep(2_000);
        assertThat(count("SELECT count(*) FROM inbox_message WHERE reservation_id = :id", reservation)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM outbox_message WHERE type = 'NO_SHOW_ACK' AND reservation_id = :id",
                reservation)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT status FROM reservation WHERE id = :id").param("id", reservation)
                .query(String.class).single()).isEqualTo("FINALIZADA");
    }

    @Test
    void aTooEarlyReportIsAnsweredAndDoesNotGoToTheDeadLetterTopic() throws Exception {
        UUID reservation = startedReservation(jdbc, STUDENT, "ESP-0107", 3);

        publish(reservation, reported(UUID.randomUUID(), reservation, OffsetDateTime.now(ZoneOffset.UTC)));

        eventually(() -> count("SELECT count(*) FROM outbox_message WHERE reservation_id = :id", reservation) == 1);
        assertThat(jdbc.sql("SELECT payload->'data'->>'rejection' FROM outbox_message WHERE reservation_id = :id")
                .param("id", reservation).query(String.class).single()).isEqualTo("TOO_EARLY");
        assertThat(count("SELECT count(*) FROM absence WHERE reservation_id = :id", reservation)).isZero();
    }

    @Test
    void aBrokenEventGoesToTheDeadLetterTopicWithTheCause() throws Exception {
        UUID key = UUID.randomUUID();

        publish(key, read("event-no-show-invalid.json"));

        var dead = deadLetters(key.toString());
        assertThat(dead).hasSize(1);
        assertThat(dead.get(0).value()).isEqualTo(read("event-no-show-invalid.json"));
        var cause = dead.get(0).headers().lastHeader("kafka_dlt-exception-message");
        assertThat(cause).isNotNull();
        assertThat(new String(cause.value(), StandardCharsets.UTF_8)).contains("reservationId");
    }

    @Test
    void aReportAboutAReservationThatDoesNotExistGoesToTheDeadLetterTopic() throws Exception {
        UUID missing = UUID.randomUUID();

        publish(missing, reported(UUID.randomUUID(), missing, OffsetDateTime.now(ZoneOffset.UTC)));

        assertThat(deadLetters(missing.toString())).hasSize(1);
        assertThat(jdbc.sql("SELECT count(*) FROM inbox_message").query(Long.class).single()).isZero();
    }

    @Test
    void aVoidingAfterTheReportUndoesTheRecord() throws Exception {
        UUID reservation = startedReservation(jdbc, STUDENT, "ESP-0107", 15);
        publish(reservation, reported(UUID.randomUUID(), reservation, OffsetDateTime.now(ZoneOffset.UTC)));
        publish(reservation, read("event-no-show-voided.json")
                .replace("b9d2e5a7-4f81-4c36-92be-7a0c1d8f3e64", UUID.randomUUID().toString())
                .replace("9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45", reservation.toString()));

        eventually(() -> count("SELECT count(*) FROM absence WHERE voided AND reservation_id = :id", reservation) == 1);
        assertThat(count("SELECT count(*) FROM reservation_closure WHERE reservation_id = :id", reservation)).isZero();
    }
}
