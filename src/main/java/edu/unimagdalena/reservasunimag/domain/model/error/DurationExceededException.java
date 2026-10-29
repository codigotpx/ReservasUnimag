package edu.unimagdalena.reservasunimag.domain.model.error;

/** Una franja de espacio más larga que el tope: es una franja mal formada, no una denegación (UC2 FR-009). */
public class DurationExceededException extends RuntimeException {

    private final long limitMinutes;
    private final long requestedMinutes;

    public DurationExceededException(long limitMinutes, long requestedMinutes) {
        super("La franja de " + requestedMinutes + " minutos supera el máximo de " + limitMinutes);
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
