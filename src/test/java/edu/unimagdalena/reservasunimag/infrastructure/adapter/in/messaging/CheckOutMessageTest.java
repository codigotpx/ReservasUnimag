package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.messaging;

import edu.unimagdalena.reservasunimag.domain.model.compliance.Verdict;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.ContractFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cómo se lee el topic de check-out contra los fixtures de UC12 Contratos §1. */
class CheckOutMessageTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void theBusinessInstantIsTheOneInDataNotTheEnvelope() {
        CheckOutMessage message = CheckOutMessage.parse(mapper, read("event-check-out-asset.json"));

        assertThat(message.reservationId()).isEqualTo(UUID.fromString("4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3"));
        assertThat(message.occurredAt()).isEqualTo(Instant.parse("2026-09-09T21:40:00Z"));
        assertThat(message.verdict()).isNull();
        assertThat(message.carriesDamage()).isFalse();
    }

    @Test
    void aSpaceReviewBringsItsVerdict() {
        assertThat(CheckOutMessage.parse(mapper, read("event-check-out-space.json")).verdict())
                .isEqualTo(Verdict.REQUIERE_MANTENIMIENTO);
    }

    @Test
    void aDamageDescriptionIsOnlyNotedNeverRead() {
        CheckOutMessage message = CheckOutMessage.parse(mapper, read("event-check-out-asset-with-verdict.json"));

        assertThat(message.carriesDamage()).isTrue();
        assertThat(message.toString()).doesNotContain("rayada");
    }

    @Test
    void anUnknownVerdictOrAMissingInstantIsAnInvalidEvent() {
        assertThatThrownBy(() -> CheckOutMessage.parse(mapper, read("event-check-out-space.json")
                .replace("REQUIERE_MANTENIMIENTO", "ROTO")))
                .isInstanceOf(InvalidEventException.class).hasMessageContaining("dictamen");
        assertThatThrownBy(() -> CheckOutMessage.parse(mapper, read("event-check-out-asset.json")
                .replace("\"occurredAt\": \"2026-09-09T16:40:00-05:00\"", "\"otro\": 1")))
                .isInstanceOf(InvalidEventException.class).hasMessageContaining("occurredAt");
        assertThatThrownBy(() -> CheckOutMessage.parse(mapper, read("event-check-out-asset.json")
                .replace("CheckOutRegistered", "NoShowReported")))
                .isInstanceOf(InvalidEventException.class);
    }
}
