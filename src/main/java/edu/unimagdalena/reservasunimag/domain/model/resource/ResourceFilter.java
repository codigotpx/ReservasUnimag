package edu.unimagdalena.reservasunimag.domain.model.resource;

import edu.unimagdalena.reservasunimag.domain.model.error.InvalidFilterException;

/**
 * Filtros de la consulta. {@code type} es una cadena opaca del Módulo 1; el aforo mínimo solo existe para espacios.
 * Un {@code null} significa "no filtré".
 */
public record ResourceFilter(ResourceCategory category, String type, Integer minCapacity) {

    public ResourceFilter {
        type = type == null || type.isBlank() ? null : type.strip();
        if (minCapacity != null && category != ResourceCategory.ESPACIO) {
            throw new InvalidFilterException("minCapacity", "solo aplica cuando la categoría es ESPACIO");
        }
    }

    /** Si el recurso cumple el aforo mínimo; el Módulo 1 puede no filtrarlo, así que se aplica aquí también. */
    public boolean acceptsCapacityOf(Resource resource) {
        return minCapacity == null || (resource.maxCapacity() != null && resource.maxCapacity() >= minCapacity);
    }
}
