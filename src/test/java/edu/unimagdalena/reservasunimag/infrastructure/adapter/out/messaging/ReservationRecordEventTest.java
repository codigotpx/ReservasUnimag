package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecord;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging.event.ReservationRecordEvent;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.ContractFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;

/** El JSON del evento hacia el Módulo 3 contra los fixtures de UC2 Contratos §6: lo que queda en {@code payload}. */
class ReservationRecordEventTest {

    private static final UUID EVENT_ID = UUID.fromString("c3a7f1d2-4e58-4b09-9a61-7d2e8f0b5c34");
    private static final ReservationHolder HOLDER = new ReservationHolder(
            UUID.fromString("5f1b9c2d-7a34-4e81-b0f6-3c8d1e9a4b72"), "2019114045", "Camilo Cerpa", Role.ESTUDIANTE);
    private static final Instant OCCURRED = at(2026, 8, 30, 9, 14, 22);
    private final JsonMapper mapper = JsonMapper.builder().build();

    private static Instant at(int year, int month, int day, int hour, int minute, int second) {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, TimeSlot.ZONE).toInstant();
    }

    private String json(ReservationRecord record) {
        return mapper.writeValueAsString(ReservationRecordEvent.from(EVENT_ID, record));
    }

    @Test
    void anAssetLoanEventMatchesTheContractWithItsLoan() throws Exception {
        var reservation = Reservation.confirmed(UUID.fromString("4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3"),
                HOLDER.userId(), "ACT-004512", ResourceCategory.ACTIVO, at(2026, 9, 1, 14, 30, 0),
                at(2026, 9, 10, 22, 0, 0), OCCURRED, Loan.reserved(7));
        var book = Resource.asset("ACT-004512", "Libro de Cálculo I", "LIBRO", "Biblioteca, estantería C-4",
                OperationalStatus.DISPONIBLE, "BUENO", 7);

        JSONAssert.assertEquals(read("event-reservation-record-created-student.json"),
                json(new ReservationRecord(reservation, book, HOLDER, OCCURRED)), true);
    }

    @Test
    void aSpaceEventHasNoLoan() throws Exception {
        var reservation = Reservation.confirmed(UUID.fromString("9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45"),
                HOLDER.userId(), "ESP-0107", ResourceCategory.ESPACIO, at(2026, 9, 1, 10, 0, 0),
                at(2026, 9, 1, 12, 0, 0), OCCURRED, null);
        var room = Resource.space("ESP-0107", "Sala de Estudio 3", "SALA_DE_ESTUDIO", "Biblioteca, piso 1",
                OperationalStatus.DISPONIBLE, "Biblioteca Central", 8, List.of());

        String payload = json(new ReservationRecord(reservation, room, HOLDER, OCCURRED));

        JSONAssert.assertEquals(read("event-reservation-record-created-student-space.json"), payload, true);
        assertThat(payload).doesNotContain("\"loan\"").doesNotContain("null");
    }
}
