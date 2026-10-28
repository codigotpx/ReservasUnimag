package edu.unimagdalena.reservasunimag.domain.model.reservation;

/** Estados por los que puede pasar una reserva (UC2). */
public enum ReservationStatus {
    CONFIRMADA,
    CANCELADA,
    CANCELADA_POR_PRIORIDAD_ACADEMICA,
    CANCELADA_POR_RECURSO_NO_DISPONIBLE,
    FINALIZADA
}
