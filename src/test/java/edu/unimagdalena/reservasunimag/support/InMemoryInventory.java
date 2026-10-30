package edu.unimagdalena.reservasunimag.support;

import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException.ExternalModule;
import edu.unimagdalena.reservasunimag.domain.model.resource.Catalog;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceFilter;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Un Módulo 1 en memoria que solo sabe dar fichas; cuenta cuántas pidieron y puede estar caído. */
public final class InMemoryInventory implements InventoryPort {

    private final Map<String, Resource> resources = new LinkedHashMap<>();
    public boolean down;
    public int detailCalls;

    public InMemoryInventory(Resource... resources) {
        for (Resource resource : resources) {
            this.resources.put(resource.id(), resource);
        }
    }

    @Override
    public Catalog searchCatalog(ResourceFilter filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Resource> details(String resourceId) {
        detailCalls++;
        if (down) {
            throw new ExternalServiceUnavailableException(ExternalModule.MODULO_1, "caído", null);
        }
        return Optional.ofNullable(resources.get(resourceId));
    }

    @Override
    public Map<String, OperationalStatus> operationalStatus(Collection<String> resourceIds) {
        throw new UnsupportedOperationException();
    }
}
