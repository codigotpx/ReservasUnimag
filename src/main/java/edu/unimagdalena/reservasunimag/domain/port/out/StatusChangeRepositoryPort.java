package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChange;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeFilter;

import java.util.Optional;
import java.util.UUID;

/** El historial de UC7. La escritura se llama dentro de {@link TransactionPort#required}. */
public interface StatusChangeRepositoryPort {

    void insert(StatusChange change);

    /** El registro del inicio de uso de una reserva, con cómo va su aviso. */
    Optional<StatusChange> findUseStarted(UUID reservationId);

    /** Los cambios de un recurso, del más antiguo al más reciente. */
    Page<StatusChange> findByResource(String resourceId, StatusChangeFilter filter, PageRequest page);
}
