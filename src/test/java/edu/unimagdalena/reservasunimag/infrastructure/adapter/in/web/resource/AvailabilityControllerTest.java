package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.resource;

import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException.ExternalModule;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.Availability;
import edu.unimagdalena.reservasunimag.domain.model.resource.AvailabilityAnswer;
import edu.unimagdalena.reservasunimag.domain.model.resource.DisplayedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.CheckAvailabilityPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

import static edu.unimagdalena.reservasunimag.support.ContractFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /api/resources/{resourceId}/availability} contra los fixtures de UC8 Contratos §1. */
@WebMvcTest(AvailabilityController.class)
class AvailabilityControllerTest {

    private static final String ERRORS = "https://reservasunimag.unimagdalena.edu.co/errors/";
    private static final LocalDate SEPT_1 = LocalDate.of(2026, 9, 1);

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CheckAvailabilityPort availability;

    private static RequestPostProcessor session(Role role) {
        return jwt().jwt(token -> token.subject("00000000-0000-0000-0000-000000000001").claim("code", "2021114001")
                .claim("role", role.name()));
    }

    private static Instant at(int day, int hour, int minute, int second) {
        return ZonedDateTime.of(2026, 9, day, hour, minute, second, 0, TimeSlot.ZONE).toInstant();
    }

    private static TimeSlot slot(LocalDate date, int start, int end) {
        return new TimeSlot(date, LocalTime.of(start, 0), LocalTime.of(end, 0));
    }

    private static Resource space(String id, String name) {
        return Resource.space(id, name, "SALON", "Bloque 3", OperationalStatus.DISPONIBLE, "Ingeniería", 30,
                List.of());
    }

