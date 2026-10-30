package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.compliance;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckFilter;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckOutcome;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckPage;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckPurpose;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionScope;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.QuerySanctionChecksPort;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La auditoría de UC6 Contratos §3: solo Dirección de Programa y, con personCode, siempre con un rango acotado. */
@WebMvcTest(SanctionCheckController.class)
class SanctionCheckControllerTest {

    private static final String PERSON = "2019114045";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private QuerySanctionChecksPort queryChecks;

    @TestConfiguration
    static class Properties {

        @Bean
        ReservationProperties reservationProperties() {
            return ContractFixtures.properties(URI.create("http://unused"), URI.create("http://unused"),
                    Duration.ofSeconds(1), 100, 5);
        }
    }

    private static Instant at(int hour, int minute, int second) {
        return ZonedDateTime.of(2026, 9, 3, hour, minute, second, 0, TimeSlot.ZONE).toInstant();
    }

    private static RequestPostProcessor session(Role role) {
        return jwt().jwt(token -> token.subject("00000000-0000-0000-0000-000000000003").claim("code", "DP-0001")
                .claim("role", role.name()));
    }

    @Test
    void theListCarriesWhatModule3AnsweredOnlyWhenItAnswered() throws Exception {
        var sanctioned = new SanctionCheck(UUID.fromString("e4a9c712-8b35-4d06-91fe-2c7d0b8a3f54"), PERSON,
                SanctionCheckPurpose.RESERVE_RESOURCES, ResourceCategory.ESPACIO, at(9, 14, 22),
                SanctionCheckOutcome.SANCTIONED, new SanctionCheck.Answered(
                "Dos ausencias no justificadas en el semestre", LocalDate.of(2026, 9, 15), SanctionScope.ESPACIOS,
                false, 2, 1), 312, null);
        var clear = new SanctionCheck(UUID.fromString("3b6d1f80-52c4-4e7a-9d18-a0f4c2e7b915"), PERSON,
                SanctionCheckPurpose.SEARCH_RESOURCES, null, at(9, 14, 10), SanctionCheckOutcome.CLEAR,
                new SanctionCheck.Answered(null, null, null, false, 2, 1), 280, null);
        var notVerified = new SanctionCheck(UUID.fromString("a7f2e508-3c91-4b47-86da-0e5b1d9c2f68"), PERSON,
                SanctionCheckPurpose.SEARCH_RESOURCES, null, at(9, 14, 5), SanctionCheckOutcome.NOT_VERIFIED, null,
                1500, "Connection refused");
        when(queryChecks.list(any(), any())).thenReturn(new SanctionCheckPage(
                new Page<>(List.of(sanctioned, clear, notVerified), 1, 20, 3),
                new SanctionCheckPage.Summary(3, 1, 1, 0, 1)));

        mockMvc.perform(get("/api/sanction-checks?personCode=" + PERSON + "&from=2026-09-01&to=2026-09-30&page=1")
                        .with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json(read("api-sanction-checks.json"), JsonCompareMode.STRICT));

        ArgumentCaptor<SanctionCheckFilter> filter = ArgumentCaptor.forClass(SanctionCheckFilter.class);
        verify(queryChecks).list(filter.capture(), any());
        assertThat(filter.getValue()).isEqualTo(new SanctionCheckFilter(PERSON, null, LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30)));
    }

    @Test
    void withoutPersonTheRangeIsOptional() throws Exception {
        when(queryChecks.list(any(), any())).thenReturn(new SanctionCheckPage(new Page<>(List.of(), 1, 20, 0),
                new SanctionCheckPage.Summary(0, 0, 0, 0, 0)));

        mockMvc.perform(get("/api/sanction-checks?outcome=NOT_VERIFIED").with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.notVerified").value(0));
    }

    @Test
    void onlyTheProgramDirectionAudits() throws Exception {
        for (Role role : new Role[]{Role.ESTUDIANTE, Role.MONITOR}) {
            mockMvc.perform(get("/api/sanction-checks").with(session(role)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(queryChecks);
    }

    @Test
    void aPersonWithoutRangeIsA400BecauseItWouldBeTheirWholeRecord() throws Exception {
        mockMvc.perform(get("/api/sanction-checks?personCode=" + PERSON).with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"));
        mockMvc.perform(get("/api/sanction-checks?personCode=" + PERSON + "&from=2026-09-01")
                        .with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"));
        verifyNoInteractions(queryChecks);
    }

    @Test
    void aPersonWithARangeLongerThanASemesterIsA400() throws Exception {
        mockMvc.perform(get("/api/sanction-checks?personCode=" + PERSON + "&from=2026-01-01&to=2026-07-02")
                        .with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"));
        verifyNoInteractions(queryChecks);
    }

    @Test
    void anUnknownOutcomeIsA400() throws Exception {
        mockMvc.perform(get("/api/sanction-checks?outcome=SANCIONADO").with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("outcome"));
    }
}
