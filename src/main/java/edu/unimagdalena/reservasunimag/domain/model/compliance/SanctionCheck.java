package edu.unimagdalena.reservasunimag.domain.model.compliance;

import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * El registro de una consulta al Módulo 3 (UC6 FR-009): que preguntamos, cuándo, para qué y qué nos contestaron. No
 * es una sanción: dice que en ese momento el Módulo 3 contestó eso, que es un hecho que no cambia (FR-005).
 *
 * @param category  lo que se quería reservar; solo con {@link SanctionCheckPurpose#RESERVE_RESOURCES}, y explica por
 *                  qué una sanción de otro alcance no denegó
 * @param answered  lo que contestó el Módulo 3; nulo si no contestó o no conoce a la persona
 * @param failure   por qué no se pudo comprobar; solo con {@link SanctionCheckOutcome#NOT_VERIFIED}
 */
public record SanctionCheck(UUID id, String personCode, SanctionCheckPurpose purpose, ResourceCategory category,
                            Instant askedAt, SanctionCheckOutcome outcome, Answered answered, long durationMs,
                            String failure) {

    /**
     * Lo que contestó el Módulo 3. Los datos de la sanción son los de la que decidió, y solo vienen con
     * {@link SanctionCheckOutcome#SANCTIONED}; los conteos vienen siempre que hubo reporte.
     */
    public record Answered(String sanctionReason, LocalDate endsOn, SanctionScope scope,
                           boolean endDateMissing, int absenceCount, int lateReturns) {
    }

    public static SanctionCheck answered(String personCode, SanctionCheckPurpose purpose, ResourceCategory category,
                                         Instant askedAt, Instant answeredAt, ComplianceReport report,
                                         Optional<Sanction> deciding) {
        if (report.noHistory()) {
            return new SanctionCheck(UUID.randomUUID(), personCode, purpose, category, askedAt,
                    SanctionCheckOutcome.NO_HISTORY, null, millis(askedAt, answeredAt), null);
        }
        Answered answered = deciding
                .map(sanction -> new Answered(sanction.reason(), sanction.end(), sanction.scope(),
                        sanction.endDateMissing(), report.absenceCount(), report.lateReturns()))
                .orElseGet(() -> new Answered(null, null, null, false, report.absenceCount(), report.lateReturns()));
        return new SanctionCheck(UUID.randomUUID(), personCode, purpose, category, askedAt,
                deciding.isPresent() ? SanctionCheckOutcome.SANCTIONED : SanctionCheckOutcome.CLEAR, answered,
                millis(askedAt, answeredAt), null);
    }

    public static SanctionCheck notVerified(String personCode, SanctionCheckPurpose purpose,
                                            ResourceCategory category, Instant askedAt, Instant failedAt,
                                            String failure) {
        return new SanctionCheck(UUID.randomUUID(), personCode, purpose, category, askedAt,
                SanctionCheckOutcome.NOT_VERIFIED, null, millis(askedAt, failedAt), failure);
    }

    private static long millis(Instant from, Instant to) {
        return Math.max(0, Duration.between(from, to).toMillis());
    }
}
