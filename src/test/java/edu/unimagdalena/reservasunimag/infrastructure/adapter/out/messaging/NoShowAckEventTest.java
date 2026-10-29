package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging;

import edu.unimagdalena.reservasunimag.domain.model.compliance.Absence;
import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowAck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowRejection;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging.event.NoShowAckEvent;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.ContractFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;

/** El acuse de UC9 Contratos §2 en sus tres formas: lo que queda en {@code payload}. */
class NoShowAckEventTest {

    private static final UUID HOLDER = UUID.fromString("5f1b9c2d-7a34-4e81-b0f6-3c8d1e9a4b72");
    private final JsonMapper mapper = JsonMapper.builder().build();

    private static Instant at(int month, int day, int hour, int minute, int second) {
        return ZonedDateTime.of(2026, month, day, hour, minute, second, 0, TimeSlot.ZONE).toInstant();
    }

    private static Reservation space(String id, Instant start, Instant end) {
        return Reservation.confirmed(UUID.fromString(id), HOLDER, "ESP-0107", ResourceCategory.ESPACIO, start, end,
                start.minusSeconds(86_400), null);
    }

    @Test
    void anAcceptedReportCarriesTheAbsenceTheHolderAndTheReservedTime() throws Exception {
        Instant registered = at(9, 1, 10, 11, 5);
        Reservation reservation = space("9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45", at(9, 1, 10, 0, 0),
                at(9, 1, 12, 0, 0));
        UUID source = UUID.fromString("6a1f3b84-2c57-4e90-81d6-9f4e0a7c3b25");
        var ack = NoShowAck.accepted(reservation, source,
                Absence.registered(reservation.id(), source, at(9, 1, 10, 10, 30), registered, true), "2019114045",
                false, registered);

        String payload = mapper.writeValueAsString(NoShowAckEvent.from(
                UUID.fromString("e7c4a018-5b93-4d27-86fa-1c2e9d0b4f73"), ack));

        JSONAssert.assertEquals(read("event-no-show-ack-accepted.json"), payload, true);
        assertThat(payload).doesNotContain("null").doesNotContain("duplicate");
    }

    @Test
    void aTooEarlyReportSaysFromWhenItIsAdmitted() throws Exception {
        Reservation reservation = space("9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45", at(9, 1, 10, 0, 0),
                at(9, 1, 12, 0, 0));
        var ack = NoShowAck.tooEarly(reservation, UUID.fromString("c2a7e591-8d36-4b04-97fe-0a3d1c8b5f27"),
                at(9, 1, 10, 10, 0), at(9, 1, 10, 4, 12));

        JSONAssert.assertEquals(read("event-no-show-ack-too-early.json"), mapper.writeValueAsString(
                NoShowAckEvent.from(UUID.fromString("3f8b6d20-9a14-4e75-b0c8-5d1e7f2a9c46"), ack)), true);
    }

    @Test
    void aCancelledReservationSaysInWhichStatusItWas() throws Exception {
        Reservation cancelled = space("a1b2c3d4-5e6f-4789-9abc-def012345678", at(9, 10, 14, 0, 0),
                at(9, 10, 16, 0, 0)).cancel(CancellationOrigin.ACADEMIC_PRIORITY);
        var ack = NoShowAck.rejected(cancelled, UUID.fromString("d5f1b382-6c09-4a47-83de-2b7a9e0c1d58"),
                NoShowRejection.RESERVATION_CANCELLED, at(9, 10, 14, 12, 40));

        JSONAssert.assertEquals(read("event-no-show-ack-cancelled.json"), mapper.writeValueAsString(
                NoShowAckEvent.from(UUID.fromString("7d3a9c5e-1f28-4b60-a4e7-8c2d0b6f9e13"), ack)), true);
    }

    @Test
    void aDuplicateSaysSo() {
        Instant registered = at(9, 1, 10, 11, 5);
        Reservation reservation = space("9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45", at(9, 1, 10, 0, 0),
                at(9, 1, 12, 0, 0));
        var ack = NoShowAck.accepted(reservation, UUID.randomUUID(),
                Absence.registered(reservation.id(), UUID.randomUUID(), registered, registered, true), "2019114045",
                true, registered.plusSeconds(600));

        assertThat(mapper.writeValueAsString(NoShowAckEvent.from(UUID.randomUUID(), ack)))
                .contains("\"duplicate\":true")
                .contains("\"absenceRegisteredAt\":\"2026-09-01T10:11:05-05:00\"");
    }
}
