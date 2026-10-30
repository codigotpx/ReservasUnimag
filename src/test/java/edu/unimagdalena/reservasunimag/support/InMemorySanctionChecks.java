package edu.unimagdalena.reservasunimag.support;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckFilter;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckOutcome;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckPage;
import edu.unimagdalena.reservasunimag.domain.port.out.SanctionCheckRepositoryPort;

import java.util.ArrayList;
import java.util.List;

/** El registro de consultas de UC6 en memoria; guarda en orden de llegada. */
public final class InMemorySanctionChecks implements SanctionCheckRepositoryPort {

    public final List<SanctionCheck> checks = new ArrayList<>();

    @Override
    public void record(SanctionCheck check) {
        checks.add(check);
    }

    public SanctionCheck last() {
        return checks.getLast();
    }

    @Override
    public SanctionCheckPage search(SanctionCheckFilter filter, PageRequest page) {
        List<SanctionCheck> all = checks.reversed();
        return new SanctionCheckPage(Page.of(all, page), new SanctionCheckPage.Summary(all.size(),
                count(SanctionCheckOutcome.CLEAR), count(SanctionCheckOutcome.SANCTIONED),
                count(SanctionCheckOutcome.NO_HISTORY), count(SanctionCheckOutcome.NOT_VERIFIED)));
    }

    private long count(SanctionCheckOutcome outcome) {
        return checks.stream().filter(check -> check.outcome() == outcome).count();
    }
}
