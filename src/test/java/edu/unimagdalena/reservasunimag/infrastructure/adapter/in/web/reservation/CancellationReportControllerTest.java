package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReportFilter;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReportPage;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Delivery;
import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryCancellationReportsPort;
import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import edu.unimagdalena.reservasunimag.support.ContractFixtures;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Los dos endpoints de auditoría de UC11 Contratos §2 y §3. */
@WebMvcTest(CancellationReportController.class)
class CancellationReportControllerTest {

    private static final UUID RESERVATION = UUID.fromString("9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45");
    private static final UUID EVENT = UUID.fromString("d4c9e2f1-7a83-4b60-91c5-2e8d0f3a1b57");
    private static final ReservationHolder HOLDER = new ReservationHolder(
            UUID.fromString("00000000-0000-0000-0000-000000000001"), "2019114045", "Nombre del estudiante",
            Role.ESTUDIANTE);

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private QueryCancellationReportsPort queryReports;

    @TestConfiguration
    static class Properties {

        @Bean
        ReservationProperties reservationProperties() {
            return ContractFixtures.properties(URI.create("http://unused"), URI.create("http://unused"),
                    Duration.ofSeconds(1), 100, 5);
        }
    }

    private static Instant at(int year, int month, int day, int hour, int minute, int second) {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, TimeSlot.ZONE).toInstant();
    }

    private static RequestPostProcessor session(Role role) {
        return jwt().jwt(token -> token.subject(HOLDER.userId().toString()).claim("code", "2019114045")
                .claim("role", role.name()));
    }

    private static CancellationReport report(Delivery delivery) {
        return new CancellationReport(RESERVATION, EVENT, CancellationOrigin.HOLDER, CancellationReport.HOLDER_REASON,
                1486, at(2026, 8, 31, 9, 14, 22), "ESP-0107", "Sala de Estudio 3", ResourceCategory.ESPACIO, HOLDER,
                at(2026, 9, 1, 10, 0, 0), at(2026, 9, 1, 12, 0, 0), delivery);
    }

    @Test
    void aSentReportCarriesItsDeliveryWithAnExplicitNullError() throws Exception {
        when(queryReports.find(any(), eq(RESERVATION))).thenReturn(report(new Delivery(
                DeliveryStatus.SENT, 2, at(2026, 8, 31, 9, 14, 27), at(2026, 8, 31, 9, 14, 58), null, null)));

        mockMvc.perform(get("/api/reservations/" + RESERVATION + "/cancellation-report")
                        .with(session(Role.ESTUDIANTE)))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-cancellation-report-sent.json"), JsonCompareMode.STRICT))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"lastError\":null")));
    }

    @Test
    void aPendingReportSaysWhenItWillBeRetriedAndWhy() throws Exception {
        when(queryReports.find(any(), eq(RESERVATION))).thenReturn(report(new Delivery(
                DeliveryStatus.PENDING, 3, at(2026, 8, 31, 9, 14, 27), null, at(2026, 8, 31, 9, 16, 27),
                "Connection refused")));

        mockMvc.perform(get("/api/reservations/" + RESERVATION + "/cancellation-report")
                        .with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-cancellation-report-pending.json"), JsonCompareMode.STRICT));
    }

    @Test
    void notCancelledOrSomeoneElsesIsTheSame404() throws Exception {
        when(queryReports.find(any(), eq(RESERVATION))).thenThrow(new ReservationNotFoundException(RESERVATION));

        mockMvc.perform(get("/api/reservations/" + RESERVATION + "/cancellation-report")
                        .with(session(Role.ESTUDIANTE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La reserva no existe."));
    }

    @Test
    void theListIsFilteredAndItsSummaryCountsTheWholeFilter() throws Exception {
        var academic = new CancellationReport(UUID.fromString("a1b2c3d4-5e6f-4789-9abc-def012345678"),
                UUID.fromString("f2b8c4d1-6e39-4a70-95c8-3d1e7a0f2b56"), CancellationOrigin.ACADEMIC_PRIORITY,
                "Clase IS-510.", null, at(2026, 9, 10, 15, 47, 35), "ESP-0301", "Auditorio Menor",
                ResourceCategory.ESPACIO, HOLDER, at(2026, 9, 10, 14, 0, 0), at(2026, 9, 10, 16, 0, 0),
                new Delivery(DeliveryStatus.FAILED, 10, at(2026, 9, 10, 15, 47, 40), null, null,
                        "Connection refused"));
        when(queryReports.list(any(), any())).thenReturn(new CancellationReportPage(
                new Page<>(List.of(academic), 1, 20, 1), new CancellationReportPage.Summary(1, 0, 0, 1)));

        mockMvc.perform(get("/api/cancellation-reports?origin=ACADEMIC_PRIORITY&delivery=FAILED"
                        + "&from=2026-09-01&to=2026-09-30&page=1").with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-cancellation-reports-list.json"), JsonCompareMode.STRICT));

        ArgumentCaptor<CancellationReportFilter> filter = ArgumentCaptor.forClass(CancellationReportFilter.class);
        verify(queryReports).list(filter.capture(), any());
        assertThat(filter.getValue()).isEqualTo(new CancellationReportFilter(CancellationOrigin.ACADEMIC_PRIORITY,
                DeliveryStatus.FAILED, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
    }

    @Test
    void onlyTheProgramDirectionAuditsTheList() throws Exception {
        mockMvc.perform(get("/api/cancellation-reports").with(session(Role.ESTUDIANTE)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(queryReports);
    }

    @Test
    void anUnknownFilterValueIsA400() throws Exception {
        mockMvc.perform(get("/api/cancellation-reports?origin=CUALQUIERA").with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("origin"));
    }
}
