package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.resource;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionNotice;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException.ExternalModule;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.DisplayedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceFilter;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceWithStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.SearchResourcesQuery;
import edu.unimagdalena.reservasunimag.domain.model.resource.SearchResourcesResult;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.SearchResourcesPort;
import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.ContractFixtures.read;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.NOW;
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

/** La forma exacta de {@code GET /api/resources} contra los fixtures de UC1 Contratos §1. */
@WebMvcTest(ResourceController.class)
@EnableConfigurationProperties(ReservationProperties.class)
@TestPropertySource(properties = {
        "reservations.page-size=20",
        "reservations.next-slot-step=30m",
        "reservations.resource-types=LABORATORIO",
        "reservations.module1.base-url=http://localhost:1",
        "reservations.module1.timeout=1s",
        "reservations.module1.page-size=100",
        "reservations.module1.max-pages=5",
        "reservations.module3.base-url=http://localhost:2",
        "reservations.module3.timeout=1s",
        "reservations.jwt.secret=0123456789abcdef0123456789abcdef",
        "reservations.jwt.issuer=test",
        "reservations.jwt.ttl=8h",
        "reservations.jwt.cookie-name=session",
        "reservations.max-quota=3",
        "reservations.max-space-duration=2h",
        "reservations.min-cancellation-notice=10m",
        "reservations.no-show-grace=10m",
        "reservations.loan-loss-threshold=7d",
        "reservations.holidays=",
        "reservations.kafka.topic.record=module2.reservation.record.v1",
        "reservations.kafka.topic.cancellation=module2.reservation.cancellation.v1",
        "reservations.kafka.topic.no-show=module3.reservation.no-show.v1",
        "reservations.kafka.topic.no-show-ack=module2.reservation.no-show-ack.v1",
        "reservations.kafka.topic.check-out=module3.reservation.check-out.v1",
        "reservations.kafka.topic.check-out-ack=module2.reservation.check-out-ack.v1",
        "reservations.kafka.topic.resource-status=module2.resource.status.v1",
        "reservations.outbox.enabled=false",
        "reservations.outbox.interval=5s",
        "reservations.outbox.batch=50",
        "reservations.outbox.max-attempts=10",
        "reservations.outbox.alert-after=25m",
        "reservations.upload.validity=30m"})
class ResourceControllerTest {

    private static final String QUERY = "/api/resources?date=2026-09-01&start=10:00&end=12:00";
    private static final TimeSlot SLOT = new TimeSlot(LocalDate.of(2026, 9, 1), LocalTime.of(10, 0),
            LocalTime.of(12, 0));
    /** El contrato fija 09:14:22; la hora de las pruebas de dominio es 09:14:00. */
    private static final java.time.Instant QUERIED_AT = NOW.plusSeconds(22);
    private static final String ERRORS = "https://reservasunimag.unimagdalena.edu.co/errors/";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchResourcesPort searchResources;

    private static RequestPostProcessor session(Role role) {
        return jwt().jwt(token -> token
                .subject(UUID.fromString("00000000-0000-0000-0000-000000000001").toString())
                .claim("code", "2021114001")
                .claim("role", role.name()));
    }

    private static ResourceWithStatus withStatus(Resource resource, DisplayedStatus status) {
        return new ResourceWithStatus(resource, status);
    }

    private static final Resource LAB = Resource.space("ESP-0412", "Laboratorio de Redes", "LABORATORIO",
            "Bloque 7, piso 2", edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus.DISPONIBLE, "Ingeniería", 30,
            List.of("PROYECTOR", "AIRE_ACONDICIONADO"));
    private static final Resource MICROSCOPE = Resource.asset("ACT-000912", "Microscopio M-014", "MICROSCOPIO",
            "Laboratorio de Biología", edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus.EN_MANTENIMIENTO, "BUENO", 3);
    private static final Resource STUDY_ROOM = Resource.space("ESP-0107", "Sala de Estudio 3", "SALA_DE_ESTUDIO",
            "Biblioteca, piso 1", edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus.DISPONIBLE, "Biblioteca Central", 8,
            List.of("AIRE_ACONDICIONADO"));

