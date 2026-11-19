package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.messaging;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.ContractFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cómo se lee el topic de no asistencia contra los fixtures de UC9 Contratos §1, y qué se considera roto. */
class NoShowMessageTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void aReportIsReadWithTheOnSiteCheckAndWithoutTheFreeText() {
        NoShowMessage message = NoShowMessage.parse(mapper, read("event-no-show-reported.json"));

        assertThat(message.type()).isEqualTo(NoShowMessage.Type.NoShowReported);
        assertThat(message.eventId()).isEqualTo(UUID.fromString("6a1f3b84-2c57-4e90-81d6-9f4e0a7c3b25"));
        assertThat(message.reservationId()).isEqualTo(UUID.fromString("9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45"));
        assertThat(message.verifiedAt()).isEqualTo(Instant.parse("2026-09-01T15:10:30Z"));
        assertThat(message.toString()).doesNotContain("Se verificó");
    }

    @Test
    void aVoidingIsReadWithItsReason() {
        NoShowMessage message = NoShowMessage.parse(mapper, read("event-no-show-voided.json"));

        assertThat(message.type()).isEqualTo(NoShowMessage.Type.NoShowReportVoided);
        assertThat(message.voidedReason()).startsWith("El reporte se envió por error");
        assertThat(message.verifiedAt()).isNull();
    }

    @Test
    void whatCannotBeAppliedIsAnInvalidEvent() {
        assertThatThrownBy(() -> NoShowMessage.parse(mapper, read("event-no-show-invalid.json")))
                .isInstanceOf(InvalidEventException.class).hasMessageContaining("reservationId");
        assertThatThrownBy(() -> NoShowMessage.parse(mapper, "esto no es json"))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> NoShowMessage.parse(mapper, read("event-no-show-reported.json")
                .replace("NoShowReported", "AlgoDistinto")))
                .isInstanceOf(InvalidEventException.class).hasMessageContaining("tipo");
        assertThatThrownBy(() -> NoShowMessage.parse(mapper, read("event-no-show-reported.json")
                .replace("2026-09-01T10:10:30-05:00", "2026-09-01T10:10:30")))
                .isInstanceOf(InvalidEventException.class).hasMessageContaining("verifiedAt");
    }
}
