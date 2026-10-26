package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que una persona aparta: un recurso del Módulo 1 durante {@code [startsAt, endsAt)}. En un espacio es la
 * franja; en un activo, de la recogida al vencimiento. {@code userId} es {@code null} solo en un bloqueo académico
 * y {@code loan} solo en un activo.
 */
public record Reservation(
        UUID id,
        UUID userId,
        String resourceId,
        ResourceCategory category,
        ReservationOrigin origin,
        ReservationStatus status,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt,
        Loan loan) {

    public Reservation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("la ocupación debe terminar después de empezar");
        }
        if ((origin == ReservationOrigin.ACADEMICO) != (userId == null)) {
            throw new IllegalArgumentException("solo una reserva académica queda sin titular");
        }
        if ((category == ResourceCategory.ACTIVO) != (loan != null)) {
            throw new IllegalArgumentException("el préstamo existe si y solo si el recurso es un activo");
        }
    }

    /** Una reserva estudiantil recién confirmada. {@code loan} va solo para un activo. */
    public static Reservation confirmed(UUID id, UUID userId, String resourceId, ResourceCategory category,
                                        Instant startsAt, Instant endsAt, Instant createdAt, Loan loan) {
        return new Reservation(id, userId, resourceId, category, ReservationOrigin.ESTUDIANTIL,
                ReservationStatus.CONFIRMADA, startsAt, endsAt, createdAt, loan);
    }

    /**
     * La misma reserva deshecha con el estado que deja {@code origin}. Solo se cancela una reserva confirmada: las
     * demás ya están en un estado final.
     */
    public Reservation cancel(CancellationOrigin origin) {
        if (status != ReservationStatus.CONFIRMADA) {
            throw new IllegalStateException("la reserva " + id + " ya está " + status);
        }
        return new Reservation(id, userId, resourceId, category, this.origin, origin.status(), startsAt, endsAt,
                createdAt, loan);
    }

    /** Activo que ya está en manos de alguien: no se cancela, se devuelve (UC4 FR-008). */
    public boolean inHands() {
        return loan != null && loan.pickedUp() && !loan.returned();
    }

    /** Préstamo que ya pasó su vencimiento sin que se registrara la devolución (UC2 FR-015). */
    public boolean overdue(Instant now) {
        return loan != null && !loan.returned() && now.isAfter(endsAt);
    }
}
