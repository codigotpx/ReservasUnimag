package edu.unimagdalena.reservasunimag.domain.usecase.updatestatus;

import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceHistory;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeFilter;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryStatusChangesPort;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.StatusChangeRepositoryPort;

import java.util.Optional;

/** La historia de un recurso. El nombre lo pone el Módulo 1 si responde; si no, la historia sale igual sin él. */
public class QueryStatusChangesUseCase implements QueryStatusChangesPort {

    private final StatusChangeRepositoryPort statusChanges;
    private final InventoryPort inventory;

    public QueryStatusChangesUseCase(StatusChangeRepositoryPort statusChanges, InventoryPort inventory) {
        this.statusChanges = statusChanges;
        this.inventory = inventory;
    }

    @Override
    public ResourceHistory history(String resourceId, StatusChangeFilter filter, PageRequest page) {
        var changes = statusChanges.findByResource(resourceId, filter, page);
        Optional<Resource> resource;
        try {
            resource = inventory.details(resourceId);
        } catch (ExternalServiceUnavailableException unavailable) {
            return new ResourceHistory(resourceId, null, changes);
        }
        if (resource.isEmpty() && changes.total() == 0) {
            throw new ResourceNotFoundException(resourceId);
        }
        return new ResourceHistory(resourceId, resource.map(Resource::name).orElse(null), changes);
    }
}
