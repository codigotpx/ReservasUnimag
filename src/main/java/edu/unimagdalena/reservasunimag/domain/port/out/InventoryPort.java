package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.resource.Catalog;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceFilter;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Módulo 1: dueño del catálogo y del estado operativo. Cualquier fallo es {@link ExternalServiceUnavailableException}. */
public interface InventoryPort {

    /** El catálogo filtrado, con todas sus páginas recorridas hasta el tope configurado (UC8 FR-011). */
    Catalog searchCatalog(ResourceFilter filter);

    /**
     * La ficha de un solo recurso, con su estado operativo y, en un activo, su plazo de préstamo. Vacío si el
     * Módulo 1 no lo conoce.
     */
    Optional<Resource> details(String resourceId);

    /** Estado operativo por lote. Un id que el Módulo 1 no conoce no aparece en el resultado. */
    Map<String, OperationalStatus> operationalStatus(Collection<String> resourceIds);
}
