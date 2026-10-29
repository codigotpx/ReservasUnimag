package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.schedule;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.ExtraordinaryNeedResult;
import edu.unimagdalena.reservasunimag.domain.model.error.AcademicConflictException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException.ExternalModule;
import edu.unimagdalena.reservasunimag.domain.model.error.InvalidScheduleFileException;
import edu.unimagdalena.reservasunimag.domain.model.error.UploadNotApplicableException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.upload.AffectedReservation;
import edu.unimagdalena.reservasunimag.domain.model.upload.ApplyResult;
import edu.unimagdalena.reservasunimag.domain.model.upload.RowVerdict;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleUpload;
import edu.unimagdalena.reservasunimag.domain.model.upload.UploadImpact;
import edu.unimagdalena.reservasunimag.domain.model.upload.UploadStatus;
import edu.unimagdalena.reservasunimag.domain.model.upload.UploadSummary;
import edu.unimagdalena.reservasunimag.domain.model.upload.ValidatedRow;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.ApplySchedulePort;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryScheduleUploadsPort;
import edu.unimagdalena.reservasunimag.domain.port.in.RegisterExtraordinaryNeedPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ValidateSchedulePort;
import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;
import edu.unimagdalena.reservasunimag.support.ContractFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.ContractFixtures.read;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Los endpoints de UC3 contra los fixtures de Contratos §1 a §3. */
@WebMvcTest(ScheduleController.class)
class ScheduleControllerTest {

    private static final UUID UPLOAD = UUID.fromString("7e2a4c81-3f95-4d60-b8a7-1c9e5d0f2a63");
    private static final UUID DIRECTOR = UUID.fromString("3c7d1e9a-4b72-4f08-a561-8d2e0c9f4b13");
    private static final ReservationHolder HOLDER = new ReservationHolder(UUID.randomUUID(), "2019114045",
            "Nombre del estudiante", Role.ESTUDIANTE);

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ValidateSchedulePort validateSchedule;
    @MockitoBean
    private ApplySchedulePort applySchedule;
    @MockitoBean
    private QueryScheduleUploadsPort queryUploads;
    @MockitoBean
    private RegisterExtraordinaryNeedPort registerExtraordinaryNeed;

    @TestConfiguration
    static class Properties {

        @Bean
        ReservationProperties reservationProperties() {
            return ContractFixtures.properties(URI.create("http://unused"), URI.create("http://unused"),
                    Duration.ofSeconds(1), 100, 5);
        }
    }

    private static Instant at(int month, int day, int hour, int minute, int second) {
        return ZonedDateTime.of(2026, month, day, hour, minute, second, 0, TimeSlot.ZONE).toInstant();
    }

    private static RequestPostProcessor session(Role role) {
        return jwt().jwt(token -> token.subject(DIRECTOR.toString()).claim("code", "DP-0001")
                .claim("role", role.name()));
    }

    private static ValidatedRow row(int number, String resourceId, String name, String course, String code,
                                    String group, String day, String start, String end, RowVerdict verdict,
                                    int sessions, String problem, String column, AcademicData conflict,
                                    List<AffectedReservation> affected) {
        return new ValidatedRow(number, resourceId, name, code, course, group, "Ingeniería de Sistemas", null, day,
                start, end, verdict, sessions, problem, column, conflict, affected);
    }

    private org.springframework.test.web.servlet.ResultActions upload(Role role) throws Exception {
        return mockMvc.perform(multipart("/api/schedules/validations")
                .file(new MockMultipartFile("file", "horario.csv", "text/csv", read("schedule-valid.csv").getBytes()))
                .param("academicTerm", "2026-2").param("termStart", "2026-08-10").param("termEnd", "2026-12-05")
                .with(session(role)));
    }

