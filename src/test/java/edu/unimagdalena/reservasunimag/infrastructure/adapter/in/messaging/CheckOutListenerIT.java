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
import static edu.unimagdalena.reservasunimag.support.Database.loan;
import static org.assertj.core.api.Assertions.assertThat;

/** El consumidor de UC12 contra un Kafka de verdad: evento nuevo, repetido, roto y rechazado (plan de UC12 T013). */
@SpringBootTest(properties = {"reservations.outbox.enabled=false",
        "reservations.kafka.topic.check-out=module3.reservation.check-out.it"})
@ActiveProfiles("local")
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class CheckOutListenerIT {

    private static final String TOPIC = "module3.reservation.check-out.it";
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

    private static String event(String fixture, UUID eventId, UUID reservationId) {
        String original = fixture.contains("asset-with-verdict") ? "2e6c0a95-4d17-4b83-a5f9-7c1e3b8d0f42"
                : "7b3e9c41-5a28-4f60-93d7-1e8a0c2f5b64";
        return read(fixture)
                .replace(original, eventId.toString())
                .replace("4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3", reservationId.toString())
                .replace("2026-09-09T16:40:00-05:00", OffsetDateTime.now(ZoneOffset.UTC).toString());
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
    void aReturnClosesTheLoanOnceEvenIfItArrivesTwice() throws Exception {
        UUID loanId = loan(jdbc, STUDENT, "ACT-000913", 1, 72, true);
        String json = event("event-check-out-asset.json", UUID.randomUUID(), loanId);

        publish(loanId, json);
        publish(loanId, json);

        eventually(() -> count("SELECT count(*) FROM check_out WHERE reservation_id = :id", loanId) == 1);
        Thread.sleep(2_000);
        assertThat(count("SELECT count(*) FROM inbox_message WHERE reservation_id = :id", loanId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM outbox_message WHERE type = 'CHECK_OUT_ACK' AND reservation_id = :id",
                loanId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM loan WHERE returned_at IS NOT NULL AND reservation_id = :id", loanId))
                .isEqualTo(1);
    }

    @Test
    void aVerdictOnAnAssetIsAnsweredAsARejectionNotSentToTheDeadLetterTopic() throws Exception {
        UUID loanId = loan(jdbc, STUDENT, "ACT-000913", 1, 72, true);

        publish(loanId, event("event-check-out-asset-with-verdict.json", UUID.randomUUID(), loanId));

        eventually(() -> count("SELECT count(*) FROM outbox_message WHERE reservation_id = :id", loanId) == 1);
        assertThat(jdbc.sql("SELECT payload->'data'->>'rejection' FROM outbox_message WHERE reservation_id = :id")
                .param("id", loanId).query(String.class).single()).isEqualTo("VERDICT_REQUIRED");
        assertThat(count("SELECT count(*) FROM check_out WHERE reservation_id = :id", loanId)).isZero();
        // La descripción del daño no queda en ninguna parte nuestra salvo la copia cruda de la inbox.
        assertThat(jdbc.sql("SELECT payload::text FROM outbox_message WHERE reservation_id = :id").param("id", loanId)
                .query(String.class).single()).doesNotContain("rayada");
    }

    @Test
    void aCheckOutWithoutReservationGoesToTheDeadLetterTopic() throws Exception {
        UUID key = UUID.randomUUID();
        String withoutReservation = read("event-check-out-asset.json")
                .replace("\"reservationId\": \"4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3\",", "");

        publish(key, withoutReservation);

        var dead = deadLetters(key.toString());
        assertThat(dead).hasSize(1);
        assertThat(dead.get(0).headers().lastHeader("kafka_dlt-exception-message")).isNotNull();
    }

    @Test
    void aCheckOutAboutAReservationThatDoesNotExistGoesToTheDeadLetterTopic() throws Exception {
        UUID missing = UUID.randomUUID();

        publish(missing, event("event-check-out-asset.json", UUID.randomUUID(), missing));

        assertThat(deadLetters(missing.toString())).hasSize(1);
    }
}
