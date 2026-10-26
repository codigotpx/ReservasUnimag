package edu.unimagdalena.reservasunimag.domain.model.compliance;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot.ZONE;

/**
 * El acuse que se le devuelve al Módulo 3 por cada reporte recibido, aceptado o no (UC9 FR-002, FR-011). Si se
 * aceptó lleva la ausencia, la persona y el tiempo apartado; si no, el motivo y, según cuál, desde cuándo se admite o
 * en qué estado estaba la reserva. Un reporte repetido se acepta con {@code duplicate} y los datos de la primera vez.
 * {@code holderCode} es {@code null} solo en un rechazo.
 */
public record NoShowAck(
        Reservation reservation,
        UUID sourceEventId,
        boolean accepted,
        boolean duplicate,
        Absence absence,
        String holderCode,
        NoShowRejection rejection,
        Instant acceptedFrom,
        String detail,
        Instant occurredAt) {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    public NoShowAck {
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (accepted != (absence != null) || accepted == (rejection != null)) {
            throw new IllegalArgumentException("un acuse aceptado lleva la ausencia; uno rechazado, su motivo");
        }
    }

    public static NoShowAck accepted(Reservation reservation, UUID sourceEventId, Absence absence, String holderCode,
                                     boolean duplicate, Instant now) {
        return new NoShowAck(reservation, sourceEventId, true, duplicate, absence, holderCode, null, null, null, now);
    }

    public static NoShowAck tooEarly(Reservation reservation, UUID sourceEventId, Instant acceptedFrom, Instant now) {
        return new NoShowAck(reservation, sourceEventId, false, false, null, null, NoShowRejection.TOO_EARLY,
                acceptedFrom, "El reporte se admite a partir de las " + acceptedFrom.atZone(ZONE).format(HOUR)
                + ". La persona todavía está a tiempo de llegar.", now);
    }

    public static NoShowAck rejected(Reservation reservation, UUID sourceEventId, NoShowRejection rejection,
                                     Instant now) {
        if (rejection == NoShowRejection.TOO_EARLY) {
            throw new IllegalArgumentException("TOO_EARLY lleva acceptedFrom: usar tooEarly");
        }
        return new NoShowAck(reservation, sourceEventId, false, false, null, null, rejection, null,
                detailFor(rejection, reservation.status()), now);
    }

    /** El estado de la reserva solo viaja cuando explica el rechazo: estaba cancelada. */
    public ReservationStatus reservationStatus() {
        return rejection == NoShowRejection.RESERVATION_CANCELLED ? reservation.status() : null;
    }

    private static String detailFor(NoShowRejection rejection, ReservationStatus status) {
        return switch (rejection) {
            case RESERVATION_CANCELLED -> switch (status) {
                case CANCELADA_POR_PRIORIDAD_ACADEMICA ->
                        "La reserva se canceló por prioridad académica, así que no corresponde ninguna ausencia.";
                case CANCELADA_POR_RECURSO_NO_DISPONIBLE -> "La reserva se canceló porque el recurso quedó fuera de "
                        + "servicio, así que no corresponde ninguna ausencia.";
                default -> "La reserva la canceló su titular a tiempo, así que no corresponde ninguna ausencia.";
            };
            case ALREADY_PICKED_UP -> "El activo ya se recogió: la persona sí se presentó.";
            case ALREADY_RETURNED -> "El activo ya se devolvió: la persona sí lo usó.";
            case RESERVATION_FINISHED -> "La reserva ya está cerrada, así que no corresponde registrar una ausencia.";
            case ACADEMIC_BLOCK -> "La reserva es un bloqueo académico y no tiene titular al que anotarle una ausencia.";
            case TOO_EARLY -> throw new IllegalStateException();
        };
    }
}
