package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.reservation.UseStart;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging.event.ResourceUseStartedEvent;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.ContractFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;

/** El único evento que sale hacia el Módulo 1 (UC7 Contratos §4): lo que queda en {@code payload}. */
class ResourceUseStartedEventTest {

    private static final UUID HOLDER = UUID.fromString("5f1b9c2d-7a34-4e81-b0f6-3c8d1e9a4b72");
    private final JsonMapper mapper = JsonMapper.builder().build();

    private static Instant at(int month, int day, int hour, int minute, int second) {
        return ZonedDateTime.of(2026, month, day, hour, minute, second, 0, TimeSlot.ZONE).toInstant();
    }

    @Test
    void aSpaceCarriesTheEndOfItsUseSoModule1ClosesItAlone() throws Exception {
        Reservation space = Reservation.confirmed(UUID.fromString("9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45"), HOLDER,
                "ESP-0107", ResourceCategory.ESPACIO, at(9, 1, 10, 0, 0), at(9, 1, 12, 0, 0), at(8, 30, 9, 0, 0), null);

        String payload = mapper.writeValueAsString(ResourceUseStartedEvent.from(
                UUID.fromString("c9b2f536-8a41-4d07-95ec-3f1a0d7b2e58"),
                new UseStart(space.startUse(at(9, 1, 10, 3, 51)), at(9, 1, 10, 3, 51))));

        JSONAssert.assertEquals(read("event-resource-use-started.json"), payload, true);
        // Ni quién lo usa ni ningún otro estado: solo EN_USO (FR-012).
        assertThat(payload).doesNotContain(HOLDER.toString()).doesNotContain("RESERVADO");
    }

    @Test
    void anAssetHasNoEndBecauseItsUseEndsWithTheReturn() throws Exception {
        Reservation loan = Reservation.confirmed(UUID.fromString("4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3"), HOLDER,
                "ACT-004512", ResourceCategory.ACTIVO, at(9, 1, 14, 30, 0), at(9, 10, 22, 0, 0), at(8, 30, 9, 0, 0),
                Loan.reserved(7));

        JSONAssert.assertEquals(read("event-resource-use-started-asset.json"), mapper.writeValueAsString(
                ResourceUseStartedEvent.from(UUID.fromString("1d7f4a62-0c95-4b38-8e21-5a3c9f0b7d46"),
                        new UseStart(loan.startUse(at(9, 1, 14, 32, 10)), at(9, 1, 14, 32, 10)))), true);
    }
}
