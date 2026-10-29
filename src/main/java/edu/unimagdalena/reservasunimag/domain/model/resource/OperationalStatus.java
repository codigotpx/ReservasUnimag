package edu.unimagdalena.reservasunimag.domain.model.resource;

/** Los tres estados que posee el Módulo 1. {@code RESERVADO} y {@code BLOQUEO_ACADEMICO} son nuestros. */
public enum OperationalStatus {
    DISPONIBLE,
    EN_USO,
    EN_MANTENIMIENTO
}
