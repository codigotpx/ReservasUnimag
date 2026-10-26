package edu.unimagdalena.reservasunimag.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/** Parámetros del negocio y de las integraciones, bajo el prefijo {@code reservations}. */
@Validated
@ConfigurationProperties("reservations")
public record ReservationProperties(
        @Min(1) int pageSize,
        @NotNull Duration nextSlotStep,
        @Min(1) int maxQuota,
        @NotNull Duration maxSpaceDuration,
        @NotNull @DurationMin(minutes = 1) Duration minCancellationNotice,
        @NotNull @DurationMin(minutes = 1) Duration noShowGrace,
        @NotNull @DurationMin(hours = 1) Duration loanLossThreshold,
        @NotNull List<LocalDate> holidays,
        @NotNull List<String> resourceTypes,
        @Valid @NotNull Module1 module1,
        @Valid @NotNull Module3 module3,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Kafka kafka,
        @Valid @NotNull Outbox outbox) {

    /**
     * {@code pageSize} es el tamaño de página que se le pide al Módulo 1 y {@code maxPages} el tope de páginas del
     * catálogo que se recorren por consulta (UC8 FR-011). {@code notifyUseStarted} es la válvula del aviso de inicio
     * de uso (UC7 FR-004): apagada, el inicio se registra sin avisar.
     */
    public record Module1(@NotNull URI baseUrl, @NotNull Duration timeout, @Min(1) int pageSize,
                          @Min(1) int maxPages, boolean notifyUseStarted) {
    }

    public record Module3(@NotNull URI baseUrl, @NotNull Duration timeout) {
    }

    public record Jwt(
            @NotBlank @Size(min = 32, message = "debe tener al menos 32 caracteres para HS256") String secret,
            @NotBlank String issuer,
            @NotNull Duration ttl,
            @NotBlank String cookieName,
            boolean cookieSecure) {
    }

    public record Kafka(@Valid @NotNull Topic topic) {

        /** Nombres de los topics que publicamos; el prefijo es el módulo dueño del evento. */
        public record Topic(@NotBlank String record, @NotBlank String cancellation, @NotBlank String noShow,
                            @NotBlank String noShowAck, @NotBlank String checkOut, @NotBlank String checkOutAck,
                            @NotBlank String resourceStatus) {
        }
    }

    /**
     * El publicador que lleva {@code outbox_message} a Kafka (plan general, Mensajería). {@code alertAfter} es cuánto
     * puede esperar un evento pendiente antes de advertirlo en el log, por debajo de los 30 minutos de UC11 SC-005.
     */
    public record Outbox(boolean enabled, @NotNull Duration interval, @Min(1) int batch, @Min(1) int maxAttempts,
                         @NotNull Duration alertAfter) {
    }
}
