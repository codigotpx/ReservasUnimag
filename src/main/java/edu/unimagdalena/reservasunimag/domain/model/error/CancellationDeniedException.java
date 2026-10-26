package edu.unimagdalena.reservasunimag.domain.model.error;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * La reserva existe y es de quien pide, pero ya no se puede cancelar. Siempre {@code CAN-001}: el {@link Reason}
 * dice por qué, porque lo que la persona tiene que hacer en cada caso es distinto. Los valores de
 * {@code extensions} son cadenas, números o {@link Instant}, igual que en {@link ReservationDeniedException}.
 */
public class CancellationDeniedException extends RuntimeException {

    public enum Reason {
        /** Faltan menos minutos que la antelación mínima para el inicio o la recogida (UC4 FR-007). */
        TOO_LATE,
        /** El activo ya se recogió: se devuelve, no se cancela (UC4 FR-008). */
        ASSET_ALREADY_PICKED_UP,
        /** La reserva ya terminó. */
        RESERVATION_FINISHED
    }

    private final Reason reason;
    private final Map<String, Object> extensions;

    private CancellationDeniedException(Reason reason, String detail, Map<String, Object> extensions) {
        super(detail);
        this.reason = reason;
        this.extensions = Map.copyOf(extensions);
    }

    public DenialCode code() {
        return DenialCode.CAN_001;
    }

    public Reason reason() {
        return reason;
    }

    /** Campos propios de este motivo, además de {@code code}. */
    public Map<String, Object> extensions() {
        return extensions;
    }

    /** {@code referenceAt} es el instante contra el que se midió: el inicio de la franja o la hora de recogida. */
    public static CancellationDeniedException tooLate(Duration minNotice, Instant referenceAt, boolean asset,
                                                      boolean alreadyStarted) {
        long minutes = minNotice.toMinutes();
        String what = asset ? "La recogida de este activo" : "Esta reserva";
        String detail = (alreadyStarted ? what + " ya empezó." : what + " empieza en menos de " + minutes
                + " minutos.") + " El plazo mínimo para cancelar es de " + minutes + " minutos de antelación.";
        var extensions = new LinkedHashMap<String, Object>();
        extensions.put("minNoticeMinutes", minutes);
        extensions.put("referenceAt", referenceAt);
        return new CancellationDeniedException(Reason.TOO_LATE, detail, extensions);
    }

    public static CancellationDeniedException assetAlreadyPickedUp(Instant pickedUpAt) {
        return new CancellationDeniedException(Reason.ASSET_ALREADY_PICKED_UP,
                "Ya tienes este activo en tu poder, así que no se cancela: hay que devolverlo. El préstamo se cierra "
                        + "cuando se registre la devolución.",
                Map.of("reason", Reason.ASSET_ALREADY_PICKED_UP.name(), "pickedUpAt", pickedUpAt));
    }

    public static CancellationDeniedException finished() {
        return new CancellationDeniedException(Reason.RESERVATION_FINISHED,
                "Esta reserva ya terminó, así que no hay nada que cancelar.",
                Map.of("reason", Reason.RESERVATION_FINISHED.name()));
    }
}
