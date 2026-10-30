package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionScope;
import edu.unimagdalena.reservasunimag.domain.model.compliance.Sanction;
import edu.unimagdalena.reservasunimag.domain.model.error.DurationExceededException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException.ExternalModule;
import edu.unimagdalena.reservasunimag.domain.model.error.IncompleteResourceDetailsException;
import edu.unimagdalena.reservasunimag.domain.model.error.NotAnAssetException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationDeniedException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationShapeException;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ExpectedDueDate;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.LoanPeriod;
import edu.unimagdalena.reservasunimag.domain.model.reservation.MyReservations;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Quota;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationResult;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.CancelReservationPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ListMyReservationsPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReserveResourcesPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.ContractFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La forma exacta de los endpoints de UC2 contra los fixtures de UC2 Contratos §1 a §3. */
@WebMvcTest(ReservationController.class)
class ReservationControllerTest {

    private static final UUID SPACE_ID = UUID.fromString("9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45");
    private static final UUID LOAN_ID = UUID.fromString("4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3");
    private static final UUID HOLDER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant CREATED = at(2026, 8, 30, 9, 14, 22);
    private static final String SPACE_BODY = "{\"resourceId\": \"ESP-0107\", \"date\": \"2026-09-01\", \"start\": \"10:00\", \"end\": \"12:00\"}";
    private static final String ASSET_BODY = "{\"resourceId\": \"ACT-004512\", \"date\": \"2026-09-01\", \"pickup\": \"14:30\"}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ReserveResourcesPort reserveResources;
    @MockitoBean
    private ListMyReservationsPort listMyReservations;
    @MockitoBean
    private CancelReservationPort cancelReservation;

