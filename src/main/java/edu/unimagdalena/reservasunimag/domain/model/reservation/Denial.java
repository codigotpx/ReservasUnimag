package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.error.DenialCode;

import java.time.Instant;
import java.util.UUID;

/** Registro de una negativa: quién, sobre qué recurso, por qué y cuándo (UC2 FR-005). */
public record Denial(UUID id, UUID userId, String resourceId, DenialCode code, Instant occurredAt) {
}
