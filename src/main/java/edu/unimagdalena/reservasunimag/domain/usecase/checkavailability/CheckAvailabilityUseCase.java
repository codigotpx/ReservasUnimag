package edu.unimagdalena.reservasunimag.domain.usecase.checkavailability;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Occupancy;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.DisplayedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceWithStatus;
import edu.unimagdalena.reservasunimag.domain.port.in.CheckAvailabilityPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.OccupancyRepositoryPort;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * El estado de cada recurso cruza el estado operativo del Módulo 1 (que ya viene en la ficha) con lo que el
 * Módulo 2 tiene ocupado, en una sola consulta para todo el lote. No modifica nada.
 */
public class CheckAvailabilityUseCase implements CheckAvailabilityPort {

    private final OccupancyRepositoryPort occupancies;
    private final Clock clock;

    public CheckAvailabilityUseCase(OccupancyRepositoryPort occupancies, Clock clock) {
        this.occupancies = occupancies;
        this.clock = clock;
    }

    @Override
    public List<ResourceWithStatus> checkAvailability(List<Resource> resources, TimeSlot slot) {
        if (resources.isEmpty()) {
            return List.of();
        }
        Map<String, List<Occupancy>> byResource = occupancies
                .findOccupancies(resources.stream().map(Resource::id).toList(), slot.startsAt(), slot.endsAt())
                .stream()
                .collect(Collectors.groupingBy(Occupancy::resourceId));
        Instant now = clock.now();
        return resources.stream()
                .map(resource -> new ResourceWithStatus(resource, DisplayedStatus.resolve(
                        resource.operationalStatus(), byResource.getOrDefault(resource.id(), List.of()), slot, now)))
                .toList();
    }
}
