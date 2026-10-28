package edu.unimagdalena.reservasunimag.domain.model.error;

/** Franja mal formada porque excede el tope de duración de un espacio (FR-009). */
public class DurationExceededException extends RuntimeException {

    private final long limitMinutes;
    private final long requestedMinutes;

    public DurationExceededException(long limitMinutes, long requestedMinutes) {
        super("La franja excede el tope de duración");
        this.limitMinutes = limitMinutes;
        this.requestedMinutes = requestedMinutes;
    }

    public long limitMinutes() {
        return limitMinutes;
    }

    public long requestedMinutes() {
        return requestedMinutes;
    }
}
