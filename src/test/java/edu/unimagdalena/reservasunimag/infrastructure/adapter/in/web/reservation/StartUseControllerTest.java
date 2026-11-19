package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import edu.unimagdalena.reservasunimag.domain.model.error.ReservationDeniedException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.error.UseStartRejectedException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.reservation.UseStartResult;
import edu.unimagdalena.reservasunimag.domain.model.resource.DisplayedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.NotifiedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChange;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.RegisterUseStartedPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.ContractFixtures.read;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code POST /api/reservations/{id}/start-use} contra los fixtures de UC7 Contratos §3. */
@WebMvcTest(StartUseController.class)
class StartUseControllerTest {

    private static final UUID RESERVATION = UUID.fromString("9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45");
    private static final UUID HOLDER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID EVENT = UUID.fromString("c9b2f536-8a41-4d07-95ec-3f1a0d7b2e58");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private RegisterUseStartedPort registerUseStarted;

    private static Instant at(int hour, int minute, int second) {
        return ZonedDateTime.of(2026, 9, 1, hour, minute, second, 0, TimeSlot.ZONE).toInstant();
    }

    private static RequestPostProcessor session() {
        return jwt().jwt(token -> token.subject(HOLDER.toString()).claim("code", "2021114001")
                .claim("role", Role.ESTUDIANTE.name()));
    }

    private static UseStartResult started(boolean again) {
        Reservation reservation = Reservation.confirmed(RESERVATION, HOLDER, "ESP-0107", ResourceCategory.ESPACIO,
                at(10, 0, 0), at(12, 0, 0), at(8, 0, 0), null).startUse(at(10, 3, 51));
        return new UseStartResult(reservation, new StatusChange(UUID.randomUUID(), "ESP-0107",
                ResourceCategory.ESPACIO, RESERVATION, at(10, 0, 0), at(12, 0, 0), DisplayedStatus.RESERVADO,
                DisplayedStatus.EN_USO, StatusChangeReason.USE_STARTED, at(10, 3, 51), NotifiedStatus.PENDING, EVENT),
                again);
    }

    @Test
    void theArrivalIsA200WithTheNoticeThatWasQueued() throws Exception {
        when(registerUseStarted.startByHolder(any(), eq(RESERVATION))).thenReturn(started(false));

        mockMvc.perform(post("/api/reservations/" + RESERVATION + "/start-use").with(session()))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-start-use.json"), JsonCompareMode.STRICT));
    }

    @Test
    void registeringAgainAnswersTheSame() throws Exception {
        when(registerUseStarted.startByHolder(any(), eq(RESERVATION))).thenReturn(started(true));

        mockMvc.perform(post("/api/reservations/" + RESERVATION + "/start-use").with(session()))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-start-use.json"), JsonCompareMode.STRICT));
    }

    @Test
    void tooEarlyIsA409Uso002WithWhenTheWindowOpens() throws Exception {
        when(registerUseStarted.startByHolder(any(), eq(RESERVATION))).thenThrow(
                UseStartRejectedException.tooEarly(at(10, 0, 0), Duration.ofMinutes(10)));

        mockMvc.perform(post("/api/reservations/" + RESERVATION + "/start-use").with(session()))
                .andExpect(status().isConflict())
                .andExpect(content().json(read("api-start-use-409-window.json"), JsonCompareMode.STRICT));
    }

    @Test
    void aReservationThatDoesNotAdmitItIsUso001() throws Exception {
        when(registerUseStarted.startByHolder(any(), eq(RESERVATION))).thenThrow(
                UseStartRejectedException.notStartable("CANCELADA"));

        mockMvc.perform(post("/api/reservations/" + RESERVATION + "/start-use").with(session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USO-001"))
                .andExpect(jsonPath("$.reservationStatus").value("CANCELADA"));
    }

    @Test
    void aResourceUnderMaintenanceIsRes004LikeWhenReserving() throws Exception {
        when(registerUseStarted.startByHolder(any(), eq(RESERVATION))).thenThrow(
                ReservationDeniedException.underMaintenance("ESP-0107"));

        mockMvc.perform(post("/api/reservations/" + RESERVATION + "/start-use").with(session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RES-004"))
                .andExpect(jsonPath("$.detectedStatus").value("EN_MANTENIMIENTO"));
    }

    @Test
    void someoneElsesReservationIsA404() throws Exception {
        when(registerUseStarted.startByHolder(any(), eq(RESERVATION))).thenThrow(
                new ReservationNotFoundException(RESERVATION));

        mockMvc.perform(post("/api/reservations/" + RESERVATION + "/start-use").with(session()))
                .andExpect(status().isNotFound());
    }
}
