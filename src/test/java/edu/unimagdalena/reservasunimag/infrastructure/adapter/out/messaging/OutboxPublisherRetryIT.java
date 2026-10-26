package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging;

import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;
import edu.unimagdalena.reservasunimag.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static edu.unimagdalena.reservasunimag.support.Database.clean;
import static edu.unimagdalena.reservasunimag.support.Database.reservationWithPendingEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Qué hace el publicador cuando el broker falla: reintenta con espera creciente y al final queda FAILED. */
@SpringBootTest
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class OutboxPublisherRetryIT {

    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ReservationProperties properties;

    @BeforeEach
    void emptyTables() {
        clean(jdbc);
    }

    @SuppressWarnings("unchecked")
    private OutboxPublisher publisherWithBrokerDown(int maxAttempts) {
        KafkaTemplate<String, String> broken = mock(KafkaTemplate.class);
        when(broken.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker caído")));
        var props = new ReservationProperties(properties.pageSize(), properties.nextSlotStep(), properties.maxQuota(),
                properties.maxSpaceDuration(), properties.minCancellationNotice(), properties.holidays(), properties.resourceTypes(),
                properties.module1(), properties.module3(), properties.jwt(), properties.kafka(),
                new ReservationProperties.Outbox(true, Duration.ofHours(1), 50, maxAttempts, Duration.ofMinutes(25)));
        return new OutboxPublisher(jdbc, broken, transactionManager, props);
    }

    private void makeDue() {
        jdbc.sql("UPDATE outbox_message SET next_attempt_at = now() - interval '1 second' WHERE status = 'PENDING'").update();
    }

    @Test
    void aFailureKeepsTheEventPendingCountsTheAttemptAndWaitsBeforeTryingAgain() {
        reservationWithPendingEvent(jdbc, 1);
        var publisher = publisherWithBrokerDown(10);

        assertThat(publisher.publishPending()).isZero();

        var row = jdbc.sql("SELECT status, attempts, last_error, next_attempt_at > now() + interval '3 seconds' AS later FROM outbox_message")
                .query().singleRow();
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("attempts")).isEqualTo(1);
        assertThat((String) row.get("last_error")).contains("broker caído");
        assertThat(row.get("later")).isEqualTo(true);
        // Todavía no toca: otro turno inmediato no lo reintenta.
        publisher.publishPending();
        assertThat(jdbc.sql("SELECT attempts FROM outbox_message").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void theWaitGrowsWithEachFailure() {
        reservationWithPendingEvent(jdbc, 1);
        var publisher = publisherWithBrokerDown(10);

        publisher.publishPending();
        long first = secondsUntilNextAttempt();
        makeDue();
        publisher.publishPending();
        long second = secondsUntilNextAttempt();

        assertThat(second).isGreaterThan(first);
    }

    private long secondsUntilNextAttempt() {
        return jdbc.sql("SELECT extract(epoch FROM next_attempt_at - now())::bigint FROM outbox_message")
                .query(Long.class).single();
    }

    @Test
    void afterMaxAttemptsTheEventIsFailedAndNeverRetriedButStaysInTheTable() {
        reservationWithPendingEvent(jdbc, 1);
        var publisher = publisherWithBrokerDown(3);

        for (int i = 0; i < 3; i++) {
            makeDue();
            publisher.publishPending();
        }

        var row = jdbc.sql("SELECT status, attempts FROM outbox_message").query().singleRow();
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("attempts")).isEqualTo(3);
        makeDue();
        publisher.publishPending();
        assertThat(jdbc.sql("SELECT attempts FROM outbox_message").query(Integer.class).single()).isEqualTo(3);
    }
}
