package edu.unimagdalena.reservasunimag.domain.model.compliance;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Reporte de cumplimiento de una persona. Se usa solo para avisar: no se guarda ni se cachea (UC6 FR-005). */
public record ComplianceReport(List<Sanction> activeSanctions, int absenceCount, int lateReturns,
                               Instant generatedAt) {

    public ComplianceReport {
        activeSanctions = List.copyOf(activeSanctions);
    }

    /** Persona sin historial: el Módulo 3 puede responder vacío o 404, y las dos se leen igual (UC6 FR-008). */
    public static ComplianceReport empty() {
        return new ComplianceReport(List.of(), 0, 0, null);
    }

    /** La sanción que termina más tarde, que es la que se avisa. */
    public Optional<Sanction> farthestSanction() {
        return activeSanctions.stream().max(Comparator.comparing(Sanction::end));
    }
}
