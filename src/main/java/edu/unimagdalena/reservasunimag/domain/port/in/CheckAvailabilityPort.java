package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceWithStatus;

import java.util.List;

/** UC8, versión por lote: un estado por recurso en la franja, sin modificar nada (UC8 FR-008, FR-009). */
public interface CheckAvailabilityPort {

    /** Devuelve los recursos en el mismo orden en que llegan. */
    List<ResourceWithStatus> checkAvailability(List<Resource> resources, TimeSlot slot);
}
