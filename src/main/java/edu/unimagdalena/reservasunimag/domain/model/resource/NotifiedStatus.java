package edu.unimagdalena.reservasunimag.domain.model.resource;

/**
 * Cómo va el aviso de un cambio al Módulo 1. {@code NOT_SENT} dice que no se esperaba enviarlo, que es lo que pasa en
 * todos los motivos menos el inicio de uso (FR-012); {@code PENDING} que hay un aviso esperando a salir.
 */
public enum NotifiedStatus {
    NOT_SENT,
    PENDING,
    SENT,
    FAILED
}
