package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.resource;

import edu.unimagdalena.reservasunimag.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC1 de punta a punta con el perfil local: sesión real, adaptadores falsos y PostgreSQL de verdad. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class ResourcesFlowIT {

    private static final String QUERY = "/api/resources?date=2026-09-01&start=10:00&end=12:00";

    @Autowired
    private MockMvc mockMvc;

    private Cookie login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"unimag2026\"}".formatted(email)))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("session");
    }

    @Test
    void withoutSessionTheConsultationIsA401Problem() throws Exception {
        mockMvc.perform(get(QUERY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://reservasunimag.unimagdalena.edu.co/errors/unauthenticated"))
                .andExpect(jsonPath("$.instance").value("/api/resources"));
    }

    @Test
    void aStudentSeesTheWholeCatalogOrderedByNameWithoutNotice() throws Exception {
        mockMvc.perform(get(QUERY).cookie(login("estudiante@unimagdalena.edu.co")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.total").value(10))
                .andExpect(jsonPath("$.pagination.pageSize").value(20))
                .andExpect(jsonPath("$.anySelectable").value(true))
                .andExpect(jsonPath("$.resources[0].name").value("Auditorio Principal"))
                .andExpect(jsonPath("$.sanctionNotice").doesNotExist());
    }

    @Test
    void theMaintenanceAssetIsListedButNotSelectable() throws Exception {
        mockMvc.perform(get(QUERY + "&category=ACTIVO&type=MICROSCOPIO").cookie(login("estudiante@unimagdalena.edu.co")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.total").value(2))
                .andExpect(jsonPath("$.resources[?(@.id=='ACT-000912')].status").value("EN_MANTENIMIENTO"))
                .andExpect(jsonPath("$.resources[?(@.id=='ACT-000912')].selectable").value(false))
                .andExpect(jsonPath("$.resources[?(@.id=='ACT-000913')].selectable").value(true));
    }

    @Test
    void theSanctionedMonitorGetsTheNoticeAndTheListAnyway() throws Exception {
        mockMvc.perform(get(QUERY).cookie(login("monitor@unimagdalena.edu.co")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sanctionNotice.type").value("ACTIVE_SANCTION"))
                .andExpect(jsonPath("$.resources.length()").value(10));
    }

    @Test
    void theProgramDirectionCanConsultButGetsNoNotice() throws Exception {
        mockMvc.perform(get(QUERY).cookie(login("direccion@unimagdalena.edu.co")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sanctionNotice").doesNotExist());
    }

    @Test
    void anInvalidSlotIsRejectedBeforeAnythingIsQueried() throws Exception {
        mockMvc.perform(get("/api/resources?date=2026-09-01&start=05:00&end=07:00")
                        .cookie(login("estudiante@unimagdalena.edu.co")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TIME_SLOT_OUTSIDE_WINDOW"));
    }
}
