package edu.unimagdalena.reservasunimag.domain.model.resource;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;

/** El historial de cambios de un recurso. {@code resourceName} es {@code null} si el Módulo 1 no respondió. */
public record ResourceHistory(String resourceId, String resourceName, Page<StatusChange> changes) {
}
