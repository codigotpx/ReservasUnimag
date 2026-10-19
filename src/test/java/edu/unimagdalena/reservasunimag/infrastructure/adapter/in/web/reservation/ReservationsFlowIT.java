package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import edu.unimagdalena.reservasunimag.TestcontainersConfiguration;
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

import static edu.unimagdalena.reservasunimag.support.Database.clean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC1 y UC2 de punta a punta con sesión real, los Módulos 1 y 3 falsos y PostgreSQL de verdad. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class ReservationsFlowIT {

    private static final String DAY = LocalDate.now(edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot.ZONE).plusDays(1).toString();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyTables() {
        clean(jdbc);
    }

    private Cookie login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"unimag2026\"}".formatted(email)))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("session");
    }

    private org.springframework.test.web.servlet.ResultActions reserve(Cookie session, String body) throws Exception {
        return mockMvc.perform(post("/api/reservations").cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String space(String id, String start, String end) {
        return "{\"resourceId\": \"%s\", \"date\": \"%s\", \"start\": \"%s\", \"end\": \"%s\"}".formatted(id, DAY, start, end);
    }

    @Test
    void reservedSpaceAppearsOccupiedInTheConsultationAndInMyReservations() throws Exception {
        Cookie student = login("estudiante@unimagdalena.edu.co");

        reserve(student, space("ESP-0107", "10:00", "12:00"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("CONFIRMADA"))
                .andExpect(jsonPath("$.name").value("Sala de Estudio 3"))
                .andExpect(jsonPath("$.quota.used").value(1))
                .andExpect(jsonPath("$.quota.max").value(3));

        mockMvc.perform(get("/api/resources?date=%s&start=10:00&end=12:00&category=ESPACIO&type=SALA_DE_ESTUDIO".formatted(DAY))
                        .cookie(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resources[?(@.id=='ESP-0107')].status").value("RESERVADO"))
                .andExpect(jsonPath("$.resources[?(@.id=='ESP-0107')].selectable").value(false));

        mockMvc.perform(get("/api/reservations/mine").cookie(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations.length()").value(1))
                .andExpect(jsonPath("$.reservations[0].resourceId").value("ESP-0107"))
                .andExpect(jsonPath("$.reservations[0].name").value("Sala de Estudio 3"))
                .andExpect(jsonPath("$.quota.used").value(1))
                .andExpect(jsonPath("$.catalogUnavailable").value(false));
    }

    @Test
    void theSameSlotTakenTwiceIsRes004AndTheDenialIsRecorded() throws Exception {
        Cookie student = login("estudiante@unimagdalena.edu.co");
        Cookie monitorWithoutSanction = login("direccion@unimagdalena.edu.co");
        reserve(student, space("ESP-0107", "10:00", "12:00")).andExpect(status().isCreated());

        reserve(student, space("ESP-0107", "11:00", "13:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RES-004"))
                .andExpect(jsonPath("$.resourceId").value("ESP-0107"));
        reserve(monitorWithoutSanction, space("ESP-0107", "11:00", "13:00")).andExpect(status().isForbidden());

        assertThat(jdbc.sql("SELECT count(*) FROM denial WHERE code = 'RES-004'").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void anAssetIsReservedWithTheDueDateTheSystemCalculates() throws Exception {
        Cookie student = login("estudiante@unimagdalena.edu.co");

        mockMvc.perform(get("/api/loans/expected-due-date?resourceId=ACT-000913&date=%s&pickup=09:00".formatted(DAY))
                        .cookie(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termBusinessDays").value(3))
                .andExpect(jsonPath("$.dueAt").value(org.hamcrest.Matchers.endsWith("T22:00:00-05:00")));

        reserve(student, "{\"resourceId\": \"ACT-000913\", \"date\": \"%s\", \"pickup\": \"09:00\"}".formatted(DAY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("ACTIVO"))
                .andExpect(jsonPath("$.loan.termBusinessDays").value(3))
                .andExpect(jsonPath("$.loan.pickedUp").value(false))
                .andExpect(jsonPath("$.end").value(org.hamcrest.Matchers.endsWith("T22:00:00-05:00")));
    }

    @Test
    void anAssetInMaintenanceIsAProvisionalRes004() throws Exception {
        reserve(login("estudiante@unimagdalena.edu.co"),
                "{\"resourceId\": \"ACT-000912\", \"date\": \"%s\", \"pickup\": \"09:00\"}".formatted(DAY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RES-004"))
                .andExpect(jsonPath("$.detectedStatus").value("EN_MANTENIMIENTO"));
    }

    @Test
    void theSanctionedMonitorGetsRes003WithTheEndDate() throws Exception {
        reserve(login("monitor@unimagdalena.edu.co"), space("ESP-0107", "10:00", "12:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RES-003"))
                .andExpect(jsonPath("$.sanctionReason").value("Dos ausencias no justificadas en el semestre"))
                .andExpect(jsonPath("$.sanctionUntil").exists());
        assertThat(jdbc.sql("SELECT count(*) FROM reservation").query(Long.class).single()).isZero();
    }

    @Test
    void theFourthReservationIsRes002AndAFourthCancelledOneWouldNotBe() throws Exception {
        Cookie student = login("estudiante@unimagdalena.edu.co");
        reserve(student, space("ESP-0107", "08:00", "09:00")).andExpect(status().isCreated());
        reserve(student, space("ESP-0305", "08:00", "09:00")).andExpect(status().isCreated());
        reserve(student, space("ESP-0412", "08:00", "09:00")).andExpect(status().isCreated());

        reserve(student, space("ESP-0413", "08:00", "09:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RES-002"))
                .andExpect(jsonPath("$.active").value(3))
                .andExpect(jsonPath("$.max").value(3))
                .andExpect(jsonPath("$.nextRelease").exists());

        jdbc.sql("UPDATE reservation SET status = 'CANCELADA' WHERE resource_id = 'ESP-0107'").update();
        reserve(student, space("ESP-0413", "08:00", "09:00")).andExpect(status().isCreated());
    }

    @Test
    void everyConfirmedReservationLeavesExactlyOnePendingEvent() throws Exception {
        Cookie student = login("estudiante@unimagdalena.edu.co");
        reserve(student, space("ESP-0107", "10:00", "11:00")).andExpect(status().isCreated());
        reserve(student, space("ESP-0107", "10:30", "11:30")).andExpect(status().isConflict());

        assertThat(jdbc.sql("SELECT count(*) FROM outbox_message WHERE status = 'PENDING'").query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void invalidInputsAreRejectedBeforeAnythingElse() throws Exception {
        Cookie student = login("estudiante@unimagdalena.edu.co");

        reserve(student, space("ESP-0107", "09:00", "12:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DURATION_EXCEEDED"));
        reserve(student, "{\"resourceId\": \"ESP-0107\", \"date\": \"%s\", \"pickup\": \"09:00\"}".formatted(DAY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SHAPE_DOES_NOT_MATCH_CATEGORY"));
        reserve(student, space("NO-EXISTE", "10:00", "11:00")).andExpect(status().isNotFound());
        assertThat(jdbc.sql("SELECT count(*) FROM denial").query(Long.class).single()).isZero();
    }

    @Test
    void withoutSessionItIsA401() throws Exception {
        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content(space("ESP-0107", "10:00", "11:00")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reservations/mine")).andExpect(status().isUnauthorized());
    }
}
