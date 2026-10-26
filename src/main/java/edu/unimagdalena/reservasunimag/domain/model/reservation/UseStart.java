package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;

import java.time.Instant;
import java.util.Objects;

/**
 * Lo único que se le avisa al Módulo 1: que el uso de un recurso empezó (UC7 FR-004). No lleva un estado, así que no
 * se le puede mandar otro por descuido (FR-012).
 */
public record UseStart(Reservation reservation, Instant startedAt) {

    public UseStart {
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(startedAt, "startedAt");
    }

    /**
     * Hasta cuándo dura el uso, que es la instrucción de cierre para el Módulo 1 (FR-004a). En un espacio, la hora de
     * fin de la franja; en un activo {@code null}, porque su uso termina con la devolución que le reporta el Módulo 3.
     */
    public Instant useEndsAt() {
        return reservation.category() == ResourceCategory.ESPACIO ? reservation.endsAt() : null;
    }
}
