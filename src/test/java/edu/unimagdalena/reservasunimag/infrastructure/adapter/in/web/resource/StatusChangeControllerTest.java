package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.resource;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.DisplayedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.NotifiedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceHistory;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChange;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeFilter;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryStatusChangesPort;
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

/** {@code GET /api/resources/{id}/status-changes} contra el fixture de UC7 Contratos §2. */
@WebMvcTest(StatusChangeController.class)
class StatusChangeControllerTest {

    private static final UUID RESERVATION = UUID.fromString("9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private QueryStatusChangesPort statusChanges;

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
        return jwt().jwt(token -> token.subject(UUID.randomUUID().toString()).claim("code", "DP-0001")
                .claim("role", role.name()));
    }

    private static StatusChange change(String id, DisplayedStatus previous, DisplayedStatus next,
                                       StatusChangeReason reason, Instant at, NotifiedStatus notified, UUID outbox) {
        return new StatusChange(UUID.fromString(id), "ESP-0107", ResourceCategory.ESPACIO, RESERVATION,
                at(9, 1, 10, 0, 0), at(9, 1, 12, 0, 0), previous, next, reason, at, notified, outbox);
    }

    @Test
    void theHistoryTellsTheStoryWithTheNoteOfWhatIsNotOurs() throws Exception {
        var history = List.of(
                change("b8f3c107-4d92-4a56-9e01-2c7d8b5f3a64", DisplayedStatus.DISPONIBLE, DisplayedStatus.RESERVADO,
                        StatusChangeReason.RESERVATION_CONFIRMED, at(8, 30, 9, 14, 22), NotifiedStatus.NOT_SENT, null),
                change("e4c7b019-2f68-4a35-91db-7a0e5c3f2b91", DisplayedStatus.RESERVADO, DisplayedStatus.EN_USO,
                        StatusChangeReason.USE_STARTED, at(9, 1, 10, 3, 51), NotifiedStatus.SENT,
                        UUID.fromString("c9b2f536-8a41-4d07-95ec-3f1a0d7b2e58")));
        when(statusChanges.history(eq("ESP-0107"), any(), any()))
                .thenReturn(new ResourceHistory("ESP-0107", "Sala de Estudio 3", new Page<>(history, 1, 20, 2)));

        mockMvc.perform(get("/api/resources/ESP-0107/status-changes").with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-status-changes.json"), JsonCompareMode.STRICT));
    }

    @Test
    void theReasonFilterReachesTheQuery() throws Exception {
        when(statusChanges.history(eq("ESP-0107"), any(), any()))
                .thenReturn(new ResourceHistory("ESP-0107", null, new Page<>(List.of(), 1, 20, 0)));

        mockMvc.perform(get("/api/resources/ESP-0107/status-changes?reason=USE_STARTED")
                        .with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceName").doesNotExist())
                .andExpect(jsonPath("$.note").exists());

        ArgumentCaptor<StatusChangeFilter> filter = ArgumentCaptor.forClass(StatusChangeFilter.class);
        verify(statusChanges).history(eq("ESP-0107"), filter.capture(), any());
        assertThat(filter.getValue().reason()).isEqualTo(StatusChangeReason.USE_STARTED);
    }

    @Test
    void onlyTheProgramDirectionAuditsAndAnUnknownResourceIsA404() throws Exception {
        mockMvc.perform(get("/api/resources/ESP-0107/status-changes").with(session(Role.ESTUDIANTE)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(statusChanges);

        when(statusChanges.history(eq("NO-EXISTE"), any(), any())).thenThrow(new ResourceNotFoundException("NO-EXISTE"));
        mockMvc.perform(get("/api/resources/NO-EXISTE/status-changes").with(session(Role.DIRECCION_PROGRAMA)))
                .andExpect(status().isNotFound());
    }
}
