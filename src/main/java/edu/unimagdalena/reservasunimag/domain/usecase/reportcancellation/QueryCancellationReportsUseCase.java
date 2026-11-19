package edu.unimagdalena.reservasunimag.domain.usecase.reportcancellation;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReportFilter;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReportPage;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryCancellationReportsPort;
import edu.unimagdalena.reservasunimag.domain.port.out.CancellationReportRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La cara consultable del historial de UC11. El nombre del recurso no se guarda y se le pide al Módulo 1 al
 * responder; si no contesta, el reporte sale con el identificador y sin nombre.
 */
public class QueryCancellationReportsUseCase implements QueryCancellationReportsPort {

    private final CancellationReportRepositoryPort reports;
    private final InventoryPort inventory;

    public QueryCancellationReportsUseCase(CancellationReportRepositoryPort reports, InventoryPort inventory) {
        this.reports = reports;
        this.inventory = inventory;
    }

    @Override
    public CancellationReport find(CurrentUser user, UUID reservationId) {
        CancellationReport report = reports.findByReservation(reservationId)
                .filter(found -> user.role() == Role.DIRECCION_PROGRAMA
                        || (found.holder() != null && user.id().equals(found.holder().userId())))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return report.withResourceName(names(List.of(report.resourceId())).get(report.resourceId()));
    }

    @Override
    public CancellationReportPage list(CancellationReportFilter filter, PageRequest page) {
        CancellationReportPage found = reports.search(filter, page);
        Map<String, String> names = names(found.page().items().stream()
                .map(CancellationReport::resourceId).distinct().toList());
        List<CancellationReport> named = found.page().items().stream()
                .map(report -> report.withResourceName(names.get(report.resourceId())))
                .toList();
        return new CancellationReportPage(
                new Page<>(named, found.page().page(), found.page().pageSize(), found.page().total()),
                found.summary());
    }

    private Map<String, String> names(List<String> resourceIds) {
        Map<String, String> names = new HashMap<>();
        try {
            for (String resourceId : resourceIds) {
                inventory.details(resourceId).map(Resource::name).ifPresent(name -> names.put(resourceId, name));
            }
        } catch (ExternalServiceUnavailableException unavailable) {
            names.clear();
        }
        return names;
    }
}
