package edu.unimagdalena.reservasunimag.domain.model.compliance;

import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;

/**
 * Sobre qué bloquea una sanción (UC6 edge case "Sanciones que no aplican a lo que se está pidiendo"): hay castigos
 * que solo bloquean espacios y otros que solo bloquean activos.
 */
public enum SanctionScope {
    ESPACIOS,
    ACTIVOS,
    TODO;

    /** Un alcance que falta o que no conocemos bloquea todo: ante la duda no se deja reservar (UC6 FR-006). */
    public static SanctionScope parse(String value) {
        if (value == null) {
            return TODO;
        }
        for (SanctionScope scope : values()) {
            if (scope.name().equalsIgnoreCase(value.strip())) {
                return scope;
            }
        }
        return TODO;
    }

    public boolean covers(ResourceCategory category) {
        return switch (this) {
            case TODO -> true;
            case ESPACIOS -> category == ResourceCategory.ESPACIO;
            case ACTIVOS -> category == ResourceCategory.ACTIVO;
        };
    }
}
