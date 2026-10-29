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
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.Database.clean;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC10 SC-002: con el reloj del publicador en una hora, la ficha igual sale en menos de 5 segundos, porque el
 * publicador se despierta al confirmar la transacción de la reserva.
 */
@SpringBootTest(properties = {"reservations.outbox.enabled=true", "reservations.outbox.interval=1h",
        "reservations.outbox.publish-on-commit=true", "reservations.academic.use-start-enabled=false",
        "reservations.kafka.topic.record=module2.reservation.record.on-commit-it"})
@ActiveProfiles("local")
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class PublishOnCommitIT {

    private static final CurrentUser STUDENT = new CurrentUser(UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "2021114001", Role.ESTUDIANTE);

    @Autowired
    private ReserveResourcesPort reserveResources;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private ReservationProperties properties;
    @Autowired
    private KafkaContainer kafkaContainer;

    @BeforeEach
    void emptyTables() {
        clean(jdbc);
    }

    @Test
    void theRecordLeavesInLessThanFiveSecondsWithoutWaitingForTheClock() throws InterruptedException {
        var config = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (var consumer = new KafkaConsumer<String, String>(config)) {
            consumer.subscribe(List.of(properties.kafka().topic().record()));
            consumer.poll(Duration.ofMillis(200));

            long committed = System.nanoTime();
            Reservation reservation = reserveResources.reserve(ReservationRequest.forSpace(STUDENT, "ESP-0107",
                    LocalDate.now(TimeSlot.ZONE).plusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0)))
                    .reservation();

            boolean received = false;
            while (!received && System.nanoTime() - committed < Duration.ofSeconds(5).toNanos()) {
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                    received |= reservation.id().toString().equals(record.key());
                }
            }
            assertThat(received).as("la ficha llegó al topic en menos de 5 s").isTrue();
        }

        // El historial se actualiza en el mismo turno, justo después del envío.
        String status = null;
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!"SENT".equals(status) && System.nanoTime() < deadline) {
            status = jdbc.sql("SELECT delivery_status FROM reservation_record").query(String.class).single();
            Thread.sleep(100);
        }
        assertThat(status).isEqualTo("SENT");
    }
}
