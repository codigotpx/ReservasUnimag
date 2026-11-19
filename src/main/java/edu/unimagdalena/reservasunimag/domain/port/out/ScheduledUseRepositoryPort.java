package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.resource.ScheduledUse;

import java.time.Instant;
import java.util.List;

/** Las reservas confirmadas de un recurso que se cruzan con [from, to), ordenadas por inicio. */
public interface ScheduledUseRepositoryPort {

    List<ScheduledUse> findByResource(String resourceId, Instant from, Instant to);
}
