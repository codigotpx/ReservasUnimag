package edu.unimagdalena.reservasunimag.domain.model.reservation;

/** En qué va la entrega de un reporte al Módulo 3: los mismos estados que la outbox. */
public enum DeliveryStatus {
    PENDING,
    SENT,
    /** Se agotaron los reintentos; hay que reencolarlo a mano. */
    FAILED
}
