package edu.unimagdalena.reservasunimag.domain.model.error;

/** Denegación de una reserva: código del diccionario con el detalle del motivo. */
public class ReservationDeniedException extends RuntimeException {

    private final DenialCode denialCode;
    private final String detail;

    public ReservationDeniedException(DenialCode denialCode, String detail) {
        super(detail);
        this.denialCode = denialCode;
        this.detail = detail;
    }

    public DenialCode denialCode() {
        return denialCode;
    }

    public String detail() {
        return detail;
    }
}