    private static Instant at(int year, int month, int day, int hour, int minute, int second) {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, TimeSlot.ZONE).toInstant();
    }

    private static RequestPostProcessor session(Role role) {
        return jwt().jwt(token -> token.subject(HOLDER.toString()).claim("code", "2021114001").claim("role", role.name()));
    }

    private static final Resource STUDY_ROOM = Resource.space("ESP-0107", "Sala de Estudio 3", "SALA_DE_ESTUDIO",
            "Biblioteca, piso 1", OperationalStatus.DISPONIBLE, "Biblioteca Central", 8, List.of());
    private static final Resource BOOK = Resource.asset("ACT-004512", "Libro de Cálculo I", "LIBRO",
            "Biblioteca, estantería C-4", OperationalStatus.DISPONIBLE, "BUENO", 7);

    private static Reservation spaceReservation() {
        return Reservation.confirmed(SPACE_ID, HOLDER, "ESP-0107", ResourceCategory.ESPACIO, at(2026, 9, 1, 10, 0, 0),
                at(2026, 9, 1, 12, 0, 0), CREATED, null);
    }

    private static Reservation loanReservation(Loan loan) {
        return Reservation.confirmed(LOAN_ID, HOLDER, "ACT-004512", ResourceCategory.ACTIVO, at(2026, 9, 1, 14, 30, 0),
                at(2026, 9, 10, 22, 0, 0), CREATED, loan);
    }

    private org.springframework.test.web.servlet.ResultActions postSpace() throws Exception {
        return mockMvc.perform(post("/api/reservations").with(session(Role.ESTUDIANTE))
                .contentType(MediaType.APPLICATION_JSON).content(SPACE_BODY));
    }

    // -------------------------------------------------------------------------------------------- 201

    @Test
    void reservingASpaceIsA201WithLocationAndTheContractBody() throws Exception {
        when(reserveResources.reserve(any())).thenReturn(
                new ReservationResult(spaceReservation(), STUDY_ROOM, new Quota(2, 3)));

        postSpace()
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/reservations/" + SPACE_ID))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json(read("api-reservation-space-201.json"), JsonCompareMode.STRICT));
    }

    @Test
    void reservingAnAssetIsA201WithItsLoanAndTheDueDate() throws Exception {
        when(reserveResources.reserve(any())).thenReturn(
                new ReservationResult(loanReservation(Loan.reserved(7)), BOOK, new Quota(2, 3)));

        mockMvc.perform(post("/api/reservations").with(session(Role.MONITOR))
                        .contentType(MediaType.APPLICATION_JSON).content(ASSET_BODY))
                .andExpect(status().isCreated())
                .andExpect(content().json(read("api-reservation-asset-201.json"), JsonCompareMode.STRICT));
    }

    @Test
    void theRequestIsBuiltFromTheSessionAndTheBodyNeverFromAnOwnerInTheBody() throws Exception {
        when(reserveResources.reserve(any())).thenReturn(
                new ReservationResult(spaceReservation(), STUDY_ROOM, new Quota(1, 3)));

        mockMvc.perform(post("/api/reservations").with(session(Role.ESTUDIANTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\": \"ESP-0107\", \"date\": \"2026-09-01\", \"start\": \"10:00\", "
                                + "\"end\": \"12:00\", \"userId\": \"otra-persona\", \"origin\": \"ACADEMICO\"}"))
                .andExpect(status().isCreated());

        var captor = ArgumentCaptor.forClass(ReservationRequest.class);
        verify(reserveResources).reserve(captor.capture());
        assertThat(captor.getValue()).isEqualTo(ReservationRequest.forSpace(
                new CurrentUser(HOLDER, "2021114001", Role.ESTUDIANTE), "ESP-0107", LocalDate.of(2026, 9, 1),
                LocalTime.of(10, 0), LocalTime.of(12, 0)));
    }

    @Test
    void noOptionalFieldEverTravelsAsNull() throws Exception {
        when(reserveResources.reserve(any())).thenReturn(
                new ReservationResult(spaceReservation(), STUDY_ROOM, new Quota(1, 3)));

        assertThat(postSpace().andReturn().getResponse().getContentAsString()).doesNotContain("null");
    }

    // -------------------------------------------------------------------------------------------- 400

    @Test
    void aSlotOverTheMaximumIsA400WithItsLimit() throws Exception {
        when(reserveResources.reserve(any())).thenThrow(new DurationExceededException(120, 180));

        postSpace()
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(content().json(read("api-reservation-400-duration.json"), JsonCompareMode.STRICT));
    }

    @Test
    void aBodyThatDoesNotMatchTheCategoryIsA400WithoutADenialCode() throws Exception {
        when(reserveResources.reserve(any())).thenThrow(new ReservationShapeException("Un activo se reserva con pickup"));

        postSpace()
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SHAPE_DOES_NOT_MATCH_CATEGORY"));
    }

    @Test
    void missingRequiredFieldsAreA400OfParameters() throws Exception {
        mockMvc.perform(post("/api/reservations").with(session(Role.ESTUDIANTE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"start\": \"10:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://reservasunimag.unimagdalena.edu.co/errors/invalid-parameters"))
                .andExpect(jsonPath("$.errors.length()").value(2));
        verifyNoInteractions(reserveResources);
    }

    // ------------------------------------------------------------------------------------ 403, 404, 409

    @Test
    void theProgramDirectionCannotReserve() throws Exception {
        mockMvc.perform(post("/api/reservations").with(session(Role.DIRECCION_PROGRAMA))
                        .contentType(MediaType.APPLICATION_JSON).content(SPACE_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reserveResources);
    }

    @Test
    void anUnknownResourceIsA404() throws Exception {
        when(reserveResources.reserve(any())).thenThrow(new ResourceNotFoundException("ACT-999999"));

        postSpace()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://reservasunimag.unimagdalena.edu.co/errors/resource-not-found"))
                .andExpect(jsonPath("$.resourceId").value("ACT-999999"))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"));
    }

    private void expectDenial(ReservationDeniedException denial, String fixture) throws Exception {
        when(reserveResources.reserve(any())).thenThrow(denial);

        postSpace()
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(content().json(read(fixture), JsonCompareMode.STRICT));
    }

    @Test
    void res001ComesWithTheResourceAndUntilWhen() throws Exception {
        expectDenial(ReservationDeniedException.academicConflict("ESP-0412", at(2026, 9, 1, 10, 0, 0)),
                "api-reservation-409-res-001.json");
    }

    @Test
    void res002ComesWithThreeOfThreeAndTheNextRelease() throws Exception {
        expectDenial(ReservationDeniedException.quotaExhausted(3, 3, at(2026, 9, 1, 12, 0, 0)),
                "api-reservation-409-res-002.json");
    }

    @Test
    void res003ComesWithTheReasonAndTheEndDate() throws Exception {
        expectDenial(ReservationDeniedException.activeSanction(new Sanction(
                "Dos ausencias no justificadas en el semestre", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15),
                SanctionScope.TODO)), "api-reservation-409-res-003.json");
    }

    @Test
    void res004SaysTheResourceWasJustTakenAndNeverByWhom() throws Exception {
        expectDenial(ReservationDeniedException.resourceTaken("ESP-0107"), "api-reservation-409-res-004.json");
    }

    @Test
    void res004ForMaintenanceCarriesTheDetectedStatus() throws Exception {
        expectDenial(ReservationDeniedException.underMaintenance("ACT-000912"),
                "api-reservation-409-res-004-mantenimiento.json");
    }

    @Test
    void everyDenialHasACodeAndAMessage() throws Exception {
        List<ReservationDeniedException> all = List.of(
                ReservationDeniedException.academicConflict("R", null),
                ReservationDeniedException.quotaExhausted(3, 3, null),
                ReservationDeniedException.activeSanction(new Sanction(null, null, null, SanctionScope.TODO)),
                ReservationDeniedException.resourceTaken("R"),
                ReservationDeniedException.underMaintenance("R"));
        for (ReservationDeniedException denial : all) {
            doThrow(denial).when(reserveResources).reserve(any());

            postSpace().andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(denial.code().code()))
                    .andExpect(jsonPath("$.detail").isNotEmpty());
        }
    }

    // ------------------------------------------------------------------------------------------------ 503

    @Test
    void inventoryDownIsA503() throws Exception {
        when(reserveResources.reserve(any())).thenThrow(
                new ExternalServiceUnavailableException(ExternalModule.MODULO_1, "caído", null));

        postSpace()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("https://reservasunimag.unimagdalena.edu.co/errors/inventory-unavailable"))
                .andExpect(jsonPath("$.module").value("MODULO_1"));
    }

    @Test
    void complianceDownBlocksTheReservationWithItsOwn503() throws Exception {
        when(reserveResources.reserve(any())).thenThrow(
                new ExternalServiceUnavailableException(ExternalModule.MODULO_3, "caído", null));

        postSpace()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("https://reservasunimag.unimagdalena.edu.co/errors/sanctions-not-verifiable"))
                .andExpect(jsonPath("$.module").value("MODULO_3"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("la reserva no se confirmó")));
    }

    @Test
    void anIncompleteInventoryFichaIsA503WithItsCode() throws Exception {
        when(reserveResources.reserve(any())).thenThrow(new IncompleteResourceDetailsException("ACT-1"));

        postSpace()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INCOMPLETE_RESOURCE_DETAILS"));
    }

    // ---------------------------------------------------------------------------- vencimiento y mis reservas

    @Test
    void theExpectedDueDateMatchesTheContract() throws Exception {
        var period = new LoanPeriod(at(2026, 9, 1, 14, 30, 0), at(2026, 9, 10, 22, 0, 0), 7,
                List.of(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 6)));
        when(reserveResources.expectedDueDate("ACT-004512", LocalDate.of(2026, 9, 1), LocalTime.of(14, 30)))
                .thenReturn(new ExpectedDueDate(BOOK, period));

        mockMvc.perform(get("/api/loans/expected-due-date?resourceId=ACT-004512&date=2026-09-01&pickup=14:30")
                        .with(session(Role.ESTUDIANTE)))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-expected-due-date.json"), JsonCompareMode.STRICT));
    }

    @Test
    void askingTheDueDateOfASpaceIsA409NotAnAsset() throws Exception {
        when(reserveResources.expectedDueDate(any(), any(), any())).thenThrow(new NotAnAssetException("ESP-0107"));

        mockMvc.perform(get("/api/loans/expected-due-date?resourceId=ESP-0107&date=2026-09-01&pickup=14:30")
                        .with(session(Role.ESTUDIANTE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_AN_ASSET"));
    }

    @Test
    void myReservationsMatchTheContractIncludingTheLoanState() throws Exception {
        var pickedUp = new Loan(7, at(2026, 9, 1, 14, 35, 0), null);
        when(listMyReservations.list(any(), org.mockito.ArgumentMatchers.eq(true))).thenReturn(new MyReservations(
                List.of(new MyReservations.Item(spaceReservation(), "Sala de Estudio 3", false, true, null),
                        new MyReservations.Item(loanReservation(pickedUp), "Libro de Cálculo I", false, false, null)),
                new Quota(2, 3), false));

        mockMvc.perform(get("/api/reservations/mine").with(session(Role.ESTUDIANTE)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json(read("api-my-reservations.json"), JsonCompareMode.STRICT));
    }

    @Test
    void aReservationWithAnAbsenceShowsTheMarkWithoutPromisingASanction() throws Exception {
        var finished = new Reservation(SPACE_ID, HOLDER, "ESP-0107", ResourceCategory.ESPACIO,
                edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin.ESTUDIANTIL,
                edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus.FINALIZADA,
                at(2026, 9, 1, 10, 0, 0), at(2026, 9, 1, 12, 0, 0), CREATED, null);
        var absence = edu.unimagdalena.reservasunimag.domain.model.compliance.Absence.registered(SPACE_ID,
                UUID.randomUUID(), at(2026, 9, 1, 10, 10, 30), at(2026, 9, 1, 10, 11, 5), true);
        when(listMyReservations.list(any(), org.mockito.ArgumentMatchers.eq(false))).thenReturn(new MyReservations(
                List.of(new MyReservations.Item(finished, "Sala de Estudio 3", false, false, absence)),
                new Quota(0, 3), false));

        mockMvc.perform(get("/api/reservations/mine?vigentes=false").with(session(Role.ESTUDIANTE)))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-my-reservations-absence.json"), JsonCompareMode.STRICT));
    }

    @Test
    void myReservationsOmitTheNamesWhenInventoryIsDown() throws Exception {
        when(listMyReservations.list(any(), org.mockito.ArgumentMatchers.eq(false))).thenReturn(new MyReservations(
                List.of(new MyReservations.Item(spaceReservation(), null, false, true, null)), new Quota(1, 3), true));

        mockMvc.perform(get("/api/reservations/mine?vigentes=false").with(session(Role.ESTUDIANTE)))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-my-reservations-catalog-down.json"), JsonCompareMode.STRICT));
    }
}
