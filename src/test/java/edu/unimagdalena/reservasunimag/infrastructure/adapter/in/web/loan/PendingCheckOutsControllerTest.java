package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.loan;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingCheckOut;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReturns;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReviewFilter;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReviews;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryPendingCheckOutsPort;
import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;
import edu.unimagdalena.reservasunimag.support.ContractFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
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
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.ContractFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Las dos listas de pendientes de UC12 Contratos §3 y §4. */
@WebMvcTest(PendingCheckOutsController.class)
class PendingCheckOutsControllerTest {

    private static final ReservationHolder HOLDER = new ReservationHolder(
            UUID.fromString("5f1b9c2d-7a34-4e81-b0f6-3c8d1e9a4b72"), "2019114045", "Nombre del estudiante",
            Role.ESTUDIANTE);

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private QueryPendingCheckOutsPort pending;

    @TestConfiguration
    static class Properties {

        @Bean
        ReservationProperties reservationProperties() {
            return ContractFixtures.properties(URI.create("http://unused"), URI.create("http://unused"),
                    Duration.ofSeconds(1), 100, 5);
        }
    }

    private static Instant at(int month, int day, int hour, int minute) {
        return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, TimeSlot.ZONE).toInstant();
    }

    private static RequestPostProcessor session(Role role) {
        return jwt().jwt(token -> token.subject(HOLDER.userId().toString()).claim("code", "DP-0001")
                .claim("role", role.name()));
    }

    @Test
    void theOverdueLoansComeWithTheirDelayAndWhenTheyWouldBeDeclaredLost() throws Exception {
        Reservation loan = Reservation.confirmed(UUID.fromString("4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3"),
                HOLDER.userId(), "ACT-004512", ResourceCategory.ACTIVO, at(9, 1, 14, 30), at(9, 10, 22, 0),
                at(8, 30, 9, 0), new Loan(7, at(9, 1, 14, 35), null));
        when(pending.pendingReturns(any())).thenReturn(new PendingReturns(
                new Page<>(List.of(new PendingCheckOut(loan, HOLDER, "Libro de Cálculo I")), 1, 20, 1), 0,
                Duration.ofDays(7), at(9, 13, 23, 0)));

        mockMvc.perform(get("/api/loans/pending-returns").with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-pending-returns.json"), JsonCompareMode.STRICT));
    }

    @Test
    void theUnreviewedSpacesArePagedAndTheSummarySeparatesTheirOrigin() throws Exception {
        Reservation space = Reservation.confirmed(UUID.fromString("9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45"),
                HOLDER.userId(), "ESP-0107", ResourceCategory.ESPACIO, at(9, 1, 10, 0), at(9, 1, 12, 0),
                at(8, 30, 9, 0), null);
        when(pending.pendingReviews(any(), any())).thenReturn(new PendingReviews(
                new Page<>(List.of(new PendingCheckOut(space, HOLDER, "Sala de Estudio 3")), 1, 20, 2671), 2104, 567,
                at(9, 3, 13, 0)));

        mockMvc.perform(get("/api/reservations/pending-reviews?from=2026-09-01&to=2026-09-30&origin=ESTUDIANTIL"
                        + "&resourceId=ESP-0107").with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-pending-reviews.json"), JsonCompareMode.STRICT));

        ArgumentCaptor<PendingReviewFilter> filter = ArgumentCaptor.forClass(PendingReviewFilter.class);
        verify(pending).pendingReviews(filter.capture(), any());
        assertThat(filter.getValue()).isEqualTo(new PendingReviewFilter(LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30), ReservationOrigin.ESTUDIANTIL, "ESP-0107"));
    }

    @Test
    void onlyTheProgramDirectionSeesThePendingLists() throws Exception {
        mockMvc.perform(get("/api/loans/pending-returns").with(session(Role.ESTUDIANTE)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reservations/pending-reviews").with(session(Role.MONITOR)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(pending);
    }
}
