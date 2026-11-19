package edu.unimagdalena.reservasunimag.domain.model.error;

import java.time.Instant;
import java.util.Map;

/** La carga existe pero no se puede confirmar: un 409 con su {@code code} (UC3 Contratos §2). */
public class UploadNotApplicableException extends RuntimeException {

    public enum Code {
        UPLOAD_NOT_APPLICABLE,
        UPLOAD_EXPIRED,
        UPLOAD_ALREADY_APPLIED,
        IMPACT_CHANGED
    }

    private final Code code;
    private final Map<String, Object> extensions;

    private UploadNotApplicableException(Code code, String detail, Map<String, Object> extensions) {
        super(detail);
        this.code = code;
        this.extensions = Map.copyOf(extensions);
    }

    public Code code() {
        return code;
    }

    /** Valores de texto, números o {@link Instant}. */
    public Map<String, Object> extensions() {
        return extensions;
    }

    public static UploadNotApplicableException notApplicable(int rowsWithBlockingVerdict) {
        return new UploadNotApplicableException(Code.UPLOAD_NOT_APPLICABLE,
                "La validación encontró " + rowsWithBlockingVerdict + " fila(s) con problemas. Corrige el archivo y "
                        + "vuelve a validarlo.", Map.of("rowsWithBlockingVerdict", rowsWithBlockingVerdict));
    }

    public static UploadNotApplicableException expired(Instant expiredAt) {
        return new UploadNotApplicableException(Code.UPLOAD_EXPIRED,
                "La validación venció sin confirmarse. Vuelve a validar el archivo.", Map.of("expiredAt", expiredAt));
    }

    public static UploadNotApplicableException alreadyApplied(Instant appliedAt) {
        return new UploadNotApplicableException(Code.UPLOAD_ALREADY_APPLIED,
                "Esta carga ya se aplicó; no se aplica dos veces.", Map.of("appliedAt", appliedAt));
    }

    public static UploadNotApplicableException impactChanged(int atValidation, int now) {
        String detail = atValidation == now
                ? "El impacto cambió desde que se validó: las reservas que se cancelarían ya no son las mismas. "
                + "Vuelve a validar el archivo."
                : "El impacto cambió desde que se validó: ahora se cancelarían " + now + " reservas en vez de "
                + atValidation + ". Vuelve a validar el archivo.";
        return new UploadNotApplicableException(Code.IMPACT_CHANGED, detail,
                Map.of("reservationsToCancelAtValidation", atValidation, "reservationsToCancelNow", now));
    }
}
