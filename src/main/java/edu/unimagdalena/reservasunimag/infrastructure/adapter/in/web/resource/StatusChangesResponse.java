package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.resource;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceHistory;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChange;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

import java.util.List;

/**
 * La historia de un recurso, como la fija UC7 Contratos §2. {@code note} es fijo a propósito: sin él, un historial que
 * nunca menciona el mantenimiento parecería incompleto, cuando lo que pasa es que ese estado no es nuestro.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record StatusChangesResponse(String resourceId, String resourceName, List<Item> changes,
                             SearchResourcesResponse.PaginationBody pagination, String note) {

    static final String NOTE = "El estado operativo del recurso lo lleva el Módulo 1. De los tres que son suyos, el "
            + "único que sale de este historial es EN_USO; el regreso a DISPONIBLE y el mantenimiento se los reporta "
            + "el Módulo 3.";

    static StatusChangesResponse from(ResourceHistory history) {
        var page = history.changes();
        return new StatusChangesResponse(history.resourceId(), history.resourceName(),
                page.items().stream().map(Item::from).toList(),
                new SearchResourcesResponse.PaginationBody(page.page(), page.pageSize(), page.totalPages(),
                        page.total()),
                NOTE);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Item(String id, String reservationId, OccupancyBody occupancy, String previousStatusModule2,
                String newStatus, String reason, String changedAt, String notifiedStatus, String outboxMessageId) {

        static Item from(StatusChange change) {
            return new Item(change.id().toString(),
                    change.reservationId() == null ? null : change.reservationId().toString(),
                    new OccupancyBody(IsoTimes.offset(change.occupancyStart()), IsoTimes.offset(change.occupancyEnd())),
                    change.previousStatusModule2().name(), change.newStatus().name(), change.reason().name(),
                    IsoTimes.offset(change.changedAt()), change.notifiedStatus().name(),
                    change.outboxMessageId() == null ? null : change.outboxMessageId().toString());
        }
    }

    record OccupancyBody(String start, String end) {
    }
}
