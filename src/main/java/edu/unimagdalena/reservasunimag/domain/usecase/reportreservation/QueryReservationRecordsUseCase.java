package edu.unimagdalena.reservasunimag.domain.usecase.reportreservation;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordFilter;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordPage;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordReport;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryReservationRecordsPort;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRecordRepositoryPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La cara consultable del historial de UC10. El nombre del recurso no se guarda y se le pide al Módulo 1 al
 * responder; si no contesta, la ficha sale con el identificador y sin nombre.
 */
public class QueryReservationRecordsUseCase implements QueryReservationRecordsPort {

    private final ReservationRecordRepositoryPort records;
    private final InventoryPort inventory;

    public QueryReservationRecordsUseCase(ReservationRecordRepositoryPort records, InventoryPort inventory) {
        this.records = records;
        this.inventory = inventory;
    }

    @Override
    public ReservationRecordReport find(CurrentUser user, UUID reservationId) {
        ReservationRecordReport report = records.findByReservation(reservationId)
                .filter(found -> user.role() == Role.DIRECCION_PROGRAMA
                        || (found.holder() != null && user.id().equals(found.holder().userId())))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return report.withResourceName(names(List.of(report.resourceId())).get(report.resourceId()));
    }

    @Override
    public ReservationRecordPage list(ReservationRecordFilter filter, PageRequest page) {
        ReservationRecordPage found = records.search(filter, page);
        Map<String, String> names = names(found.page().items().stream()
                .map(ReservationRecordReport::resourceId).distinct().toList());
        List<ReservationRecordReport> named = found.page().items().stream()
                .map(report -> report.withResourceName(names.get(report.resourceId())))
                .toList();
        return new ReservationRecordPage(
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
