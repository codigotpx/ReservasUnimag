package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.error;

import edu.unimagdalena.reservasunimag.domain.model.error.DurationExceededException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.InvalidCredentialsException;
import edu.unimagdalena.reservasunimag.domain.model.error.InvalidTimeSlotException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationDeniedException;
import edu.unimagdalena.reservasunimag.domain.model.error.UserAlreadyRegisteredException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.error.ProblemResponses.problem;

/**
 * Traduce las excepciones del dominio y de validación a {@link ProblemDetail}. Extiende
 * {@link ResponseEntityExceptionHandler} para que las del propio Spring MVC (405, 415, cuerpo ilegible…) también
 * salgan como {@code ProblemDetail}, y para reemplazar el manejador por defecto de Spring Boot.
 */
@RestControllerAdvice
class GlobalErrorHandler extends ResponseEntityExceptionHandler {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    @ExceptionHandler(InvalidTimeSlotException.class)
    ProblemDetail invalidTimeSlot(InvalidTimeSlotException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "invalid-time-slot", "Franja horaria inválida",
                "La franja debe estar entre las 06:00 y las 22:00 del mismo día, y terminar después de empezar.");
        problem.setProperty("code", exception.reason().name());
        problem.setProperty("timeSlot", Map.of(
                "date", exception.date().toString(),
                "start", exception.start().format(HOUR),
                "end", exception.end().format(HOUR)));
        return problem;
    }

    @ExceptionHandler(DurationExceededException.class)
    ProblemDetail durationExceeded(DurationExceededException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "invalid-time-slot", "Franja horaria inválida",
                "Una reserva de espacio puede durar como máximo 2 horas. Ajusta la franja.");
        problem.setProperty("code", "DURATION_EXCEEDED");
        problem.setProperty("limitMinutes", exception.limitMinutes());
        problem.setProperty("requestedMinutes", exception.requestedMinutes());
        return problem;
    }

    @ExceptionHandler(ReservationDeniedException.class)
    ProblemDetail reservationDenied(ReservationDeniedException exception) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "reservation-denied", "Reservation denegada",
                exception.detail());
        problem.setProperty("code", exception.denialCode().code());
        return problem;
    }

    @ExceptionHandler(ExternalServiceUnavailableException.class)
    ProblemDetail externalServiceUnavailable(ExternalServiceUnavailableException exception) {
        ProblemDetail problem = switch (exception.module()) {
            case MODULO_1 -> problem(HttpStatus.SERVICE_UNAVAILABLE, "inventory-unavailable",
                    "Inventario no disponible",
                    "La información de recursos no está disponible en este momento. Intenta de nuevo en unos minutos.");
            case MODULO_3 -> problem(HttpStatus.SERVICE_UNAVAILABLE, "compliance-unavailable",
                    "Sanciones no disponibles",
                    "No se pudo comprobar la situación de sanciones en este momento. Intenta de nuevo en unos minutos.");
        };
        problem.setProperty("module", exception.module().name());
        return problem;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials() {
        return problem(HttpStatus.UNAUTHORIZED, "invalid-credentials", "Credenciales inválidas",
                "El correo o la contraseña no son correctos.");
    }

    /** Sesión que pasó el filtro pero ya no vale dentro del controlador, p. ej. un usuario desactivado. */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail unauthenticated() {
        return problem(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sesión requerida", "Inicia sesión para continuar.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return badRequest(invalidParameters(exception.getBindingResult().getFieldErrors().stream()
                .map(error -> fieldError(error.getField(), error.getDefaultMessage()))
                .toList()));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        return badRequest(invalidParameters(exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> fieldError(
                                error instanceof FieldError fieldError
                                        ? fieldError.getField()
                                        : result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList()));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String field = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : exception.getPropertyName();
        return badRequest(invalidParameters(List.of(fieldError(field, "formato inválido"))));
    }

    private static ResponseEntity<Object> badRequest(ProblemDetail problem) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private static ProblemDetail invalidParameters(List<Map<String, String>> errors) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "invalid-parameters", "Parámetros inválidos",
                "Revisa los parámetros de la petición.");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", field, "message", message == null ? "inválido" : message);
    }
}
