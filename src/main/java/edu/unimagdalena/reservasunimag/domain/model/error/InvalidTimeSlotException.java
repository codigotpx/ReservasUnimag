package edu.unimagdalena.reservasunimag.domain.model.error;

import java.time.LocalDate;
import java.time.LocalTime;

public class InvalidTimeSlotException extends RuntimeException {

    /** Los tres rechazos de {@code TimeSlot}; viajan como {@code code} en el 400 (UC1 Contratos §1). */
    public enum Reason {
        TIME_SLOT_OUTSIDE_WINDOW,
        TIME_SLOT_CROSSES_MIDNIGHT,
        TIME_SLOT_END_NOT_AFTER_START
    }

    private final Reason reason;
    private final LocalDate date;
    private final LocalTime start;
    private final LocalTime end;

    public InvalidTimeSlotException(Reason reason, LocalDate date, LocalTime start, LocalTime end) {
        super(reason.name());
        this.reason = reason;
        this.date = date;
        this.start = start;
        this.end = end;
    }

    public Reason reason() {
        return reason;
    }

    public LocalDate date() {
        return date;
    }

    public LocalTime start() {
        return start;
    }

    public LocalTime end() {
        return end;
    }
}
