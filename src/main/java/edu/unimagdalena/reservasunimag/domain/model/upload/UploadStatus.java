package edu.unimagdalena.reservasunimag.domain.model.upload;

public enum UploadStatus {
    /** Se validó y se puede confirmar dentro de su vigencia. */
    VALIDATED,
    APPLIED,
    /** Alguna fila tiene un dictamen bloqueante; queda como constancia (FR-009). */
    REJECTED,
    /** Pasó la vigencia sin confirmar: el impacto que se mostró ya no es de fiar. */
    EXPIRED
}
