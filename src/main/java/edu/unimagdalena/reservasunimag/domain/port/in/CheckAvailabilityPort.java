package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.Availability;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceWithStatus;

import java.time.Instant;
import java.util.List;

/** UC8, versión por lote: un estado por recurso en la franja, sin modificar nada (UC8 FR-008, FR-009). */
public interface CheckAvailabilityPort {

    /** Devuelve los recursos en el mismo orden en que llegan. */
    List<ResourceWithStatus> checkAvailability(List<Resource> resources, TimeSlot slot);

    /**
     * Un solo recurso en un intervalo cualquiera —un préstamo cruza días y no cabe en una franja—, con "hasta
     * cuándo" está comprometido (UC8 FR-012). Usa el estado operativo que ya trae la ficha: el Módulo 1 no se
     * vuelve a consultar.
     */
    Availability checkOne(Resource resource, Instant from, Instant to);
}
