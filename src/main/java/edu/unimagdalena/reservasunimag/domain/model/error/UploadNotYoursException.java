package edu.unimagdalena.reservasunimag.domain.model.error;

/** Una carga solo la confirma quien la validó: es esa persona la que revisó el impacto (UC3 Contratos §2). */
public class UploadNotYoursException extends RuntimeException {

    public UploadNotYoursException() {
        super("Solo quien validó la carga puede confirmarla.");
    }
}