    @Test
    void anApplicableValidationListsTheReservationsItWouldCancel() throws Exception {
        var affected = new AffectedReservation(UUID.fromString("a1b2c3d4-5e6f-4789-9abc-def012345678"), HOLDER,
                at(9, 10, 14, 0, 0), at(9, 10, 16, 0, 0), ResourceCategory.ESPACIO);
        when(validateSchedule.validate(any(), any(), eq("2026-2"), eq(LocalDate.of(2026, 8, 10)),
                eq(LocalDate.of(2026, 12, 5)))).thenReturn(new ScheduleUpload(UPLOAD, "2026-2",
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 12, 5), "horario.csv", DIRECTOR, null,
                UploadStatus.VALIDATED, at(10, 2, 15, 40, 11), at(10, 2, 16, 10, 11), null,
                new UploadSummary(312, 300, 12, 0, 4680, 96, 0, 3), new UploadImpact(Set.of(), Set.of()), null,
                List.of(
                        row(2, "ESP-0412", "Laboratorio de Redes", "Redes de Computadores", "IS-402", "1", "LUNES",
                                "08:00", "10:00", RowVerdict.OK, 16, null, null, null, List.of()),
                        row(7, "ESP-0301", "Auditorio Menor", "Seminario de Investigación", "IS-510", "1",
                                "JUEVES", "14:00", "16:00", RowVerdict.DISPLACES_RESERVATIONS, 16, null, null, null,
                                List.of(affected)),
                        row(19, "ESP-0107", null, "Cálculo I", "MA-101", "3", "MARTES", "06:00", "08:00",
                                RowVerdict.UNCHANGED, 16, null, null, null, List.of()))));

