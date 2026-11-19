package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.resource;

import edu.unimagdalena.reservasunimag.domain.model.error.InvalidFilterException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceOccupancy;
import edu.unimagdalena.reservasunimag.domain.model.resource.ScheduledUse;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.ViewResourceOccupancyPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /api/resources/{resourceId}/occupancy}: el calendario de un espacio para Dirección de Programa. */
@WebMvcTest(OccupancyController.class)
class OccupancyControllerTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 6);

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ViewResourceOccupancyPort occupancy;

    private static RequestPostProcessor session(Role role) {
        return jwt().jwt(token -> token.subject("00000000-0000-0000-0000-000000000003").claim("code", "DP-0001")
                .claim("role", role.name()));
    }

    private static Instant at(int day, int hour) {
        return ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, TimeSlot.ZONE).toInstant();
    }

    @Test
    void theWeekOfASpaceShowsClassesByNameAndStudentReservationsWithoutHolder() throws Exception {
        var lab = Resource.space("ESP-0412", "Laboratorio de Redes", "LABORATORIO", "Bloque 7, piso 2",
                OperationalStatus.DISPONIBLE, "Ingeniería", 30, List.of());
        when(occupancy.view("ESP-0412", MONDAY, FRIDAY)).thenReturn(new ResourceOccupancy(lab, MONDAY, FRIDAY, List.of(
                new ScheduledUse(ScheduledUse.Kind.CLASS, at(2, 8), at(2, 10), "Redes de Computadores",
                        "Ingeniería de Sistemas"),
                new ScheduledUse(ScheduledUse.Kind.STUDENT_RESERVATION, at(3, 14), at(3, 16), null, null))));

        mockMvc.perform(get("/api/resources/ESP-0412/occupancy?from=2026-11-02&to=2026-11-06")
                        .with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("""
                        {
                          "resourceId": "ESP-0412",
                          "name": "Laboratorio de Redes",
                          "category": "ESPACIO",
                          "location": "Bloque 7, piso 2",
                          "operationalStatus": "DISPONIBLE",
                          "from": "2026-11-02",
                          "to": "2026-11-06",
                          "uses": [
                            { "kind": "CLASS", "start": "2026-11-02T08:00:00-05:00", "end": "2026-11-02T10:00:00-05:00",
                              "course": "Redes de Computadores", "program": "Ingeniería de Sistemas" },
                            { "kind": "STUDENT_RESERVATION", "start": "2026-11-03T14:00:00-05:00",
                              "end": "2026-11-03T16:00:00-05:00" }
                          ]
                        }""", JsonCompareMode.STRICT));
    }

    @Test
    void onlyTheProgramDirectionSeesIt() throws Exception {
        mockMvc.perform(get("/api/resources/ESP-0412/occupancy?from=2026-11-02&to=2026-11-06")
                        .with(session(Role.ESTUDIANTE)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(occupancy);
    }

    @Test
    void aRangeThatMakesNoSenseIsA400() throws Exception {
        when(occupancy.view(any(), any(), any())).thenThrow(new InvalidFilterException("to", "debe ser igual o "
                + "posterior a from"));

        mockMvc.perform(get("/api/resources/ESP-0412/occupancy?from=2026-11-06&to=2026-11-02")
                        .with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"));
    }
}
