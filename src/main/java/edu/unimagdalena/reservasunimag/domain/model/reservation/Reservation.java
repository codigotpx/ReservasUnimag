package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que una persona aparta: un recurso del Módulo 1 durante {@code [startsAt, endsAt)}. En un espacio es la
 * franja; en un activo, de la recogida al vencimiento. {@code userId} es {@code null} solo en un bloqueo académico,
 * y {@code loan} existe solo en el activo de una reserva estudiantil: una clase que aparta un activo no es un
 * préstamo. {@code useStartedAt} es cuándo empezó el uso (UC7 FR-004), {@code null} mientras nadie lo registre.
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
        Loan loan,
        Instant useStartedAt) {

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
        if ((category == ResourceCategory.ACTIVO && origin == ReservationOrigin.ESTUDIANTIL) != (loan != null)) {
            throw new IllegalArgumentException("el préstamo existe si y solo si es un activo apartado por una persona");
        }
    }

    /** Una reserva cuyo uso todavía no empezó. */
    public Reservation(UUID id, UUID userId, String resourceId, ResourceCategory category, ReservationOrigin origin,
                       ReservationStatus status, Instant startsAt, Instant endsAt, Instant createdAt, Loan loan) {
        this(id, userId, resourceId, category, origin, status, startsAt, endsAt, createdAt, loan, null);
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
                createdAt, loan, useStartedAt);
    }

    /**
     * La misma reserva con el uso empezado a {@code at}. En un activo, recoger es empezar a usar: si no tenía la
     * recogida marcada, se marca con el mismo instante.
     */
    public Reservation startUse(Instant at) {
        Loan started = loan == null || loan.pickedUp()
                ? loan
                : new Loan(loan.termBusinessDays(), at, loan.returnedAt());
        return new Reservation(id, userId, resourceId, category, origin, status, startsAt, endsAt, createdAt, started,
                at);
    }

    /** Alguien registró que empezó a usarla, o la clase llegó a su hora (UC7 FR-004). */
    public boolean useStarted() {
        return useStartedAt != null;
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
