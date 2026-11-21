package edu.unimagdalena.reservasunimag.domain.model.resource;

/**
 * El estado que reporta el Módulo 1. Los tres primeros son los del contrato. {@code RESERVADO} y
 * {@code BLOQUEO_ACADEMICO} los usa su API real aunque según nuestros specs son estados de este módulo (P-20.4): si su
 * inventario los marca, se respetan y el recurso no se ofrece, en cualquier franja, como el mantenimiento.
 */
public enum OperationalStatus {
    DISPONIBLE,
    EN_USO,
    EN_MANTENIMIENTO,
    RESERVADO,
    BLOQUEO_ACADEMICO
}
