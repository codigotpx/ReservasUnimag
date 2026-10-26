package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import edu.unimagdalena.reservasunimag.domain.model.error.CancellationDeniedException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Cancellation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationResult;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.MaintenanceCancellation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Quota;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.CancelForMaintenancePort;
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

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.ContractFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La forma exacta de los endpoints de UC4 contra los fixtures de UC4 Contratos §1 y §2. */
@WebMvcTest({ReservationController.class, MaintenanceCancellationController.class})
class CancelReservationControllerTest {

    private static final UUID SPACE_ID = UUID.fromString("9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45");
    private static final UUID LOAN_ID = UUID.fromString("4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3");
    private static final UUID HOLDER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String REASON = "El salón quedó fuera de servicio por una gotera.";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ReserveResourcesPort reserveResources;
    @MockitoBean
    private ListMyReservationsPort listMyReservations;
    @MockitoBean
    private CancelReservationPort cancelReservation;
    @MockitoBean
    private CancelForMaintenancePort cancelForMaintenance;

    private static Instant at(int year, int month, int day, int hour, int minute, int second) {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, TimeSlot.ZONE).toInstant();
    }

    private static RequestPostProcessor session(Role role) {
        return jwt().jwt(token -> token.subject(HOLDER.toString()).claim("code", "2021114001").claim("role", role.name()));
    }

    private static Reservation space() {
        return Reservation.confirmed(SPACE_ID, HOLDER, "ESP-0107", ResourceCategory.ESPACIO, at(2026, 9, 1, 10, 0, 0),
                at(2026, 9, 1, 12, 0, 0), at(2026, 8, 30, 9, 14, 22), null);
    }

    private static final Cancellation BY_HOLDER = new Cancellation(CancellationOrigin.HOLDER,
            CancellationReport.HOLDER_REASON, 1486, at(2026, 8, 31, 9, 14, 22));

    // -------------------------------------------------------------------------------------------- DELETE

    @Test
    void cancellingASpaceIsA200WithTheReleasedSlotAndTheQuota() throws Exception {
        when(cancelReservation.cancelByHolder(any(), eq(SPACE_ID))).thenReturn(new CancellationResult(
                space().cancel(CancellationOrigin.HOLDER), BY_HOLDER, new Quota(1, 3), false));

        mockMvc.perform(delete("/api/reservations/" + SPACE_ID).with(session(Role.ESTUDIANTE)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json(read("api-cancellation-200.json"), JsonCompareMode.STRICT));
    }

    @Test
    void cancellingAnAssetReleasesTheWholeLoanPeriod() throws Exception {
        Reservation loan = Reservation.confirmed(LOAN_ID, HOLDER, "ACT-004512", ResourceCategory.ACTIVO,
                at(2026, 9, 3, 14, 30, 0), at(2026, 9, 14, 22, 0, 0), at(2026, 9, 1, 8, 0, 0), Loan.reserved(7));
        when(cancelReservation.cancelByHolder(any(), eq(LOAN_ID))).thenReturn(new CancellationResult(
                loan.cancel(CancellationOrigin.HOLDER), new Cancellation(CancellationOrigin.HOLDER,
                CancellationReport.HOLDER_REASON, 1225, at(2026, 9, 2, 18, 5, 0)), new Quota(1, 3), false));

        mockMvc.perform(delete("/api/reservations/" + LOAN_ID).with(session(Role.MONITOR)))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-cancellation-200-asset.json"), JsonCompareMode.STRICT));
    }

    @Test
    void cancellingTwiceIsA200WithAlreadyCancelledAndTheOriginalInstant() throws Exception {
        when(cancelReservation.cancelByHolder(any(), eq(SPACE_ID))).thenReturn(new CancellationResult(
                space().cancel(CancellationOrigin.HOLDER), new Cancellation(CancellationOrigin.HOLDER, null, null,
                at(2026, 8, 31, 9, 14, 22)), new Quota(1, 3), true));

        mockMvc.perform(delete("/api/reservations/" + SPACE_ID).with(session(Role.ESTUDIANTE)))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-cancellation-200-idempotent.json"), JsonCompareMode.STRICT));
    }

    @Test
    void theHolderComesFromTheSession() throws Exception {
        when(cancelReservation.cancelByHolder(any(), eq(SPACE_ID))).thenReturn(new CancellationResult(
                space().cancel(CancellationOrigin.HOLDER), BY_HOLDER, new Quota(1, 3), false));

        mockMvc.perform(delete("/api/reservations/" + SPACE_ID).with(session(Role.ESTUDIANTE)))
                .andExpect(status().isOk());

        ArgumentCaptor<CurrentUser> user = ArgumentCaptor.forClass(CurrentUser.class);
        verify(cancelReservation).cancelByHolder(user.capture(), eq(SPACE_ID));
        assertThat(user.getValue().id()).isEqualTo(HOLDER);
    }

    @Test
    void tooLateIsA409Can001WithTheNoticeAndTheReference() throws Exception {
        when(cancelReservation.cancelByHolder(any(), eq(SPACE_ID))).thenThrow(CancellationDeniedException.tooLate(
                Duration.ofMinutes(10), at(2026, 9, 1, 10, 0, 0), false, false));

        mockMvc.perform(delete("/api/reservations/" + SPACE_ID).with(session(Role.ESTUDIANTE)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().json(read("api-cancellation-409-late.json"), JsonCompareMode.STRICT));
    }

    @Test
    void anAssetInHandsIsA409Can001WithItsOwnReason() throws Exception {
        when(cancelReservation.cancelByHolder(any(), eq(LOAN_ID))).thenThrow(
                CancellationDeniedException.assetAlreadyPickedUp(at(2026, 9, 3, 14, 35, 0)));

        mockMvc.perform(delete("/api/reservations/" + LOAN_ID).with(session(Role.ESTUDIANTE)))
                .andExpect(status().isConflict())
                .andExpect(content().json(read("api-cancellation-409-picked-up.json"), JsonCompareMode.STRICT));
    }

    @Test
    void someoneElsesReservationIsA404ThatRevealsNothing() throws Exception {
        when(cancelReservation.cancelByHolder(any(), eq(SPACE_ID))).thenThrow(
                new ReservationNotFoundException(SPACE_ID));

        mockMvc.perform(delete("/api/reservations/" + SPACE_ID).with(session(Role.ESTUDIANTE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La reserva no existe."))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void aMalformedIdIsA400() throws Exception {
        mockMvc.perform(delete("/api/reservations/no-es-un-uuid").with(session(Role.ESTUDIANTE)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cancelReservation);
    }

    // ------------------------------------------------------------------------------- mantenimiento

    private static MaintenanceCancellation preview() {
        Reservation upcoming = Reservation.confirmed(UUID.fromString("c7e1a930-8b24-4d16-95af-0e3d7c1b2f84"), HOLDER,
                "ESP-0220", ResourceCategory.ESPACIO, at(2026, 9, 5, 8, 0, 0), at(2026, 9, 5, 10, 0, 0),
                at(2026, 9, 1, 8, 0, 0), null);
        Reservation inHands = Reservation.confirmed(UUID.fromString("a3f8d215-6c47-4e09-b8d1-5f2e9a0c7b63"), HOLDER,
                "ESP-0220", ResourceCategory.ACTIVO, at(2026, 9, 1, 8, 0, 0), at(2026, 9, 4, 22, 0, 0),
                at(2026, 9, 1, 7, 0, 0), new Loan(3, at(2026, 9, 1, 8, 5, 0), null));
        return new MaintenanceCancellation("ESP-0220", "Laboratorio de Química",
                List.of(new MaintenanceCancellation.Affected(upcoming,
                        new ReservationHolder(HOLDER, "2019114045", "Nombre del estudiante", Role.ESTUDIANTE))),
                List.of(new MaintenanceCancellation.Skipped(inHands, "ASSET_ALREADY_PICKED_UP")), null);
    }

    private org.springframework.test.web.servlet.ResultActions maintenance(Role role, boolean confirm)
            throws Exception {
        return mockMvc.perform(post("/api/resources/ESP-0220/cancel-reservations").with(session(role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"%s\", \"confirm\": %s}".formatted(REASON, confirm)));
    }

    @Test
    void withoutConfirmTheMaintenanceEndpointOnlyPreviews() throws Exception {
        when(cancelForMaintenance.preview("ESP-0220")).thenReturn(preview());

        maintenance(Role.DIRECCION_PROGRAMA, false)
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-cancel-by-maintenance-preview.json"), JsonCompareMode.STRICT));
        verify(cancelForMaintenance).preview("ESP-0220");
    }

    @Test
    void withConfirmItAppliesAndCountsWhatItDid() throws Exception {
        MaintenanceCancellation preview = preview();
        var affected = preview.affected().get(0);
        when(cancelForMaintenance.apply("ESP-0220", REASON)).thenReturn(new MaintenanceCancellation("ESP-0220",
                "Laboratorio de Química", List.of(affected, affected, affected, affected), preview.skipped(),
                at(2026, 10, 2, 16, 20, 8)));

        maintenance(Role.DIRECCION_PROGRAMA, true)
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-cancel-by-maintenance-applied.json"), JsonCompareMode.STRICT));
    }

    @Test
    void onlyTheProgramDirectionCancelsForMaintenance() throws Exception {
        maintenance(Role.ESTUDIANTE, true).andExpect(status().isForbidden());
        maintenance(Role.MONITOR, false).andExpect(status().isForbidden());
        verifyNoInteractions(cancelForMaintenance);
    }

    @Test
    void theReasonIsMandatory() throws Exception {
        mockMvc.perform(post("/api/resources/ESP-0220/cancel-reservations").with(session(Role.DIRECCION_PROGRAMA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"  \", \"confirm\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        verifyNoInteractions(cancelForMaintenance);
    }

    @Test
    void anUnknownResourceIsA404() throws Exception {
        when(cancelForMaintenance.preview("ESP-0220")).thenThrow(new ResourceNotFoundException("ESP-0220"));

        maintenance(Role.DIRECCION_PROGRAMA, false).andExpect(status().isNotFound());
    }
}
