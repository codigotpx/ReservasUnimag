package edu.unimagdalena.reservasunimag.domain.model.error;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot.ZONE;

/**
 * No se puede registrar el inicio de uso de esta reserva (UC7 Contratos §3). {@code USO-001} si la reserva no lo
 * admite y {@code USO-002} si llega fuera de su ventana. Los valores de {@code extensions} son cadenas, números o
 * {@link Instant}, como en las demás denegaciones.
 */
public class UseStartRejectedException extends RuntimeException {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final DenialCode code;
    private final Map<String, Object> extensions;

    private UseStartRejectedException(DenialCode code, String detail, Map<String, Object> extensions) {
        super(detail);
        this.code = code;
        this.extensions = Map.copyOf(extensions);
    }

    public DenialCode code() {
        return code;
    }

    public Map<String, Object> extensions() {
        return extensions;
    }

    /** USO-001: la reserva no está confirmada, o es un bloqueo académico sin titular. */
    public static UseStartRejectedException notStartable(String status) {
        return new UseStartRejectedException(DenialCode.USO_001,
                "Esta reserva no admite registrar la llegada: está " + status + ".",
                Map.of("reservationStatus", status));
    }

    /** USO-002: todavía falta más que la ventana para el inicio. */
    public static UseStartRejectedException tooEarly(Instant startsAt, Duration window) {
        Instant opens = startsAt.minus(window);
        return new UseStartRejectedException(DenialCode.USO_002,
                "Tu reserva empieza a las " + startsAt.atZone(ZONE).format(HOUR) + ". Puedes registrar tu llegada "
                        + "desde las " + opens.atZone(ZONE).format(HOUR) + ".", Map.of("windowOpensAt", opens));
    }

    /** USO-002: la franja o el periodo ya terminó. */
    public static UseStartRejectedException finished(Instant endsAt) {
        return new UseStartRejectedException(DenialCode.USO_002,
                "Tu reserva ya terminó, así que no hay llegada que registrar.", Map.of("endedAt", endsAt));
    }
}
