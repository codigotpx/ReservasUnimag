package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.auth;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login mínimo de punta a punta, con los usuarios sembrados del perfil local (UC1 Contratos §4). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class AuthFlowIT {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String ERRORS = "https://reservasunimag.unimagdalena.edu.co/errors/";

    @Autowired
    private MockMvc mockMvc;

    private Cookie login(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, password)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andReturn();
        Cookie session = result.getResponse().getCookie("session");
        assertThat(session).isNotNull();
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(session.getMaxAge()).isEqualTo(8 * 60 * 60);
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("SameSite=Lax");
        return session;
    }

    @Test
    void loginSetsTheSessionCookieAndMeReturnsTheProfile() throws Exception {
        Cookie session = login("estudiante@unimagdalena.edu.co", "unimag2026");

        mockMvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$.code").value("2021114001"))
                .andExpect(jsonPath("$.name").value("Estudiante de Prueba"))
                .andExpect(jsonPath("$.role").value("ESTUDIANTE"));
    }

    @Test
    void theMonitorKeepsItsOwnRole() throws Exception {
        Cookie session = login("monitor@unimagdalena.edu.co", "unimag2026");

        mockMvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MONITOR"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameProblem() throws Exception {
        for (String body : new String[]{
                "{\"email\": \"estudiante@unimagdalena.edu.co\", \"password\": \"mala\"}",
                "{\"email\": \"nadie@unimagdalena.edu.co\", \"password\": \"unimag2026\"}"}) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value(ERRORS + "invalid-credentials"))
                    .andExpect(jsonPath("$.detail").value("El correo o la contraseña no son correctos."));
        }
    }

    @Test
    void malformedLoginIsAnInvalidParametersProblem() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"no-es-correo\", \"password\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(ERRORS + "invalid-parameters"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void withoutSessionTheApiAnswersUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(ERRORS + "unauthenticated"))
                .andExpect(jsonPath("$.instance").value("/api/auth/me"));
    }

    @Test
    void aTamperedTokenIsRejected() throws Exception {
        Cookie session = login("estudiante@unimagdalena.edu.co", "unimag2026");
        String[] parts = session.getValue().split("\\.");
        Cookie tampered = new Cookie("session", parts[0] + "." + parts[1] + "x." + parts[2]);

        mockMvc.perform(get("/api/auth/me").cookie(tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(ERRORS + "unauthenticated"));
    }

    @Test
    void logoutClearsTheCookie() throws Exception {
        var response = mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        assertThat(response.getCookie("session")).isNotNull();
        assertThat(response.getCookie("session").getMaxAge()).isZero();
    }

    @Test
    void registerWithValidDataCreatesTheUserAndTheLoginStartsTheSession() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "2022114003", "name": "Estudiante Nuevo", "email": "nuevo@unimagdalena.edu.co", "password": "unimag2026"}"""))
                .andExpect(status().isCreated());

        Cookie session = login("nuevo@unimagdalena.edu.co", "unimag2026");

        mockMvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2022114003"))
                .andExpect(jsonPath("$.name").value("Estudiante Nuevo"))
                .andExpect(jsonPath("$.role").value("ESTUDIANTE"));
    }

    @Test
    void registerWithAnEmailThatAlreadyExistsAnswersConflict() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "2022999999", "name": "Otra Persona", "email": "estudiante@unimagdalena.edu.co", "password": "unimag2026"}"""))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(ERRORS + "user-already-registered"));
    }

    @Test
    void registerWithAnInvalidCodeAnswersBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "12345", "name": "Persona Invalida", "email": "invalido@unimagdalena.edu.co", "password": "unimag2026"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(ERRORS + "invalid-parameters"));
    }
}
