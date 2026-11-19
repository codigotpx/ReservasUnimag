package edu.unimagdalena.reservasunimag.domain.model.compliance;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot.ZONE;

/**
 * El acuse de un check-out (UC12 Contratos §2). Si se aceptó lleva el check-out aplicado; uno repetido se acepta con
 * {@code duplicate} y los datos del primero, y entonces no cerró ni liberó nada. Si se rechazó lleva el motivo y,
 * cuando la revisión llegó antes de tiempo, desde cuándo se admite.
 */
public record CheckOutAck(
        Reservation reservation,
        UUID sourceEventId,
        boolean accepted,
        boolean duplicate,
        CheckOut checkOut,
        CheckOutRejection rejection,
        Instant acceptedFrom,
        String detail,
        Instant occurredAt) {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    public CheckOutAck {
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (accepted != (checkOut != null) || accepted == (rejection != null)) {
            throw new IllegalArgumentException("un acuse aceptado lleva el check-out; uno rechazado, su motivo");
        }
    }

    public static CheckOutAck accepted(Reservation reservation, UUID sourceEventId, CheckOut checkOut,
                                       boolean duplicate, Instant now) {
        return new CheckOutAck(reservation, sourceEventId, true, duplicate, checkOut, null, null, null, now);
    }

    public static CheckOutAck slotNotFinished(Reservation reservation, UUID sourceEventId, Instant now) {
        return new CheckOutAck(reservation, sourceEventId, false, false, null, CheckOutRejection.SLOT_NOT_FINISHED,
                reservation.endsAt(), "La franja de esta reserva termina a las "
                + reservation.endsAt().atZone(ZONE).format(HOUR) + ". La revisión se admite después de esa hora.", now);
    }

    /** {@code kind} afina el mensaje de {@code VERDICT_REQUIRED}, que tiene dos variantes. */
    public static CheckOutAck rejected(Reservation reservation, UUID sourceEventId, CheckOutRejection rejection,
                                       CheckOutKind kind, Instant now) {
        if (rejection == CheckOutRejection.SLOT_NOT_FINISHED) {
            throw new IllegalArgumentException("SLOT_NOT_FINISHED lleva acceptedFrom: usar slotNotFinished");
        }
        return new CheckOutAck(reservation, sourceEventId, false, false, null, rejection, null,
                detailFor(rejection, kind), now);
    }

    /** El check-out de este acuse cerró el préstamo y liberó el cupo: un activo aceptado por primera vez. */
    public boolean loanClosed() {
        return accepted && !duplicate && checkOut.kind() == CheckOutKind.ASSET_RETURN;
    }

    private static String detailFor(CheckOutRejection rejection, CheckOutKind kind) {
        return switch (rejection) {
            case RESERVATION_CANCELLED -> "La reserva se canceló antes de la entrega, así que no hay préstamo que cerrar.";
            case ASSET_NOT_PICKED_UP -> "El activo nunca se recogió, así que no hay devolución que registrar.";
            case RESERVATION_NOT_USED -> "La reserva se canceló o tiene una ausencia registrada: el espacio no se usó "
                    + "y no hay revisión que registrar.";
            case VERDICT_REQUIRED -> kind == CheckOutKind.SPACE_REVIEW
                    ? "La revisión de un espacio debe traer su dictamen: SIN_NOVEDAD o REQUIERE_MANTENIMIENTO."
                    : "La devolución de un activo no lleva dictamen ni daños: el estado del activo se le reporta "
                    + "directamente al Módulo 1.";
            case SLOT_NOT_FINISHED -> throw new IllegalStateException();
        };
    }
}