    @Test
    void aFreeResourceMatchesTheContractAndIsNeverCached() throws Exception {
        TimeSlot slot = slot(SEPT_1, 10, 12);
        when(availability.check("ESP-0107", slot)).thenReturn(new AvailabilityAnswer(
                space("ESP-0107", "Sala de Estudio 3"), slot, Availability.of(DisplayedStatus.DISPONIBLE),
                at(1, 9, 14, 22)));

        mockMvc.perform(get("/api/resources/ESP-0107/availability?date=2026-09-01&start=10:00&end=12:00")
                        .with(session(Role.ESTUDIANTE)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json(read("api-availability-free.json"), JsonCompareMode.STRICT));
    }

    @Test
    void aClassMatchesTheContractWithReasonAndUntil() throws Exception {
        TimeSlot slot = slot(SEPT_1, 8, 10);
        when(availability.check("ESP-0412", slot)).thenReturn(new AvailabilityAnswer(space("ESP-0412", "Salón 201"),
                slot, new Availability(DisplayedStatus.BLOQUEO_ACADEMICO, at(1, 10, 0, 0)), at(1, 9, 14, 22)));

        mockMvc.perform(get("/api/resources/ESP-0412/availability?date=2026-09-01&start=08:00&end=10:00")
                        .with(session(Role.MONITOR)))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-availability-academic-block.json"), JsonCompareMode.STRICT));
    }

    @Test
    void anAssetOnLoanMatchesTheContractWithItsDueDate() throws Exception {
        TimeSlot slot = slot(LocalDate.of(2026, 9, 3), 10, 12);
        Resource book = Resource.asset("ACT-004512", "Libro de Cálculo I", "LIBRO", "Biblioteca",
                OperationalStatus.DISPONIBLE, "BUENO", 7);
        when(availability.check("ACT-004512", slot)).thenReturn(new AvailabilityAnswer(book, slot,
                new Availability(DisplayedStatus.EN_USO, at(10, 22, 0, 0)), at(3, 9, 14, 22)));

        mockMvc.perform(get("/api/resources/ACT-004512/availability?date=2026-09-03&start=10:00&end=12:00")
                        .with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-availability-asset-on-loan.json"), JsonCompareMode.STRICT));
    }

    @Test
    void anOverdueLoanOmitsTheUntilAndSaysWhy() throws Exception {
        TimeSlot slot = slot(SEPT_1, 10, 12);
        when(availability.check(eq("ACT-004512"), any())).thenReturn(new AvailabilityAnswer(
                Resource.asset("ACT-004512", "Libro de Cálculo I", "LIBRO", "Biblioteca", OperationalStatus.DISPONIBLE,
                        "BUENO", 7),
                slot, new Availability(DisplayedStatus.EN_USO, null, true), at(1, 9, 14, 22)));

        String body = mockMvc.perform(get("/api/resources/ACT-004512/availability?date=2026-09-01&start=10:00&end=12:00")
                        .with(session(Role.ESTUDIANTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("El recurso está en uso y su plazo de préstamo ya venció."))
                .andExpect(jsonPath("$.occupiedUntil").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("null");
    }

    @Test
    void anInvalidSlotIsA400WithItsCodeAndNothingIsAsked() throws Exception {
        for (String[] bad : new String[][]{
                {"22:00", "23:00", "TIME_SLOT_OUTSIDE_WINDOW"},
                {"21:00", "01:00", "TIME_SLOT_CROSSES_MIDNIGHT"},
                {"12:00", "10:00", "TIME_SLOT_END_NOT_AFTER_START"}}) {
            mockMvc.perform(get("/api/resources/ESP-0107/availability?date=2026-09-01&start=" + bad[0] + "&end="
                            + bad[1]).with(session(Role.ESTUDIANTE)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value(ERRORS + "invalid-time-slot"))
                    .andExpect(jsonPath("$.code").value(bad[2]))
                    .andExpect(jsonPath("$.instance").value("/api/resources/ESP-0107/availability"));
        }
        verifyNoInteractions(availability);
    }

    @Test
    void missingParametersAreA400OfParameters() throws Exception {
        mockMvc.perform(get("/api/resources/ESP-0107/availability?start=10:00&end=12:00")
                        .with(session(Role.ESTUDIANTE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(ERRORS + "invalid-parameters"))
                .andExpect(jsonPath("$.errors[0].field").value("date"));
    }

    @Test
    void withoutSessionItIsA401() throws Exception {
        mockMvc.perform(get("/api/resources/ESP-0107/availability?date=2026-09-01&start=10:00&end=12:00"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(availability);
    }

    @Test
    void anUnknownResourceIsA404AndNotOccupied() throws Exception {
        when(availability.check(eq("ESP-9999"), any())).thenThrow(new ResourceNotFoundException("ESP-9999"));

        mockMvc.perform(get("/api/resources/ESP-9999/availability?date=2026-09-01&start=10:00&end=12:00")
                        .with(session(Role.ESTUDIANTE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(ERRORS + "resource-not-found"))
                .andExpect(jsonPath("$.resourceId").value("ESP-9999"))
                .andExpect(jsonPath("$.available").doesNotExist());
    }

    @Test
    void module1DownIsA503ThatSaysItCannotConfirmItIsFree() throws Exception {
        when(availability.check(eq("ESP-0107"), any()))
                .thenThrow(new ExternalServiceUnavailableException(ExternalModule.MODULO_1, "caído", null));

        mockMvc.perform(get("/api/resources/ESP-0107/availability?date=2026-09-01&start=10:00&end=12:00")
                        .with(session(Role.ESTUDIANTE)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json(read("api-availability-503.json"), JsonCompareMode.STRICT))
                .andExpect(jsonPath("$.available").doesNotExist());
    }

    @Test
    void asksForTheSlotOfTheParameters() throws Exception {
        TimeSlot slot = slot(SEPT_1, 14, 16);
        when(availability.check("ESP-0107", slot)).thenReturn(new AvailabilityAnswer(
                space("ESP-0107", "Sala de Estudio 3"), slot, Availability.of(DisplayedStatus.DISPONIBLE),
                at(1, 9, 14, 22)));

        mockMvc.perform(get("/api/resources/ESP-0107/availability?date=2026-09-01&start=14:00&end=16:00")
                .with(session(Role.ESTUDIANTE))).andExpect(status().isOk());

        verify(availability).check("ESP-0107", slot);
    }
}
