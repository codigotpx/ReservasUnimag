package edu.unimagdalena.reservasunimag.domain.model.reservation;

import java.time.Instant;

/**
 * El cupo en uso de una persona. {@code nextRelease} es cuándo termina la vigente más próxima; es {@code null}
 * cuando no hay ninguna o cuando la más próxima es un préstamo vencido y sin devolver, que no libera cupo hasta
 * que se registre la devolución ({@code waitsForReturn}).
 */
public record QuotaUsage(int used, Instant nextRelease, boolean waitsForReturn) {
}
