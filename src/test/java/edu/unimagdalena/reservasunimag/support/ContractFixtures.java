package edu.unimagdalena.reservasunimag.support;

import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/** Lee los JSON de {@code src/test/resources/contracts}: la única fuente del contrato para las pruebas. */
public final class ContractFixtures {

    private ContractFixtures() {
    }

    public static String read(String name) {
        try (var stream = ContractFixtures.class.getResourceAsStream("/contracts/" + name)) {
            if (stream == null) {
                throw new IllegalArgumentException("No existe el fixture " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ReservationProperties properties(URI module1, URI module3, Duration timeout, int module1PageSize,
                                                   int module1MaxPages) {
        return new ReservationProperties(20, Duration.ofMinutes(30), 3, Duration.ofHours(2), Duration.ofMinutes(10),
                List.of(), List.of(),
                new ReservationProperties.Module1(module1, timeout, module1PageSize, module1MaxPages),
                new ReservationProperties.Module3(module3, timeout),
                new ReservationProperties.Jwt("0123456789abcdef0123456789abcdef", "test", Duration.ofHours(8),
                        "session", false),
                new ReservationProperties.Kafka(new ReservationProperties.Kafka.Topic("module2.reservation.record.v1",
                        "module2.reservation.cancellation.v1")),
                new ReservationProperties.Outbox(false, Duration.ofSeconds(5), 50, 10, Duration.ofMinutes(25)));
    }
}
