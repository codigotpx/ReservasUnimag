package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.unimagdalena.reservasunimag.TestcontainersConfiguration;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.Database.clean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC8 SC-003 y SC-004 de punta a punta: con los cinco estados en juego, la lista de UC1 y la pregunta por un recurso
 * responden lo mismo recurso por recurso, y lo que se reportó ocupado no se puede reservar en UC2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class AvailabilityConsistencyIT {

    private static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate DAY = LocalDate.now(TimeSlot.ZONE).plusDays(1);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void occupyTheInventory() {
        clean(jdbc);
        reservation("ESP-0412", "ESPACIO", "ACADEMICO", at(DAY, 10), at(DAY, 12));          // clase
        reservation("ESP-0305", "ESPACIO", "ACADEMICO", at(DAY, 8), at(DAY, 10));           // clase que se toca
        reservation("ESP-0107", "ESPACIO", "ESTUDIANTIL", at(DAY, 10).plusMinutes(30), at(DAY, 11)); // otra persona
        UUID loan = reservation("ACT-001200", "ACTIVO", "ESTUDIANTIL", at(DAY.minusDays(2), 9),
                at(DAY.plusDays(3), 22));
        jdbc.sql("INSERT INTO loan (reservation_id, term_business_days, picked_up_at) VALUES (:id, 3, :at)")
                .param("id", loan)
                .param("at", at(DAY.minusDays(2), 9))
                .update();
    }

    private static OffsetDateTime at(LocalDate date, int hour) {
        return date.atTime(hour, 0).atZone(TimeSlot.ZONE).toOffsetDateTime();
    }

    private UUID reservation(String resourceId, String category, String origin, OffsetDateTime start,
                             OffsetDateTime end) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO reservation (id, user_id, resource_id, resource_category, origin, status, starts_at, ends_at)
                        VALUES (:id, :user, :resource, :category, :origin, 'CONFIRMADA', :start, :end)""")
                .param("id", id)
                .param("user", origin.equals("ACADEMICO") ? null : STUDENT, java.sql.Types.OTHER)
                .param("resource", resourceId)
                .param("category", category)
                .param("origin", origin)
                .param("start", start)
                .param("end", end)
                .update();
        return id;
    }

    private Cookie login() throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"direccion@unimagdalena.edu.co\", \"password\": \"unimag2026\"}"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("session");
    }

    private JsonNode body(String url, Cookie session) throws Exception {
        return JSON.readTree(mockMvc.perform(get(url).cookie(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    @Test
    void theListAndTheSingleQuestionAgreeResourceByResource() throws Exception {
        Cookie session = login();
        JsonNode list = body("/api/resources?date=%s&start=10:00&end=12:00".formatted(DAY), session);

        assertThat(list.get("resources")).hasSize(10);
        for (JsonNode item : list.get("resources")) {
            String id = item.get("id").asText();
            JsonNode single = body("/api/resources/%s/availability?date=%s&start=10:00&end=12:00".formatted(id, DAY),
                    session);
            assertThat(single.get("status").asText()).as(id).isEqualTo(item.get("status").asText());
            assertThat(single.get("available").asBoolean()).as(id).isEqualTo(item.get("selectable").asBoolean());
            assertThat(text(single, "reason")).as(id).isEqualTo(text(item, "reason"));
            assertThat(text(single, "occupiedUntil")).as(id).isEqualTo(text(item, "occupiedUntil"));
        }
    }

    @Test
    void eachOfTheFiveStatusesComesWithItsReasonAndItsUntil() throws Exception {
        Cookie session = login();
        String slot = "?date=%s&start=10:00&end=12:00".formatted(DAY);

        mockMvc.perform(get("/api/resources/ESP-0412/availability" + slot).cookie(session))
                .andExpect(jsonPath("$.status").value("BLOQUEO_ACADEMICO"))
                .andExpect(jsonPath("$.occupiedUntil").value(IsoTimes.offset(at(DAY, 12).toInstant())));
        mockMvc.perform(get("/api/resources/ESP-0107/availability" + slot).cookie(session))
                .andExpect(jsonPath("$.status").value("RESERVADO"))
                .andExpect(jsonPath("$.reason").value("El recurso ya está reservado en la franja consultada."));
        mockMvc.perform(get("/api/resources/ACT-001200/availability" + slot).cookie(session))
                .andExpect(jsonPath("$.status").value("EN_USO"))
                .andExpect(jsonPath("$.occupiedUntil").value(IsoTimes.offset(at(DAY.plusDays(3), 22).toInstant())));
        mockMvc.perform(get("/api/resources/ACT-000912/availability" + slot).cookie(session))
                .andExpect(jsonPath("$.status").value("EN_MANTENIMIENTO"))
                .andExpect(jsonPath("$.occupiedUntil").doesNotExist());
        mockMvc.perform(get("/api/resources/ESP-0413/availability" + slot).cookie(session))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    void theBoundaryInPostgresIsTheSameHalfOpenRange() throws Exception {
        Cookie session = login();

        mockMvc.perform(get("/api/resources/ESP-0305/availability?date=%s&start=10:00&end=12:00".formatted(DAY))
                        .cookie(session))
                .andExpect(jsonPath("$.available").value(true));
        mockMvc.perform(get("/api/resources/ESP-0305/availability?date=%s&start=09:59&end=10:30".formatted(DAY))
                        .cookie(session))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.status").value("BLOQUEO_ACADEMICO"));
    }

    @Test
    void anUnknownResourceIsA404() throws Exception {
        mockMvc.perform(get("/api/resources/ESP-9999/availability?date=%s&start=10:00&end=12:00".formatted(DAY))
                        .cookie(login()))
                .andExpect(status().isNotFound());
    }

    @Test
    void whatTheQuestionReportedOccupiedCannotBeReserved() throws Exception {
        Cookie session = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"estudiante@unimagdalena.edu.co\", \"password\": \"unimag2026\"}"))
                .andReturn().getResponse().getCookie("session");

        for (String id : new String[]{"ESP-0412", "ESP-0107"}) {
            mockMvc.perform(get("/api/resources/%s/availability?date=%s&start=10:00&end=12:00".formatted(id, DAY))
                            .cookie(session))
                    .andExpect(jsonPath("$.available").value(false));
            int status = mockMvc.perform(post("/api/reservations").cookie(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"resourceId\": \"%s\", \"date\": \"%s\", \"start\": \"10:00\", \"end\": \"12:00\"}"
                                    .formatted(id, DAY)))
                    .andReturn().getResponse().getStatus();
            assertThat(status).as(id).isEqualTo(409);
        }
        assertThat(jdbc.sql("SELECT count(*) FROM reservation WHERE status = 'CONFIRMADA'").query(Long.class).single())
                .isEqualTo(4);
    }
}
