package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.TestcontainersConfiguration;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.ExtraordinaryNeed;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationDeniedException;
import edu.unimagdalena.reservasunimag.domain.model.error.UploadNotApplicableException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.upload.RowVerdict;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleFile;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleUpload;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.ApplySchedulePort;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryScheduleUploadsPort;
import edu.unimagdalena.reservasunimag.domain.port.in.RegisterExtraordinaryNeedPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReserveResourcesPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ValidateSchedulePort;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.Database.clean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC3 contra PostgreSQL: la carga, la prioridad académica en una sola transacción, la recarga y lo que queda (T019). */
@SpringBootTest(properties = {"reservations.outbox.enabled=false", "reservations.academic.use-start-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class AcademicPriorityIT {

    private static final CurrentUser DIRECTOR = new CurrentUser(
            UUID.fromString("00000000-0000-0000-0000-000000000003"), "DP-0001", Role.DIRECCION_PROGRAMA);
    private static final CurrentUser STUDENT = new CurrentUser(
            UUID.fromString("00000000-0000-0000-0000-000000000001"), "2021114001", Role.ESTUDIANTE);
    private static final LocalDate TERM_START = LocalDate.now(TimeSlot.ZONE).plusDays(1);
    private static final LocalDate TERM_END = TERM_START.plusDays(27);
    private static final LocalDate FIRST_MONDAY = TERM_START.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    private static final String HEADER = "course_code,course,group,program,instructor,resource_id,day_of_week,"
            + "start_time,end_time\n";
    private static final String NETWORKS = "IS-402,Redes de Computadores,1,Ingeniería de Sistemas,Docente,ESP-0412,"
            + "LUNES,08:00,10:00\n";
    private static final String SEMINAR = "IS-510,Seminario de Investigación,1,Ingeniería de Sistemas,,ESP-0201,"
            + "MARTES,14:00,17:00\n";

    @Autowired
    private ValidateSchedulePort validateSchedule;
    @Autowired
    private ApplySchedulePort applySchedule;
    @Autowired
    private QueryScheduleUploadsPort queryUploads;
    @Autowired
    private RegisterExtraordinaryNeedPort registerExtraordinaryNeed;
    @Autowired
    private ReserveResourcesPort reserveResources;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyTables() {
        clean(jdbc);
    }

    private ScheduleUpload validate(String rows) {
        return validateSchedule.validate(DIRECTOR, new ScheduleFile("horario.csv",
                (HEADER + rows).getBytes(StandardCharsets.UTF_8)), "IT-TERM", TERM_START, TERM_END);
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private long confirmedBlocks() {
        return count("SELECT count(*) FROM reservation WHERE origin = 'ACADEMICO' AND status = 'CONFIRMADA'");
    }

    @Test
    void confirmingCreatesEachSessionWithItsClassItsKeyItsRecordAndItsFicha() {
        ScheduleUpload validated = validate(NETWORKS + SEMINAR);
        assertThat(validated.applicable()).isTrue();
        assertThat(confirmedBlocks()).isZero();

        ScheduleUpload applied = applySchedule.confirm(DIRECTOR, validated.id());

        long sessions = validated.summary().sessionsToCreate();
        assertThat(sessions).isPositive();
        assertThat(applied.result().blocksCreated()).isEqualTo((int) sessions);
        assertThat(confirmedBlocks()).isEqualTo(sessions);
        assertThat(count("SELECT count(*) FROM academic_block WHERE session_key IS NOT NULL AND type = 'REGULAR'"))
                .isEqualTo(sessions);
        assertThat(count("SELECT count(*) FROM status_change WHERE reason = 'ACADEMIC_BLOCK_CREATED'"))
                .isEqualTo(sessions);
        assertThat(count("""
                SELECT count(*) FROM outbox_message
                WHERE type = 'RESERVATION_RECORD' AND payload->'data'->>'origin' = 'ACADEMICO'
                  AND payload->'data'->>'sanctionable' = 'false'
                  AND payload->'data'->'academic'->>'course' IS NOT NULL""")).isEqualTo(sessions);
        assertThat(jdbc.sql("SELECT status FROM semester_schedule").query(String.class).single()).isEqualTo("APPLIED");
    }

    @Test
    void afterTheUploadAStudentCannotReserveAClassSlot() {
        applySchedule.confirm(DIRECTOR, validate(NETWORKS).id());

        assertThatThrownBy(() -> reserveResources.reserve(ReservationRequest.forSpace(STUDENT, "ESP-0412",
                FIRST_MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))))
                .isInstanceOfSatisfying(ReservationDeniedException.class,
                        denied -> assertThat(denied.code().code()).isEqualTo("RES-001"));
    }

    @Test
    void aDisplacedReservationIsCancelledInTheSameTransactionAndReportedWithoutBlame() {
        var displaced = reserveResources.reserve(ReservationRequest.forSpace(STUDENT, "ESP-0412", FIRST_MONDAY,
                LocalTime.of(9, 0), LocalTime.of(11, 0))).reservation();
        ScheduleUpload validated = validate(NETWORKS);
        assertThat(validated.rows().get(0).verdict()).isEqualTo(RowVerdict.DISPLACES_RESERVATIONS);
        assertThat(validated.impact().reservationsToCancel()).containsExactly(displaced.id());

        applySchedule.confirm(DIRECTOR, validated.id());

        assertThat(jdbc.sql("SELECT status FROM reservation WHERE id = :id").param("id", displaced.id())
                .query(String.class).single()).isEqualTo("CANCELADA_POR_PRIORIDAD_ACADEMICA");
        var report = jdbc.sql("SELECT cancellation_origin, attributable_to_person, reason FROM cancellation_report")
                .query().singleRow();
        assertThat(report.get("cancellation_origin")).isEqualTo("ACADEMIC_PRIORITY");
        assertThat(report.get("attributable_to_person")).isEqualTo(false);
        assertThat(report.get("reason")).isEqualTo("La franja se asignó a actividad docente: IS-402 grupo 1.");
    }

    @Test
    void reloadingTheSameFileCreatesNothingAndTheDatabaseRefusesADuplicateKey() {
        applySchedule.confirm(DIRECTOR, validate(NETWORKS).id());
        long before = confirmedBlocks();

        ScheduleUpload reloaded = validate(NETWORKS);
        assertThat(reloaded.rows().get(0).verdict()).isEqualTo(RowVerdict.UNCHANGED);
        assertThat(applySchedule.confirm(DIRECTOR, reloaded.id()).result().blocksCreated()).isZero();
        assertThat(confirmedBlocks()).isEqualTo(before);

        String key = jdbc.sql("SELECT session_key FROM academic_block LIMIT 1").query(String.class).single();
        UUID other = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO reservation (id, resource_id, resource_category, origin, status, starts_at, ends_at)
                        VALUES (:id, 'ESP-0413', 'ESPACIO', 'ACADEMICO', 'CONFIRMADA', now() + interval '60 days',
                                now() + interval '60 days 1 hour')""").param("id", other).update();
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO academic_block (reservation_id, type, course_code, course, group_code, program, session_key)
                VALUES (:id, 'EXTRAORDINARIO', 'X', 'X', '1', 'X', :key)""")
                .param("id", other).param("key", key).update()).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void aClassThatLeftTheFileIsWithdrawnItsKeyIsReleasedAndItCanComeBack() {
        applySchedule.confirm(DIRECTOR, validate(NETWORKS + SEMINAR).id());
        long seminarSessions = count("SELECT count(*) FROM academic_block WHERE course_code = 'IS-510'");

        ScheduleUpload withoutSeminar = validate(NETWORKS);
        assertThat(withoutSeminar.summary().blocksToWithdraw()).isEqualTo((int) seminarSessions);
        applySchedule.confirm(DIRECTOR, withoutSeminar.id());

        assertThat(count("""
                SELECT count(*) FROM academic_block b JOIN reservation r ON r.id = b.reservation_id
                WHERE b.course_code = 'IS-510' AND r.status = 'CANCELADA' AND b.session_key IS NULL"""))
                .isEqualTo(seminarSessions);
        assertThat(count("SELECT count(*) FROM cancellation_report WHERE cancellation_origin = 'SCHEDULE_WITHDRAWN'"))
                .isEqualTo(seminarSessions);

        applySchedule.confirm(DIRECTOR, validate(NETWORKS + SEMINAR).id());
        assertThat(count("""
                SELECT count(*) FROM academic_block b JOIN reservation r ON r.id = b.reservation_id
                WHERE b.course_code = 'IS-510' AND r.status = 'CONFIRMADA'""")).isEqualTo(seminarSessions);
    }

    @Test
    void anImpactThatChangedLeavesEverythingAsItWas() {
        ScheduleUpload validated = validate(NETWORKS);
        reserveResources.reserve(ReservationRequest.forSpace(STUDENT, "ESP-0412", FIRST_MONDAY, LocalTime.of(9, 0),
                LocalTime.of(11, 0)));

        assertThatThrownBy(() -> applySchedule.confirm(DIRECTOR, validated.id()))
                .isInstanceOfSatisfying(UploadNotApplicableException.class,
                        error -> assertThat(error.code()).isEqualTo(UploadNotApplicableException.Code.IMPACT_CHANGED));

        assertThat(confirmedBlocks()).isZero();
        assertThat(count("SELECT count(*) FROM reservation WHERE status <> 'CONFIRMADA'")).isZero();
        assertThat(jdbc.sql("SELECT status FROM semester_schedule").query(String.class).single())
                .isEqualTo("VALIDATED");
    }

    @Test
    void theReportIsKeptAndCanBeReadAgainRowByRow() {
        ScheduleUpload rejected = validate(NETWORKS + "FI-202,Física II,2,Civil,,ESP-9999,MIERCOLES,10:00,12:00\n");

        var stored = queryUploads.find(rejected.id());

        assertThat(stored.status().name()).isEqualTo("REJECTED");
        assertThat(stored.uploadedByName()).isEqualTo("Dirección de Programa de Prueba");
        assertThat(stored.rows()).hasSize(2);
        assertThat(stored.rows()).extracting(row -> row.verdict())
                .containsExactly(RowVerdict.OK, RowVerdict.REJECTED_RESOURCE);
        assertThat(stored.rows().get(1).row()).isEqualTo(3);
        assertThat(stored.summary()).isEqualTo(rejected.summary());
    }

    @Test
    void anExtraordinaryNeedDisplacesAStudentAndIsAnExtraordinaryBlock() {
        var displaced = reserveResources.reserve(ReservationRequest.forSpace(STUDENT, "ESP-0305", FIRST_MONDAY,
                LocalTime.of(14, 0), LocalTime.of(16, 0))).reservation();

        var applied = registerExtraordinaryNeed.register(new ExtraordinaryNeed("ESP-0305", FIRST_MONDAY,
                LocalTime.of(14, 0), LocalTime.of(17, 0), new AcademicData("EXT-001", "Visita de pares", "1",
                "Ingeniería de Sistemas", null)), true);

        assertThat(applied.applied()).isTrue();
        assertThat(jdbc.sql("SELECT status FROM reservation WHERE id = :id").param("id", displaced.id())
                .query(String.class).single()).isEqualTo("CANCELADA_POR_PRIORIDAD_ACADEMICA");
        var block = jdbc.sql("SELECT type, semester_schedule_id IS NULL AS free FROM academic_block").query()
                .singleRow();
        assertThat(block.get("type")).isEqualTo("EXTRAORDINARIO");
        assertThat(block.get("free")).isEqualTo(true);
    }

    @Test
    void theWholeFlowWorksOverHttpWithAMultipartFile() throws Exception {
        Cookie director = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"direccion@unimagdalena.edu.co\", \"password\": \"unimag2026\"}"))
                .andReturn().getResponse().getCookie("session");

        String body = mockMvc.perform(multipart("/api/schedules/validations")
                        .file(new MockMultipartFile("file", "horario.csv", "text/csv",
                                (HEADER + NETWORKS).getBytes(StandardCharsets.UTF_8)))
                        .param("academicTerm", "IT-TERM").param("termStart", TERM_START.toString())
                        .param("termEnd", TERM_END.toString())
                        .cookie(director))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.rows[0].verdict").value("OK"))
                .andReturn().getResponse().getContentAsString();
        String uploadId = objectMapper.readTree(body).get("uploadId").asString();

        mockMvc.perform(post("/api/schedules/" + uploadId + "/confirmation").cookie(director))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.appliedBy.name").value("Dirección de Programa de Prueba"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/schedules")
                        .cookie(director))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploads[0].status").value("APPLIED"));

        mockMvc.perform(multipart("/api/schedules/validations")
                        .file(new MockMultipartFile("file", "vacio.csv", "text/csv",
                                HEADER.getBytes(StandardCharsets.UTF_8)))
                        .param("academicTerm", "IT-TERM").param("termStart", TERM_START.toString())
                        .param("termEnd", TERM_END.toString())
                        .cookie(director))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"));
        assertThat(confirmedBlocks()).isPositive();
    }
}