        upload(Role.DIRECCION_PROGRAMA)
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-schedule-validation-applicable.json"), JsonCompareMode.STRICT));
    }

    @Test
    void aRejectedValidationTellsRowByRowWhatToFix() throws Exception {
        var clash = new AcademicData("IS-402", "Redes de Computadores", "1", "Ingeniería de Sistemas", null);
        when(validateSchedule.validate(any(), any(), any(), any(), any())).thenReturn(new ScheduleUpload(
                UUID.fromString("b4d9f0c2-7a16-4e38-95bd-2f8c1e0a7d45"), "2026-2", LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 12, 5), "horario.csv", DIRECTOR, null, UploadStatus.REJECTED,
                at(10, 2, 15, 44, 2), null, null, new UploadSummary(312, 307, 0, 5, 0, 0, 0, 0), UploadImpact.NONE,
                null, List.of(
                row(23, "ESP-9999", null, "Física II", "FI-202", "2", "MIERCOLES", "10:00", "12:00",
                        RowVerdict.REJECTED_RESOURCE, 0, "El recurso ESP-9999 no existe en el inventario.",
                        "resource_id", null, List.of()),
                row(41, "ESP-0412", null, "Sistemas Operativos", "IS-404", "1", "LUNES", "9:3", "11:30",
                        RowVerdict.REJECTED_FORMAT, 0, "La columna start_time no tiene el formato HH:mm.",
                        "start_time", null, List.of()),
                row(58, "ESP-0412", null, "Arquitectura de Computadores", "IS-301", "1", "LUNES", "09:00", "11:00",
                        RowVerdict.ACADEMIC_CLASH, 0, "Se cruza con otra clase ya bloqueada en este recurso: "
                                + "IS-402 grupo 1.", null, clash, List.of()))));

        upload(Role.DIRECCION_PROGRAMA)
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-schedule-validation-rejected.json"), JsonCompareMode.STRICT));
    }

    @Test
    void anEmptyFileOrABadHeaderIsA400WithItsCode() throws Exception {
        when(validateSchedule.validate(any(), any(), any(), any(), any()))
                .thenThrow(InvalidScheduleFileException.empty())
                .thenThrow(InvalidScheduleFileException.invalidHeader(List.of("resource_id")));

        upload(Role.DIRECCION_PROGRAMA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"))
                .andExpect(jsonPath("$.detail").value("El archivo no trae ninguna clase. No se cargó nada."));
        upload(Role.DIRECCION_PROGRAMA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_HEADER"))
                .andExpect(jsonPath("$.missingColumns[0]").value("resource_id"));
    }

    @Test
    void withoutModule1ItIsA503() throws Exception {
        when(validateSchedule.validate(any(), any(), any(), any(), any()))
                .thenThrow(new ExternalServiceUnavailableException(ExternalModule.MODULO_1, "caído", null));

        upload(Role.DIRECCION_PROGRAMA)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.module").value("MODULO_1"));
    }

    @Test
    void onlyTheProgramDirectionUploadsSchedules() throws Exception {
        upload(Role.ESTUDIANTE).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/schedules/" + UPLOAD + "/confirmation").with(session(Role.MONITOR)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(validateSchedule, applySchedule);
    }

    @Test
    void confirmingIsA200WithWhatWasDone() throws Exception {
        when(applySchedule.confirm(any(), eq(UPLOAD))).thenReturn(new ScheduleUpload(UPLOAD, "2026-2",
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 12, 5), "horario.csv", DIRECTOR, "Dirección de Programa",
                UploadStatus.APPLIED, at(10, 2, 15, 40, 11), at(10, 2, 16, 10, 11), at(10, 2, 15, 47, 35),
                new UploadSummary(312, 300, 12, 0, 4680, 96, 0, 3), UploadImpact.NONE,
                new ApplyResult(4680, 192, 0, 3, 3), List.of()));

        mockMvc.perform(post("/api/schedules/" + UPLOAD + "/confirmation").with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-schedule-confirmation.json"), JsonCompareMode.STRICT));
    }

    @Test
    void aChangedImpactIsA409ThatAsksToValidateAgain() throws Exception {
        when(applySchedule.confirm(any(), eq(UPLOAD))).thenThrow(UploadNotApplicableException.impactChanged(3, 5));

        mockMvc.perform(post("/api/schedules/" + UPLOAD + "/confirmation").with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isConflict())
                .andExpect(content().json(read("api-schedule-409-impact-changed.json"), JsonCompareMode.STRICT));
    }

    private static final String NEED = """
            {"resourceId": "ESP-0301", "date": "2026-09-10", "start": "14:00", "end": "16:00",
             "course": "Visita de pares académicos", "courseCode": "EXT-001", "group": "1",
             "program": "Ingeniería de Sistemas", "instructor": "Nombre del instructor", "confirm": %s}""";

    private ExtraordinaryNeedResult need(boolean applied) {
        var academic = new AcademicData("EXT-001", "Visita de pares académicos", "1", "Ingeniería de Sistemas",
                "Nombre del instructor");
        var affected = new AffectedReservation(UUID.fromString("a1b2c3d4-5e6f-4789-9abc-def012345678"), HOLDER,
                at(9, 10, 14, 0, 0), at(9, 10, 16, 0, 0), ResourceCategory.ESPACIO);
        var block = new Reservation(UUID.fromString("d5e8f1a2-6b94-4c07-8d31-0f7a2e9c5b48"), null, "ESP-0301",
                ResourceCategory.ESPACIO, ReservationOrigin.ACADEMICO, ReservationStatus.CONFIRMADA,
                at(9, 10, 14, 0, 0), at(9, 10, 16, 0, 0), at(9, 1, 8, 0, 0), null);
        return new ExtraordinaryNeedResult(applied, RowVerdict.DISPLACES_RESERVATIONS, "ESP-0301", "Auditorio Menor",
                at(9, 10, 14, 0, 0), at(9, 10, 16, 0, 0), academic, List.of(affected), applied ? block : null);
    }

    @Test
    void anExtraordinaryNeedPreviewsAndThenAppliesWithItsLocation() throws Exception {
        when(registerExtraordinaryNeed.register(any(), eq(false))).thenReturn(need(false));
        when(registerExtraordinaryNeed.register(any(), eq(true))).thenReturn(need(true));

        mockMvc.perform(post("/api/schedules/extraordinary-needs").with(session(Role.DIRECCION_PROGRAMA))
                        .contentType(MediaType.APPLICATION_JSON).content(NEED.formatted(false)))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-extraordinary-need-preview.json"), JsonCompareMode.STRICT));
        mockMvc.perform(post("/api/schedules/extraordinary-needs").with(session(Role.DIRECCION_PROGRAMA))
                        .contentType(MediaType.APPLICATION_JSON).content(NEED.formatted(true)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/reservations/d5e8f1a2-6b94-4c07-8d31-0f7a2e9c5b48"))
                .andExpect(content().json(read("api-extraordinary-need-201.json"), JsonCompareMode.STRICT));
    }

    @Test
    void anExtraordinaryNeedOverAnotherClassIsA409WithTheClass() throws Exception {
        when(registerExtraordinaryNeed.register(any(), anyBoolean())).thenThrow(new AcademicConflictException(
                RowVerdict.ACADEMIC_CLASH, "Se cruza con la clase IS-402 grupo 1, de 08:00 a 10:00. Una clase no "
                + "desplaza a otra: resuélvanlo entre los responsables.",
                new AcademicData("IS-402", "Redes de Computadores", "1", "Ingeniería de Sistemas", null)));

        mockMvc.perform(post("/api/schedules/extraordinary-needs").with(session(Role.DIRECCION_PROGRAMA))
                        .contentType(MediaType.APPLICATION_JSON).content(NEED.formatted(true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.verdict").value("ACADEMIC_CLASH"))
                .andExpect(jsonPath("$.conflictingBlock.courseCode").value("IS-402"))
                .andExpect(jsonPath("$.title").value("La franja ya tiene actividad docente"));
    }
}
