package edu.unimagdalena.reservasunimag.domain.model.resource;

import java.util.List;
import java.util.Objects;

/**
 * Ficha del catálogo tal como la entrega el Módulo 1; no se guarda (FR-006). Los atributos de espacio y de activo
 * son {@code null} en la categoría que no los tiene.
 */
public record Resource(
        String id,
        String name,
        ResourceCategory category,
        String type,
        String location,
        OperationalStatus operationalStatus,
        String faculty,
        Integer maxCapacity,
        List<String> equipment,
        String condition,
        Integer loanTermBusinessDays) {

    public Resource {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(operationalStatus, "operationalStatus");
        equipment = equipment == null ? null : List.copyOf(equipment);
    }

    public static Resource space(String id, String name, String type, String location,
                                 OperationalStatus operationalStatus, String faculty, int maxCapacity,
                                 List<String> equipment) {
        return new Resource(id, name, ResourceCategory.ESPACIO, type, location, operationalStatus, faculty,
                maxCapacity, equipment, null, null);
    }

    public static Resource asset(String id, String name, String type, String location,
                                 OperationalStatus operationalStatus, String condition,
                                 Integer loanTermBusinessDays) {
        return new Resource(id, name, ResourceCategory.ACTIVO, type, location, operationalStatus, null, null, null,
                condition, loanTermBusinessDays);
    }
}
