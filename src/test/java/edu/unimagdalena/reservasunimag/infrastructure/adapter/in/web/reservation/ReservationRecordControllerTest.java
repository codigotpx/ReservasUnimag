package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Delivery;
import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordFilter;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordPage;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryReservationRecordsPort;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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

/** Los dos endpoints de auditoría de UC10 Contratos §2 y §3. */
@WebMvcTest(ReservationRecordController.class)
class ReservationRecordControllerTest {

    private static final UUID LOAN = UUID.fromString("4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3");
    private static final UUID BLOCK = UUID.fromString("5d2f8a41-7b63-4e90-a1c4-8f3e2b6d9c07");
    private static final ReservationHolder HOLDER = new ReservationHolder(
            UUID.fromString("00000000-0000-0000-0000-000000000001"), "2019114045", "Nombre del estudiante",
            Role.ESTUDIANTE);
    private static final AcademicData NETWORKS = new AcademicData("IS-402", "Redes de Computadores", "1",
            "Ingeniería de Sistemas", "Nombre del docente");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private QueryReservationRecordsPort queryRecords;

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
        return jwt().jwt(token -> token.subject(HOLDER.userId().toString()).claim("code", "2019114045")
                .claim("role", role.name()));
    }

    private static ReservationRecordReport academic(Delivery delivery) {
        return new ReservationRecordReport(BLOCK, UUID.fromString("8d41b6c0-2f93-4a7e-8c15-9b0e3d7f2a68"),
                ReservationOrigin.ACADEMICO, "ESP-0412", "Laboratorio de Redes", ResourceCategory.ESPACIO, null,
                NETWORKS, at(9, 7, 8, 0, 0), at(9, 7, 10, 0, 0), null, at(8, 30, 9, 14, 22), delivery);
    }

    @Test
    void anAssetRecordCarriesItsLoanAndItsDeliveryWithAnExplicitNullError() throws Exception {
        when(queryRecords.find(any(), eq(LOAN))).thenReturn(new ReservationRecordReport(LOAN,
                UUID.fromString("c3a7f1d2-4e58-4b09-9a61-7d2e8f0b5c34"), ReservationOrigin.ESTUDIANTIL,
                "ACT-000912", "Microscopio M-014", ResourceCategory.ACTIVO, HOLDER, null, at(9, 1, 14, 30, 0),
                at(9, 10, 22, 0, 0), 7, at(8, 30, 9, 14, 22), new Delivery(DeliveryStatus.SENT, 1,
                at(8, 30, 9, 14, 24), at(8, 30, 9, 14, 24), null, null)));

        mockMvc.perform(get("/api/reservations/" + LOAN + "/record").with(session(Role.ESTUDIANTE)))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-reservation-record-asset.json"), JsonCompareMode.STRICT))
                .andExpect(content().string(containsString("\"lastError\":null")));
    }

    @Test
    void anAcademicRecordHasItsClassButNotTheInstructor() throws Exception {
        when(queryRecords.find(any(), eq(BLOCK))).thenReturn(academic(new Delivery(DeliveryStatus.PENDING, 2,
                at(8, 30, 9, 14, 25), null, at(8, 30, 9, 15, 25), "Connection refused")));

        mockMvc.perform(get("/api/reservations/" + BLOCK + "/record").with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-reservation-record-academic.json"), JsonCompareMode.STRICT))
                .andExpect(content().string(not(containsString("Nombre del docente"))));
    }

    @Test
    void withoutRecordOrSomeoneElsesIsTheSame404() throws Exception {
        when(queryRecords.find(any(), eq(LOAN))).thenThrow(new ReservationNotFoundException(LOAN));

        mockMvc.perform(get("/api/reservations/" + LOAN + "/record").with(session(Role.ESTUDIANTE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La reserva no existe."));
    }

    @Test
    void theListIsFilteredAndItsSummaryCountsTheSanctionable() throws Exception {
        when(queryRecords.list(any(), any())).thenReturn(new ReservationRecordPage(
                new Page<>(List.of(academic(new Delivery(DeliveryStatus.FAILED, 10, at(8, 30, 9, 14, 25), null,
                        null, "Connection refused"))), 1, 20, 1),
                new ReservationRecordPage.Summary(1, 0, 0, 1, 0)));

        mockMvc.perform(get("/api/reservation-records?delivery=FAILED&origin=ACADEMICO&from=2026-08-10"
                        + "&to=2026-12-05&page=1").with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-reservation-records-list.json"), JsonCompareMode.STRICT));

        ArgumentCaptor<ReservationRecordFilter> filter = ArgumentCaptor.forClass(ReservationRecordFilter.class);
        verify(queryRecords).list(filter.capture(), any());
        assertThat(filter.getValue()).isEqualTo(new ReservationRecordFilter(ReservationOrigin.ACADEMICO,
                DeliveryStatus.FAILED, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 12, 5)));
    }

    @Test
    void onlyTheProgramDirectionAuditsTheList() throws Exception {
        mockMvc.perform(get("/api/reservation-records").with(session(Role.ESTUDIANTE)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(queryRecords);
    }

    @Test
    void anUnknownFilterValueIsA400() throws Exception {
        mockMvc.perform(get("/api/reservation-records?origin=CUALQUIERA").with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("origin"));
    }
}
