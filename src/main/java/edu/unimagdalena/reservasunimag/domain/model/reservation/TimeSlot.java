package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.error.DurationExceededException;
import edu.unimagdalena.reservasunimag.domain.model.error.InvalidTimeSlotException;
import edu.unimagdalena.reservasunimag.domain.model.error.InvalidTimeSlotException.Reason;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Franja consultada o reservada: un día y un intervalo {@code [start, end)} en hora de Bogotá,
 * dentro de la ventana de operación de 06:00 a 22:00 y sin cruzar la medianoche (UC1 FR-010, FR-011).
 */
public record TimeSlot(LocalDate date, LocalTime start, LocalTime end) {

    public static final ZoneId ZONE = ZoneId.of("America/Bogota");
    public static final LocalTime OPENING = LocalTime.of(6, 0);
    public static final LocalTime CLOSING = LocalTime.of(22, 0);
    /** Tope de duración de una reserva sobre un espacio: FR-009. */
    public static final Duration MAXIMUM_DURATION = Duration.ofHours(2);

    public TimeSlot {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Reason rejection = rejectionFor(start, end);
        if (rejection != null) {
            throw new InvalidTimeSlotException(rejection, date, start, end);
        }
        Instant startsAt = date.atTime(start).atZone(ZONE).toInstant();
        Instant endsAt = date.atTime(end).atZone(ZONE).toInstant();
        if (Duration.between(startsAt, endsAt).toMinutes() > MAXIMUM_DURATION.toMinutes()) {
            throw new DurationExceededException(MAXIMUM_DURATION.toMinutes(), Duration.between(startsAt, endsAt).toMinutes());
        }
    }

    private static Reason rejectionFor(LocalTime start, LocalTime end) {
        if (!end.isAfter(start)) {
            // 21:00–01:00 o 20:00–00:00: el fin cae de madrugada, así que la franja pasa al día siguiente.
            return end.isBefore(start) && end.isBefore(OPENING)
                    ? Reason.TIME_SLOT_CROSSES_MIDNIGHT
                    : Reason.TIME_SLOT_END_NOT_AFTER_START;
        }
        if (start.isBefore(OPENING) || end.isAfter(CLOSING)) {
            return Reason.TIME_SLOT_OUTSIDE_WINDOW;
        }
        return null;
    }

    public Instant startsAt() {
        return date.atTime(start).atZone(ZONE).toInstant();
    }

    public Instant endsAt() {
        return date.atTime(end).atZone(ZONE).toInstant();
    }

    /** Cruce de intervalos semiabiertos: 10:00–12:00 y 12:00–14:00 no se cruzan. */
    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return startsAt().isBefore(otherEnd) && otherStart.isBefore(endsAt());
    }
}
