package edu.unimagdalena.reservasunimag.domain.usecase.checksanctions;

import edu.unimagdalena.reservasunimag.domain.model.compliance.ComplianceReport;
import edu.unimagdalena.reservasunimag.domain.model.compliance.Sanction;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckPurpose;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionNotice;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.port.in.CheckSanctionsPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.CompliancePort;
import edu.unimagdalena.reservasunimag.domain.port.out.SanctionCheckRepositoryPort;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * UC6. Pregunta por la persona de la sesión y por nadie más (FR-007), decide la vigencia contra el día de hoy en
 * Bogotá y registra cada consulta con lo que contestó el Módulo 3 (FR-009). No guarda ninguna sanción ni la cachea.
 */
public class CheckSanctionsUseCase implements CheckSanctionsPort {

    private final CompliancePort compliance;
    private final SanctionCheckRepositoryPort checks;
    private final Clock clock;

    public CheckSanctionsUseCase(CompliancePort compliance, SanctionCheckRepositoryPort checks, Clock clock) {
        this.compliance = compliance;
        this.checks = checks;
        this.clock = clock;
    }

    @Override
    public Optional<SanctionNotice> checkSanctions(CurrentUser user) {
        try {
            return ask(user, SanctionCheckPurpose.SEARCH_RESOURCES, null).map(SanctionNotice::active);
        } catch (ExternalServiceUnavailableException unavailable) {
            // Sin sanciones sí hay qué mostrar: la comprobación que decide es la de Reservar recursos (FR-006).
            return Optional.of(SanctionNotice.notVerified());
        }
    }

    @Override
    public Optional<Sanction> activeSanction(CurrentUser user, ResourceCategory category) {
        return ask(user, SanctionCheckPurpose.RESERVE_RESOURCES, category);
    }

    private Optional<Sanction> ask(CurrentUser user, SanctionCheckPurpose purpose, ResourceCategory category) {
        Instant askedAt = clock.now();
        ComplianceReport report;
        try {
            report = compliance.report(user.code());
        } catch (ExternalServiceUnavailableException unavailable) {
            checks.record(SanctionCheck.notVerified(user.code(), purpose, category, askedAt, clock.now(),
                    detail(unavailable)));
            throw unavailable;
        }
        Instant answeredAt = clock.now();
        LocalDate today = askedAt.atZone(TimeSlot.ZONE).toLocalDate();
        Optional<Sanction> deciding = report.farthestInForce(today, category);
        checks.record(SanctionCheck.answered(user.code(), purpose, category, askedAt, answeredAt, report, deciding));
        return deciding;
    }

    /** La causa de fondo ("Connection refused", "Read timed out") dice más que el mensaje genérico del adaptador. */
    private static String detail(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? failure.getMessage() : root.getMessage();
    }
}
