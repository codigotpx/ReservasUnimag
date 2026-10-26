package edu.unimagdalena.reservasunimag.domain.usecase.receivecheckout;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingCheckOut;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReturns;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReviewFilter;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReviews;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryPendingCheckOutsPort;
import edu.unimagdalena.reservasunimag.domain.port.out.CheckOutRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Las dos listas de pendientes de UC12. Son consultas, no tablas: nada que mantener ni que pueda desincronizarse. El
 * nombre del recurso es del Módulo 1; si no responde, la lista sale igual sin nombres.
 */
public class QueryPendingCheckOutsUseCase implements QueryPendingCheckOutsPort {

    private final CheckOutRepositoryPort checkOuts;
    private final InventoryPort inventory;
    private final Clock clock;
    private final Duration lossThreshold;

    public QueryPendingCheckOutsUseCase(CheckOutRepositoryPort checkOuts, InventoryPort inventory, Clock clock,
                                        Duration lossThreshold) {
        this.checkOuts = checkOuts;
        this.inventory = inventory;
        this.clock = clock;
        this.lossThreshold = lossThreshold;
    }

    @Override
    public PendingReturns pendingReturns(PageRequest page) {
        PendingReturns found = checkOuts.pendingReturns(clock.now(), lossThreshold, page);
        return new PendingReturns(named(found.page()), found.nearLossThreshold(), found.lossThreshold(), found.now());
    }

    @Override
    public PendingReviews pendingReviews(PendingReviewFilter filter, PageRequest page) {
        PendingReviews found = checkOuts.pendingReviews(filter, clock.now(), page);
        return new PendingReviews(named(found.page()), found.student(), found.academic(), found.now());
    }

    private Page<PendingCheckOut> named(Page<PendingCheckOut> page) {
        Map<String, String> names = new HashMap<>();
        try {
            for (String resourceId : page.items().stream().map(p -> p.reservation().resourceId()).distinct().toList()) {
                inventory.details(resourceId).map(Resource::name).ifPresent(name -> names.put(resourceId, name));
            }
        } catch (ExternalServiceUnavailableException unavailable) {
            names.clear();
        }
        List<PendingCheckOut> items = page.items().stream()
                .map(p -> p.withResourceName(names.get(p.reservation().resourceId())))
                .toList();
        return new Page<>(items, page.page(), page.pageSize(), page.total());
    }
}
