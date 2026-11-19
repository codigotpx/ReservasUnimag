package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging;

import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOut;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutAck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutKind;
import edu.unimagdalena.reservasunimag.domain.model.compliance.Verdict;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging.event.CheckOutAckEvent;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.ContractFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;

/** El acuse de UC12 Contratos §2 en sus tres formas: lo que queda en {@code payload}. */
class CheckOutAckEventTest {

    private static final UUID HOLDER = UUID.fromString("5f1b9c2d-7a34-4e81-b0f6-3c8d1e9a4b72");
    private final JsonMapper mapper = JsonMapper.builder().build();

    private static Instant at(int month, int day, int hour, int minute, int second) {
        return ZonedDateTime.of(2026, month, day, hour, minute, second, 0, TimeSlot.ZONE).toInstant();
    }

    private static Reservation space() {
        return Reservation.confirmed(UUID.fromString("9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45"), HOLDER, "ESP-0107",
                ResourceCategory.ESPACIO, at(9, 1, 10, 0, 0), at(9, 1, 12, 0, 0), at(8, 30, 9, 0, 0), null);
    }

    @Test
    void anAssetAckCarriesTheDueDateAndThatTheLoanClosed() throws Exception {
        Reservation loan = Reservation.confirmed(UUID.fromString("4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3"), HOLDER,
                "ACT-004512", ResourceCategory.ACTIVO, at(9, 1, 14, 30, 0), at(9, 10, 22, 0, 0), at(8, 30, 9, 0, 0),
                new Loan(7, at(9, 1, 14, 35, 0), null));
        UUID source = UUID.fromString("7b3e9c41-5a28-4f60-93d7-1e8a0c2f5b64");
        Instant received = at(9, 9, 16, 42, 14);
        var ack = CheckOutAck.accepted(loan, source, new CheckOut(loan.id(), CheckOutKind.ASSET_RETURN, source,
                at(9, 9, 16, 40, 0), received, null, loan.endsAt()), false, received);

        JSONAssert.assertEquals(read("event-check-out-ack-asset.json"), mapper.writeValueAsString(
                CheckOutAckEvent.from(UUID.fromString("e1c8b350-7d24-4a96-b0f3-5c2e9a1d7b48"), ack)), true);
    }

    @Test
    void aSpaceAckRecordsTheVerdictAndSaysTheSlotWasNotReleased() throws Exception {
        UUID source = UUID.fromString("a5f2d738-9b61-4c05-87ea-3d0c1b9f4e26");
        Instant received = at(9, 1, 12, 15, 42);
        var ack = CheckOutAck.accepted(space(), source, new CheckOut(space().id(), CheckOutKind.SPACE_REVIEW, source,
                at(9, 1, 12, 10, 0), received, Verdict.REQUIERE_MANTENIMIENTO, null), false, received);

        JSONAssert.assertEquals(read("event-check-out-ack-space.json"), mapper.writeValueAsString(
                CheckOutAckEvent.from(UUID.fromString("b4d7e219-6a03-4c58-9f12-0e8b5c3a7d61"), ack)), true);
    }

    @Test
    void aRejectionSaysWhyAndFromWhen() throws Exception {
        var ack = CheckOutAck.slotNotFinished(space(), UUID.fromString("c4b1a826-3e57-4d09-96fa-8d2e0b7c1f35"),
                at(9, 1, 11, 50, 8));

        JSONAssert.assertEquals(read("event-check-out-ack-rejected.json"), mapper.writeValueAsString(
                CheckOutAckEvent.from(UUID.fromString("5c9a2e74-0b18-4f36-8d5e-1a7f3c9b6e20"), ack)), true);
    }

    @Test
    void theInstantKeepsTheSecondsItCameWith() {
        UUID source = UUID.randomUUID();
        Instant atTen = at(9, 1, 22, 0, 1).plusMillis(250);
        var ack = CheckOutAck.accepted(space(), source, new CheckOut(space().id(), CheckOutKind.SPACE_REVIEW, source,
                atTen, atTen, Verdict.SIN_NOVEDAD, null), false, atTen);

        // Edge case de las 22:00: 22:00:00 y 22:00:01 se distinguen, y no se redondea nada.
        assertThat(mapper.writeValueAsString(CheckOutAckEvent.from(UUID.randomUUID(), ack)))
                .contains("\"occurredAt\":\"2026-09-01T22:00:01.25-05:00\"");
    }
}
