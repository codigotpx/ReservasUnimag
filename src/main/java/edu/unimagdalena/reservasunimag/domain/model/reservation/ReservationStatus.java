package edu.unimagdalena.reservasunimag.domain.model.reservation;

public enum ReservationStatus {
    CONFIRMADA,
    CANCELADA,
    CANCELADA_POR_PRIORIDAD_ACADEMICA,
    CANCELADA_POR_RECURSO_NO_DISPONIBLE,
    FINALIZADA;

    /** Si es uno de los tres estados en que termina una reserva deshecha. */
    public boolean isCancellation() {
        return this == CANCELADA || this == CANCELADA_POR_PRIORIDAD_ACADEMICA
                || this == CANCELADA_POR_RECURSO_NO_DISPONIBLE;
    }
}