    @Test
    void theNormalResponseMatchesTheContractExactly() throws Exception {
        var page = new Page<>(List.of(
                withStatus(LAB, DisplayedStatus.BLOQUEO_ACADEMICO),
                withStatus(MICROSCOPE, DisplayedStatus.EN_MANTENIMIENTO),
                withStatus(STUDY_ROOM, DisplayedStatus.DISPONIBLE)), 1, 20, 47);
        when(searchResources.search(any())).thenReturn(new SearchResourcesResult(SLOT,
                new ResourceFilter(null, null, null), page, true, false, QUERIED_AT, null, null, null));

        mockMvc.perform(get(QUERY).with(session(Role.ESTUDIANTE)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json(read("api-resources-response.json"), JsonCompareMode.STRICT));
    }

    @Test
    void theNoneSelectableResponseWithNextSlotAndSanctionNoticeMatchesTheContract() throws Exception {
        var notice = new SanctionNotice(SanctionNotice.Type.ACTIVE_SANCTION,
                "Dos ausencias no justificadas en el semestre", LocalDate.of(2026, 9, 30),
                "Tienes una sanción vigente hasta el 2026-09-30. Puedes consultar los recursos, pero no podrás confirmar una reserva.");
        var lab = new Resource("ESP-0412", "Laboratorio de Redes", ResourceCategory.ESPACIO, "LABORATORIO",
                "Bloque 7, piso 2", edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus.DISPONIBLE, "Ingeniería", 30,
                List.of("PROYECTOR"), null, null);
        var page = new Page<>(List.of(withStatus(lab, DisplayedStatus.BLOQUEO_ACADEMICO)), 1, 20, 1);
        when(searchResources.search(any())).thenReturn(new SearchResourcesResult(SLOT,
                new ResourceFilter(ResourceCategory.ESPACIO, "LABORATORIO", 20), page, false, false, QUERIED_AT,
                "El único recurso que cumple los filtros no está disponible entre las 10:00 y las 12:00.",
                new TimeSlot(LocalDate.of(2026, 9, 1), LocalTime.of(14, 0), LocalTime.of(16, 0)), notice));

        mockMvc.perform(get(QUERY + "&category=ESPACIO&type=LABORATORIO&minCapacity=20")
                        .with(session(Role.ESTUDIANTE)))
                .andExpect(status().isOk())
                .andExpect(content().json(read("api-resources-none-selectable.json"), JsonCompareMode.STRICT));
    }

    @Test
    void theNotVerifiedNoticeOmitsReasonAndUntil() throws Exception {
        var page = new Page<ResourceWithStatus>(List.of(), 1, 20, 0);
        when(searchResources.search(any())).thenReturn(new SearchResourcesResult(SLOT,
                new ResourceFilter(null, null, null), page, false, false, QUERIED_AT, "Ningún recurso cumple los filtros.",
                null, SanctionNotice.notVerified()));

        mockMvc.perform(get(QUERY).with(session(Role.MONITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sanctionNotice.type").value("NOT_VERIFIED"))
                .andExpect(jsonPath("$.sanctionNotice.reason").doesNotExist())
                .andExpect(jsonPath("$.sanctionNotice.until").doesNotExist())
                .andExpect(jsonPath("$.nextTimeSlot").doesNotExist())
                .andExpect(jsonPath("$.resources").isEmpty());
    }

    @Test
    void noOptionalFieldEverTravelsAsNull() throws Exception {
        var page = new Page<>(List.of(withStatus(MICROSCOPE, DisplayedStatus.DISPONIBLE),
                withStatus(STUDY_ROOM, DisplayedStatus.DISPONIBLE)), 1, 20, 2);
        when(searchResources.search(any())).thenReturn(new SearchResourcesResult(SLOT,
                new ResourceFilter(null, null, null), page, true, false, QUERIED_AT, null, null, null));

        String body = mockMvc.perform(get(QUERY).with(session(Role.ESTUDIANTE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Los únicos null permitidos son los de "filters": no filtré.
        assertThat(body.replace("\"filters\":{\"category\":null,\"type\":null,\"minCapacity\":null}", ""))
                .doesNotContain("null");
        assertThat(body).doesNotContain("\"reason\"").doesNotContain("holder").doesNotContain("userId");
    }

    @Test
    void buildsTheQueryFromTheSessionAndTheParametersAndNeverFromTheBody() throws Exception {
        when(searchResources.search(any())).thenReturn(new SearchResourcesResult(SLOT,
                new ResourceFilter(null, null, null), new Page<>(List.of(), 1, 20, 0), false, false, QUERIED_AT,
                "x", null, null));

        mockMvc.perform(get(QUERY + "&category=ESPACIO&type=SALON&minCapacity=15&page=2")
                .with(session(Role.MONITOR))).andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(SearchResourcesQuery.class);
        verify(searchResources).search(captor.capture());
        SearchResourcesQuery query = captor.getValue();
        assertThat(query.user()).isEqualTo(new CurrentUser(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), "2021114001", Role.MONITOR));
        assertThat(query.slot()).isEqualTo(SLOT);
        assertThat(query.filter()).isEqualTo(new ResourceFilter(ResourceCategory.ESPACIO, "SALON", 15));
        assertThat(query.page().page()).isEqualTo(2);
        assertThat(query.page().size()).isEqualTo(20);
    }

    @Test
    void anInvalidSlotIsA400WithItsCode() throws Exception {
        for (String[] bad : new String[][]{
                {"05:00", "07:00", "TIME_SLOT_OUTSIDE_WINDOW"},
                {"21:00", "01:00", "TIME_SLOT_CROSSES_MIDNIGHT"},
                {"12:00", "10:00", "TIME_SLOT_END_NOT_AFTER_START"}}) {
            mockMvc.perform(get("/api/resources?date=2026-09-01&start=" + bad[0] + "&end=" + bad[1])
                            .with(session(Role.ESTUDIANTE)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.type").value(ERRORS + "invalid-time-slot"))
                    .andExpect(jsonPath("$.code").value(bad[2]))
                    .andExpect(jsonPath("$.instance").value("/api/resources"))
                    .andExpect(jsonPath("$.timeSlot.date").value("2026-09-01"))
                    .andExpect(jsonPath("$.timeSlot.start").value(bad[0]))
                    .andExpect(jsonPath("$.timeSlot.end").value(bad[1]));
        }
        verifyNoInteractions(searchResources);
    }

    @Test
    void minCapacityWithAnAssetCategoryIsA400OfParameters() throws Exception {
        mockMvc.perform(get(QUERY + "&category=ACTIVO&minCapacity=10").with(session(Role.ESTUDIANTE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(ERRORS + "invalid-parameters"))
                .andExpect(jsonPath("$.errors[0].field").value("minCapacity"))
                .andExpect(jsonPath("$.errors[0].message").value("solo aplica cuando la categoría es ESPACIO"));
    }

    @Test
    void pageZeroIsA400OfParameters() throws Exception {
        mockMvc.perform(get(QUERY + "&page=0").with(session(Role.ESTUDIANTE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(ERRORS + "invalid-parameters"))
                .andExpect(jsonPath("$.errors[0].field").value("page"))
                .andExpect(jsonPath("$.errors[0].message").value("debe ser mayor o igual a 1"));
    }

    @Test
    void missingOrMalformedParametersAreA400OfParameters() throws Exception {
        mockMvc.perform(get("/api/resources?start=10:00&end=12:00").with(session(Role.ESTUDIANTE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(ERRORS + "invalid-parameters"))
                .andExpect(jsonPath("$.errors[0].field").value("date"));
        mockMvc.perform(get("/api/resources?date=01-09-2026&start=10:00&end=12:00").with(session(Role.ESTUDIANTE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("date"));
        mockMvc.perform(get(QUERY + "&category=OTRA").with(session(Role.ESTUDIANTE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("category"));
    }

    @Test
    void inventoryDownIsA503WithTheContractBody() throws Exception {
        when(searchResources.search(any())).thenThrow(
                new ExternalServiceUnavailableException(ExternalModule.MODULO_1, "caído", null));

        mockMvc.perform(get(QUERY).with(session(Role.ESTUDIANTE)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(content().json(read("api-resources-503.json"), JsonCompareMode.STRICT));
    }
}
