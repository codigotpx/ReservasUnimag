package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceHistory;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeFilter;

/** UC7 FR-009: la historia de la ocupación de un recurso, para auditar. */
public interface QueryStatusChangesPort {

    /**
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException si no tiene cambios y el Módulo 1 no lo conoce
     */
    ResourceHistory history(String resourceId, StatusChangeFilter filter, PageRequest page);
}
