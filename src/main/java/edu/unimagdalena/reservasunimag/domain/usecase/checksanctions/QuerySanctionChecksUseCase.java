package edu.unimagdalena.reservasunimag.domain.usecase.checksanctions;

import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckFilter;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckPage;
import edu.unimagdalena.reservasunimag.domain.port.in.QuerySanctionChecksPort;
import edu.unimagdalena.reservasunimag.domain.port.out.SanctionCheckRepositoryPort;

/** UC6 FR-009: la auditoría de las consultas. El rol lo exige el controlador; los filtros, {@link SanctionCheckFilter}. */
public class QuerySanctionChecksUseCase implements QuerySanctionChecksPort {

    private final SanctionCheckRepositoryPort checks;

    public QuerySanctionChecksUseCase(SanctionCheckRepositoryPort checks) {
        this.checks = checks;
    }

    @Override
    public SanctionCheckPage list(SanctionCheckFilter filter, PageRequest page) {
        return checks.search(filter, page);
    }
}
