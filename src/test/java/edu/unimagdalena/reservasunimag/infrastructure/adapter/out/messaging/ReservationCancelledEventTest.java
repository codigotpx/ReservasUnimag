package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Cancellation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging.event.ReservationCancelledEvent;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.ContractFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;

/** El JSON de {@code ReservationCancelled} en sus tres formas (UC11 Contratos §1): lo que queda en {@code payload}. */
class ReservationCancelledEventTest {

    private static final ReservationHolder HOLDER = new ReservationHolder(
            UUID.fromString("5f1b9c2d-7a34-4e81-b0f6-3c8d1e9a4b72"), "2019114045", "Nombre del estudiante",
            Role.ESTUDIANTE);
    private final JsonMapper mapper = JsonMapper.builder().build();

    private static Instant at(int year, int month, int day, int hour, int minute, int second) {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, TimeSlot.ZONE).toInstant();
    }

    private String json(String eventId, String reservationId, String resourceId, String resourceName,
                        Instant start, Instant end, Cancellation cancellation) {
        Reservation reservation = Reservation.confirmed(UUID.fromString(reservationId), HOLDER.userId(), resourceId,
                ResourceCategory.ESPACIO, start, end, start.minusSeconds(86_400), null)
                .cancel(cancellation.origin());
        return mapper.writeValueAsString(ReservationCancelledEvent.from(CancellationReport.pending(
                UUID.fromString(eventId), reservation, cancellation, resourceName, HOLDER)));
    }

    @Test
    void theHolderFormCarriesTheNoticeAndIsAttributable() throws Exception {
        Instant cancelledAt = at(2026, 8, 31, 9, 14, 22);
        String payload = json("d4c9e2f1-7a83-4b60-91c5-2e8d0f3a1b57", "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
                "ESP-0107", "Sala de Estudio 3", at(2026, 9, 1, 10, 0, 0), at(2026, 9, 1, 12, 0, 0),
                new Cancellation(CancellationOrigin.HOLDER, CancellationReport.HOLDER_REASON, 1486, cancelledAt));

        JSONAssert.assertEquals(read("event-reservation-cancelled-holder.json"), payload, true);
    }

    @Test
    void theAcademicFormHasNoNoticeAtAllNotEvenAZero() throws Exception {
        String payload = json("f2b8c4d1-6e39-4a70-95c8-3d1e7a0f2b56", "a1b2c3d4-5e6f-4789-9abc-def012345678",
                "ESP-0301", "Auditorio Menor", at(2026, 9, 10, 14, 0, 0), at(2026, 9, 10, 16, 0, 0),
                new Cancellation(CancellationOrigin.ACADEMIC_PRIORITY,
                        "La franja se asignó a actividad docente: IS-510 grupo 1.", null,
                        at(2026, 10, 2, 15, 47, 35)));

        JSONAssert.assertEquals(read("event-reservation-cancelled-academic.json"), payload, true);
        assertThat(payload).doesNotContain("noticeMinutes").doesNotContain("null");
    }

    @Test
    void theMaintenanceFormHasNoNoticeAndIsNotAttributable() throws Exception {
        String payload = json("8e5d2b19-3c7a-4f60-a1d4-9b0e6c2f7a35", "c7e1a930-8b24-4d16-95af-0e3d7c1b2f84",
                "ESP-0220", "Laboratorio de Química", at(2026, 10, 5, 8, 0, 0), at(2026, 10, 5, 10, 0, 0),
                new Cancellation(CancellationOrigin.RESOURCE_UNAVAILABLE,
                        "El salón quedó fuera de servicio por una gotera.", null, at(2026, 10, 2, 16, 20, 8)));

        JSONAssert.assertEquals(read("event-reservation-cancelled-maintenance.json"), payload, true);
    }

    @Test
    void withoutModule1TheResourceGoesWithoutNameAndStillWithoutNulls() {
        String payload = json("8e5d2b19-3c7a-4f60-a1d4-9b0e6c2f7a35", "c7e1a930-8b24-4d16-95af-0e3d7c1b2f84",
                "ESP-0220", null, at(2026, 10, 5, 8, 0, 0), at(2026, 10, 5, 10, 0, 0),
                new Cancellation(CancellationOrigin.RESOURCE_UNAVAILABLE, "Gotera.", null,
                        at(2026, 10, 2, 16, 20, 8)));

        assertThat(payload).contains("\"resource\":{\"id\":\"ESP-0220\",\"category\":\"ESPACIO\"}")
                .doesNotContain("null");
    }
}
