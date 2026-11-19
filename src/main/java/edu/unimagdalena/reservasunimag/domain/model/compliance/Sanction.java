package edu.unimagdalena.reservasunimag.domain.model.compliance;

import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;

import java.time.LocalDate;

/**
 * Una sanción tal como la contestó el Módulo 3. Aquí no se decide ninguna: solo se lee si está vigente hoy y sobre qué
 * bloquea (UC6 FR-005).
 */
public record Sanction(String reason, LocalDate start, LocalDate end, SanctionScope scope) {

    public Sanction {
        scope = scope == null ? SanctionScope.TODO : scope;
    }

    /**
     * Vigente cuando {@code start <= today <= end}, con {@code today} en Bogotá. Se compara contra el día de la
     * consulta y no contra la franja pedida, y el último día cuenta completo (UC6 edge case "La sanción vence en
     * mitad de la franja pedida"). Sin {@code start} ya empezó, porque llegó en la lista de vigentes; sin {@code end}
     * no termina, porque no se puede inventar la fecha (UC6 edge case "Respuesta del Módulo 3 incompleta").
     */
    public boolean inForceOn(LocalDate today) {
        return (start == null || !today.isBefore(start)) && (end == null || !today.isAfter(end));
    }

    public boolean appliesTo(ResourceCategory category) {
        return scope.covers(category);
    }

    public boolean endDateMissing() {
        return end == null;
    }
}
