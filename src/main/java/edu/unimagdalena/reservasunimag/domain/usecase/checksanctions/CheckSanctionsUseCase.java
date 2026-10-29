package edu.unimagdalena.reservasunimag.domain.usecase.checksanctions;

import edu.unimagdalena.reservasunimag.domain.model.compliance.Sanction;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionNotice;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.port.in.CheckSanctionsPort;
import edu.unimagdalena.reservasunimag.domain.port.out.CompliancePort;

import java.util.Optional;

public class CheckSanctionsUseCase implements CheckSanctionsPort {

    private final CompliancePort compliance;

    public CheckSanctionsUseCase(CompliancePort compliance) {
        this.compliance = compliance;
    }

    @Override
    public Optional<SanctionNotice> checkSanctions(CurrentUser user) {
        try {
            return compliance.report(user.code()).farthestSanction().map(SanctionNotice::active);
        } catch (ExternalServiceUnavailableException unavailable) {
            // Sin sanciones sí hay qué mostrar: la comprobación que decide es la de Reservar recursos (UC6 FR-006).
            return Optional.of(SanctionNotice.notVerified());
        }
    }

    @Override
    public Optional<Sanction> activeSanction(CurrentUser user) {
        return compliance.report(user.code()).farthestSanction();
    }
}
