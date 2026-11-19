package edu.unimagdalena.reservasunimag.domain.model.compliance;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Desde cuándo se admite un reporte de no asistencia: {@code grace} después del inicio de la franja o de la hora de
 * recogida, que en un activo es el mismo inicio de la reserva (UC9 FR-011). El borde es inclusivo, igual que el de
 * cancelar en UC4: la persona tiene 9 minutos y 59 segundos para llegar, y a los 10 exactos ya se admite.
 */
public record NoShowPolicy(Duration grace) {

    public NoShowPolicy {
        Objects.requireNonNull(grace, "grace");
        if (grace.isNegative() || grace.isZero()) {
            throw new IllegalArgumentException("el plazo de no asistencia debe ser positivo");
        }
    }

    public Instant acceptedFrom(Reservation reservation) {
        return reservation.startsAt().plus(grace);
    }

    /** Se mide contra la comprobación en sitio, no contra la hora de llegada: un reporte que viaja tarde no se vuelve anticipado. */
    public boolean admits(Reservation reservation, Instant verifiedAt) {
        return !verifiedAt.isBefore(acceptedFrom(reservation));
    }
}
