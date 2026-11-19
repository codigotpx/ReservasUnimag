package edu.unimagdalena.reservasunimag.domain.model.reservation;

public enum ReservationOrigin {
    /** La hace una persona desde el frontend; siempre tiene titular. */
    ESTUDIANTIL,
    /** Bloqueo por actividad docente que llega con la carga de horarios (UC3); nunca tiene titular. */
    ACADEMICO
}
