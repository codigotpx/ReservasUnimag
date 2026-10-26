package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.error;

import edu.unimagdalena.reservasunimag.domain.model.error.CancellationDeniedException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.DurationExceededException;
import edu.unimagdalena.reservasunimag.domain.model.error.IncompleteResourceDetailsException;
import edu.unimagdalena.reservasunimag.domain.model.error.InvalidCredentialsException;
import edu.unimagdalena.reservasunimag.domain.model.error.InvalidFilterException;
import edu.unimagdalena.reservasunimag.domain.model.error.InvalidTimeSlotException;
import edu.unimagdalena.reservasunimag.domain.model.error.NotAnAssetException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationAlreadyClosedException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationDeniedException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationShapeException;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.error.UseStartRejectedException;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;
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
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.time.LocalDate;
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

    @ExceptionHandler(InvalidFilterException.class)
    ProblemDetail invalidFilter(InvalidFilterException exception) {
        return invalidParameters(List.of(fieldError(exception.field(), exception.getMessage())));
    }

    @ExceptionHandler(ExternalServiceUnavailableException.class)
    ProblemDetail externalServiceUnavailable(ExternalServiceUnavailableException exception) {
        ProblemDetail problem = switch (exception.module()) {
            case MODULO_1 -> problem(HttpStatus.SERVICE_UNAVAILABLE, "inventory-unavailable",
                    "Inventario no disponible",
                    "La información de recursos no está disponible en este momento. Intenta de nuevo en unos minutos.");
            // Solo llega aquí al reservar: en la consulta (UC1) la caída del Módulo 3 es un aviso, no un error.
            case MODULO_3 -> problem(HttpStatus.SERVICE_UNAVAILABLE, "sanctions-not-verifiable",
                    "No se pudo comprobar tu situación",
                    "No se pudo comprobar si tienes sanciones activas, así que la reserva no se confirmó. "
                            + "Intenta de nuevo en unos minutos.");
        };
        problem.setProperty("module", exception.module().name());
        return problem;
    }

    /** Las cuatro denegaciones del diccionario: un solo {@code code} y los campos que su mensaje necesita. */
    @ExceptionHandler(ReservationDeniedException.class)
    ProblemDetail reservationDenied(ReservationDeniedException exception) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "reservation-denied", "Reserva denegada",
                exception.getMessage());
        problem.setProperty("code", exception.code().code());
        exception.extensions().forEach((name, value) -> problem.setProperty(name, formatted(value)));
        return problem;
    }

    /** UC4: siempre {@code CAN-001}; el {@code reason} dice qué tiene que hacer la persona. */
    @ExceptionHandler(CancellationDeniedException.class)
    ProblemDetail cancellationDenied(CancellationDeniedException exception) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "cancellation-denied", "La reserva ya no es cancelable",
                exception.getMessage());
        problem.setProperty("code", exception.code().code());
        exception.extensions().forEach((name, value) -> problem.setProperty(name, formatted(value)));
        return problem;
    }

    /** No existe o es de otra persona: el mismo cuerpo, sin nada que confirme que el identificador existe. */
    @ExceptionHandler(ReservationNotFoundException.class)
    ProblemDetail reservationNotFound() {
        return problem(HttpStatus.NOT_FOUND, "reservation-not-found", "Reserva no encontrada",
                "La reserva no existe.");
    }

    /** UC7: USO-001 o USO-002, con lo que la persona necesita para entender por qué. */
    @ExceptionHandler(UseStartRejectedException.class)
    ProblemDetail useStartRejected(UseStartRejectedException exception) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "use-start-rejected",
                "No se pudo registrar el inicio de uso", exception.getMessage());
        problem.setProperty("code", exception.code().code());
        exception.extensions().forEach((name, value) -> problem.setProperty(name, formatted(value)));
        return problem;
    }

    @ExceptionHandler(ReservationAlreadyClosedException.class)
    ProblemDetail reservationAlreadyClosed() {
        return problem(HttpStatus.CONFLICT, "reservation-closed", "La reserva ya está cerrada",
                "Esta reserva ya se cerró por otra vía y no se puede cancelar.");
    }

    @ExceptionHandler(DurationExceededException.class)
    ProblemDetail durationExceeded(DurationExceededException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "invalid-time-slot", "Franja horaria inválida",
                "Una reserva de espacio puede durar como máximo " + formatHours(exception.limitMinutes())
                        + ". Ajusta la franja.");
        problem.setProperty("code", "DURATION_EXCEEDED");
        problem.setProperty("limitMinutes", exception.limitMinutes());
        problem.setProperty("requestedMinutes", exception.requestedMinutes());
        return problem;
    }

    @ExceptionHandler(ReservationShapeException.class)
    ProblemDetail reservationShape(ReservationShapeException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "invalid-reservation-shape",
                "Solicitud que no corresponde al recurso", exception.getMessage());
        problem.setProperty("code", "SHAPE_DOES_NOT_MATCH_CATEGORY");
        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail resourceNotFound(ResourceNotFoundException exception) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "resource-not-found", "Recurso no encontrado",
                exception.getMessage());
        problem.setProperty("resourceId", exception.resourceId());
        return problem;
    }

    /** El contrato del Módulo 1 vino incompleto: no es culpa de la persona ni una regla de negocio. */
    @ExceptionHandler(IncompleteResourceDetailsException.class)
    ProblemDetail incompleteResourceDetails(IncompleteResourceDetailsException exception) {
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, "inventory-unavailable",
                "Inventario no disponible",
                "No se pudo consultar el recurso en este momento. Intenta de nuevo en unos minutos.");
        problem.setProperty("code", "INCOMPLETE_RESOURCE_DETAILS");
        problem.setProperty("module", "MODULO_1");
        return problem;
    }

    @ExceptionHandler(NotAnAssetException.class)
    ProblemDetail notAnAsset(NotAnAssetException exception) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "not-an-asset", "El recurso no es un activo",
                exception.getMessage());
        problem.setProperty("code", "NOT_AN_ASSET");
        problem.setProperty("resourceId", exception.resourceId());
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
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return badRequest(invalidParameters(List.of(fieldError(exception.getParameterName(), "es obligatorio"))));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String field = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : exception.getPropertyName();
        return badRequest(invalidParameters(List.of(fieldError(field, "formato inválido"))));
    }

    /** Los instantes y las fechas viajan como los fija el contrato; lo demás, tal cual. */
    private static Object formatted(Object value) {
        if (value instanceof Instant instant) {
            return IsoTimes.offset(instant);
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        return value;
    }

    private static String formatHours(long minutes) {
        return minutes % 60 == 0 ? (minutes / 60) + (minutes == 60 ? " hora" : " horas") : minutes + " minutos";
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
