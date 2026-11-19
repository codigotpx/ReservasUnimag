package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.Availability;
import edu.unimagdalena.reservasunimag.domain.model.resource.AvailabilityAnswer;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceWithStatus;

import java.time.Instant;
import java.util.List;

/**
 * UC8: la única definición de "disponible" del módulo. Ninguna operación modifica nada (FR-009) ni reutiliza
 * respuestas anteriores (FR-006), y las tres resuelven el estado y el "hasta cuándo" con la misma regla.
 */
public interface CheckAvailabilityPort {

    /** Versión por lote (FR-008): devuelve los recursos en el mismo orden en que llegan. */
    List<ResourceWithStatus> checkAvailability(List<Resource> resources, TimeSlot slot);

    /**
     * Un solo recurso en un intervalo cualquiera —un préstamo cruza días y no cabe en una franja—, con "hasta
     * cuándo" está comprometido (FR-012). Usa el estado operativo que ya trae la ficha: el Módulo 1 no se vuelve a
     * consultar, así UC2 pide la ficha una sola vez por reserva.
     */
    Availability checkOne(Resource resource, Instant from, Instant to);

    /**
     * Un recurso por su id, pidiéndole la ficha al Módulo 1 en el momento: es la pregunta del endpoint propio de
     * UC8, que no viene de una reserva y no tiene la ficha a mano.
     *
     * @throws ResourceNotFoundException si el Módulo 1 no lo conoce: no se responde "ocupado"
     * @throws ExternalServiceUnavailableException si el Módulo 1 no contestó: nunca se da por libre (FR-007)
     */
    AvailabilityAnswer check(String resourceId, TimeSlot slot);
}
