package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;

/**
 * Arma los {@link ProblemDetail} (RFC 9457) con el {@code type} común del módulo. Lo usan el manejador global y
 * los filtros de Spring Security, que responden antes de llegar a un controlador.
 */
@Component
public class ProblemResponses {

    private static final String TYPE_BASE = "https://reservasunimag.unimagdalena.edu.co/errors/";

    private final ObjectMapper objectMapper;

    public ProblemResponses(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static ProblemDetail problem(HttpStatus status, String slug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + slug));
        problem.setTitle(title);
        return problem;
    }

    public void unauthenticated(HttpServletRequest request, HttpServletResponse response,
                                AuthenticationException exception) throws IOException {
        write(request, response, problem(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sesión requerida",
                "Inicia sesión para continuar."));
    }

    public void forbidden(HttpServletRequest request, HttpServletResponse response,
                          AccessDeniedException exception) throws IOException {
        write(request, response, problem(HttpStatus.FORBIDDEN, "forbidden", "Acción no permitida",
                "Tu rol no permite esta acción."));
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ProblemDetail problem)
            throws IOException {
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
