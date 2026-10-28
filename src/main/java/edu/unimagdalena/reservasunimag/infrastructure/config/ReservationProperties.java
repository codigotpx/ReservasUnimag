package edu.unimagdalena.reservasunimag.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

/** Parámetros del negocio y de las integraciones, bajo el prefijo {@code reservations}. */
@Validated
@ConfigurationProperties("reservations")
public record ReservationProperties(
        @Min(1) int pageSize,
        @NotNull List<String> resourceTypes,
        @Min(1) int maxQuota,
        @NotNull Duration maxSpaceDuration,
        @NotNull LocalTime closingTime,
        @NotNull List<String> holidays,
        @Valid @NotNull Module1 module1,
        @Valid @NotNull Module3 module3,
        @Valid @NotNull Jwt jwt) {

    public ReservationProperties {
        if (maxSpaceDuration == null || maxSpaceDuration.isNegative()) {
            throw new IllegalArgumentException("reservations.max-space-duration debe ser mayor que cero");
        }
        if (holidays == null) {
            holidays = List.of();
        }
    }

    /** {@code maxPages} es el tope de páginas del catálogo que se recorren por consulta (UC8 FR-011). */
    public record Module1(@NotNull URI baseUrl, @NotNull Duration timeout, @Min(1) int maxPages) {
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
}
