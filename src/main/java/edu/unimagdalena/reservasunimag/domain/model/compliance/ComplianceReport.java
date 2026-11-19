package edu.unimagdalena.reservasunimag.domain.model.compliance;

import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Reporte de cumplimiento de una persona. Se usa para decidir y avisar, y lo único que se guarda de él es lo que
 * contestó, en el registro de la consulta: nunca como sanción propia (UC6 FR-005). {@code noHistory} distingue a la
 * persona que el Módulo 3 no conoce de la que conoce y está al día; las dos se leen como "sin sanciones" (FR-008).
 */
public record ComplianceReport(List<Sanction> activeSanctions, int absenceCount, int lateReturns,
                               Instant generatedAt, boolean noHistory) {

    public ComplianceReport {
        activeSanctions = List.copyOf(activeSanctions);
    }

    public ComplianceReport(List<Sanction> activeSanctions, int absenceCount, int lateReturns, Instant generatedAt) {
        this(activeSanctions, absenceCount, lateReturns, generatedAt, false);
    }

    /** Persona sin historial: el Módulo 3 puede responder vacío o 404, y las dos se leen igual (UC6 FR-008). */
    public static ComplianceReport empty() {
        return new ComplianceReport(List.of(), 0, 0, null, true);
    }

    /**
     * De las sanciones vigentes hoy que bloquean {@code category}, la que termina más tarde, que es la que se le
     * explica a la persona; una sin fecha de fin no termina nunca y manda sobre las demás. Con {@code category} nulo
     * cuenta cualquier alcance.
     */
    public Optional<Sanction> farthestInForce(LocalDate today, ResourceCategory category) {
        return activeSanctions.stream()
                .filter(sanction -> sanction.inForceOn(today))
                .filter(sanction -> category == null || sanction.appliesTo(category))
                .max(Comparator.comparing(Sanction::end, Comparator.nullsLast(Comparator.naturalOrder())));
    }
}
