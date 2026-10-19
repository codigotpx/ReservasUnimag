package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.resource.Catalog;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceFilter;

import java.util.Collection;
import java.util.Map;

/** Módulo 1: dueño del catálogo y del estado operativo. Cualquier fallo es {@link ExternalServiceUnavailableException}. */
public interface InventoryPort {

    /** El catálogo filtrado, con todas sus páginas recorridas hasta el tope configurado (UC8 FR-011). */
    Catalog searchCatalog(ResourceFilter filter);

    /** Estado operativo por lote. Un id que el Módulo 1 no conoce no aparece en el resultado. */
    Map<String, OperationalStatus> operationalStatus(Collection<String> resourceIds);
}
