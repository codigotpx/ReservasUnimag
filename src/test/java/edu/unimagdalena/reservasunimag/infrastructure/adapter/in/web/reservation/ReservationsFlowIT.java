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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC1, UC2, UC4 y UC11 de punta a punta con sesión real, los Módulos 1 y 3 falsos y PostgreSQL de verdad. */
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

    private String reservedId(Cookie session, String body) throws Exception {
        String location = reserve(session, body).andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    @Test
    void aCancelledSlotIsAvailableAgainAndTheQuotaIsReleasedOnlyOnce() throws Exception {
        Cookie student = login("estudiante@unimagdalena.edu.co");
        String id = reservedId(student, space("ESP-0107", "10:00", "12:00"));
        mockMvc.perform(get("/api/reservations/mine").cookie(student))
                .andExpect(jsonPath("$.reservations[0].cancellable").value(true));

        mockMvc.perform(delete("/api/reservations/" + id).cookie(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"))
                .andExpect(jsonPath("$.cancellationOrigin").value("HOLDER"))
                .andExpect(jsonPath("$.noticeMinutes").isNumber())
                .andExpect(jsonPath("$.releasedTime.start").exists())
                .andExpect(jsonPath("$.quota.used").value(0))
                .andExpect(jsonPath("$.alreadyCancelled").doesNotExist());

        mockMvc.perform(get("/api/resources?date=%s&start=10:00&end=12:00&category=ESPACIO&type=SALA_DE_ESTUDIO".formatted(DAY))
                        .cookie(student))
                .andExpect(jsonPath("$.resources[?(@.id=='ESP-0107')].status").value("DISPONIBLE"))
                .andExpect(jsonPath("$.resources[?(@.id=='ESP-0107')].selectable").value(true));

        mockMvc.perform(delete("/api/reservations/" + id).cookie(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyCancelled").value(true))
                .andExpect(jsonPath("$.quota.used").value(0))
                .andExpect(jsonPath("$.noticeMinutes").doesNotExist());
        mockMvc.perform(get("/api/reservations/mine?vigentes=false").cookie(student))
                .andExpect(jsonPath("$.reservations[0].status").value("CANCELADA"))
                .andExpect(jsonPath("$.reservations[0].cancellable").value(false));
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_message WHERE type = 'CANCELLATION'").query(Long.class)
                .single()).isEqualTo(1);
    }

    @Test
    void someoneElsesReservationLooksLikeOneThatDoesNotExist() throws Exception {
        String id = reservedId(login("estudiante@unimagdalena.edu.co"), space("ESP-0107", "10:00", "12:00"));
        Cookie director = login("direccion@unimagdalena.edu.co");

        String othersBody = mockMvc.perform(delete("/api/reservations/" + id).cookie(director))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String missingBody = mockMvc.perform(delete("/api/reservations/" + java.util.UUID.randomUUID()).cookie(director))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(othersBody.replaceAll("\"instance\":\"[^\"]*\"", ""))
                .isEqualTo(missingBody.replaceAll("\"instance\":\"[^\"]*\"", ""));
        mockMvc.perform(delete("/api/reservations/" + id)).andExpect(status().isUnauthorized());
        assertThat(jdbc.sql("SELECT status FROM reservation").query(String.class).single()).isEqualTo("CONFIRMADA");
    }

    @Test
    void theHolderAndTheDirectorCanAuditWhatWasReportedAboutTheCancellation() throws Exception {
        Cookie student = login("estudiante@unimagdalena.edu.co");
        Cookie director = login("direccion@unimagdalena.edu.co");
        String id = reservedId(student, space("ESP-0107", "10:00", "12:00"));
        mockMvc.perform(get("/api/reservations/" + id + "/cancellation-report").cookie(student))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/reservations/" + id).cookie(student)).andExpect(status().isOk());

        mockMvc.perform(get("/api/reservations/" + id + "/cancellation-report").cookie(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationOrigin").value("HOLDER"))
                .andExpect(jsonPath("$.attributableToPerson").value(true))
                .andExpect(jsonPath("$.resource.name").value("Sala de Estudio 3"))
                .andExpect(jsonPath("$.holder.code").value("2021114001"))
                .andExpect(jsonPath("$.delivery.status").value("PENDING"));
        mockMvc.perform(get("/api/cancellation-reports?origin=HOLDER").cookie(director))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports.length()").value(1))
                .andExpect(jsonPath("$.summary.pending").value(1));
        mockMvc.perform(get("/api/cancellation-reports").cookie(student)).andExpect(status().isForbidden());
    }

    @Test
    void theDirectorPreviewsAndThenCancelsTheReservationsOfAResourceOutOfService() throws Exception {
        Cookie student = login("estudiante@unimagdalena.edu.co");
        Cookie director = login("direccion@unimagdalena.edu.co");
        reservedId(student, space("ESP-0305", "10:00", "12:00"));
        String body = "{\"reason\": \"Gotera en el salón.\", \"confirm\": %s}";

        mockMvc.perform(post("/api/resources/ESP-0305/cancel-reservations").cookie(director)
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.resourceName").value("Salón 305"))
                .andExpect(jsonPath("$.reservationsToCancel[0].holder.code").value("2021114001"));
        assertThat(jdbc.sql("SELECT status FROM reservation").query(String.class).single()).isEqualTo("CONFIRMADA");

        mockMvc.perform(post("/api/resources/ESP-0305/cancel-reservations").cookie(director)
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.reservationsCancelled").value(1))
                .andExpect(jsonPath("$.eventsQueued").value(1));
        assertThat(jdbc.sql("SELECT status FROM reservation").query(String.class).single())
                .isEqualTo("CANCELADA_POR_RECURSO_NO_DISPONIBLE");
        mockMvc.perform(post("/api/resources/ESP-0305/cancel-reservations").cookie(student)
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted(true)))
                .andExpect(status().isForbidden());
    }
}
