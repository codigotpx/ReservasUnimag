package edu.unimagdalena.reservasunimag.domain.usecase.searchresources;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionNotice;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.Catalog;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceWithStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.SearchResourcesQuery;
import edu.unimagdalena.reservasunimag.domain.model.resource.SearchResourcesResult;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.CheckAvailabilityPort;
import edu.unimagdalena.reservasunimag.domain.port.in.CheckSanctionsPort;
import edu.unimagdalena.reservasunimag.domain.port.in.SearchResourcesPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Trae el catálogo filtrado del Módulo 1, calcula el estado de <em>todo</em> el conjunto y recién entonces pagina:
 * así se sabe si ninguno es seleccionable (FR-012) y se puede sugerir otra franja. Si el Módulo 1 falla, la
 * consulta falla; nunca se muestra una lista que no se pudo comprobar (FR-008).
 */
public class SearchResourcesUseCase implements SearchResourcesPort {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    /** Orden estable por nombre y, ante nombres iguales, por identificador; el estado no influye (FR-014). */
    private static final Comparator<Resource> BY_NAME_THEN_ID =
            Comparator.comparing(Resource::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Resource::id);

    private final InventoryPort inventory;
    private final CheckAvailabilityPort availability;
    private final CheckSanctionsPort sanctions;
    private final NextFreeSlotFinder nextFreeSlot;
    private final Clock clock;

    public SearchResourcesUseCase(InventoryPort inventory, CheckAvailabilityPort availability,
                                  CheckSanctionsPort sanctions, NextFreeSlotFinder nextFreeSlot, Clock clock) {
        this.inventory = inventory;
        this.availability = availability;
        this.sanctions = sanctions;
        this.nextFreeSlot = nextFreeSlot;
        this.clock = clock;
    }

    @Override
    public SearchResourcesResult search(SearchResourcesQuery query) {
        Catalog catalog = inventory.searchCatalog(query.filter());
        List<Resource> resources = catalog.resources().stream()
                .filter(query.filter()::acceptsCapacityOf)
                .sorted(BY_NAME_THEN_ID)
                .toList();
        List<ResourceWithStatus> withStatus = availability.checkAvailability(resources, query.slot());
        boolean anySelectable = withStatus.stream().anyMatch(ResourceWithStatus::selectable);

        TimeSlot next = null;
        String message = null;
        if (!anySelectable) {
            next = resources.isEmpty() ? null : nextFreeSlot.find(resources, query.slot()).orElse(null);
            message = noneSelectableMessage(query.slot(), resources.size(), next != null);
        }
        SanctionNotice notice = query.user().role().includes(Role.ESTUDIANTE)
                ? sanctions.checkSanctions(query.user()).orElse(null)
                : null;

        return new SearchResourcesResult(query.slot(), query.filter(), Page.of(withStatus, query.page()),
                anySelectable, catalog.truncated(), clock.now(), message, next, notice);
    }

    private static String noneSelectableMessage(TimeSlot slot, int total, boolean hasNextSlot) {
        if (total == 0) {
            return "Ningún recurso cumple los filtros.";
        }
        String count = total == 1 ? "El único recurso que cumple los filtros no está disponible"
                : "Ninguno de los " + total + " recursos que cumplen los filtros está disponible";
        return hasNextSlot
                ? count + " entre las " + slot.start().format(HOUR) + " y las " + slot.end().format(HOUR) + "."
                : count + " entre las " + slot.start().format(HOUR) + " y las " + TimeSlot.CLOSING.format(HOUR)
                + " de hoy.";
    }
}
