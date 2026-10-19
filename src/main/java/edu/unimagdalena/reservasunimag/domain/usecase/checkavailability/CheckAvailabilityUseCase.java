package edu.unimagdalena.reservasunimag.domain.usecase.checkavailability;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Occupancy;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.Availability;
import edu.unimagdalena.reservasunimag.domain.model.resource.DisplayedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceWithStatus;
import edu.unimagdalena.reservasunimag.domain.port.in.CheckAvailabilityPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.OccupancyRepositoryPort;

import java.time.Instant;
import java.util.Comparator;
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

    @Override
    public Availability checkOne(Resource resource, Instant from, Instant to) {
        List<Occupancy> overlapping = occupancies.findOccupancies(List.of(resource.id()), from, to).stream()
                .filter(occupancy -> occupancy.overlaps(from, to))
                .toList();
        DisplayedStatus status = DisplayedStatus.resolve(resource.operationalStatus(), overlapping, from, to,
                clock.now());
        // "Hasta cuándo" solo se promete cuando lo decide una ocupación nuestra y esa ocupación tiene fin.
        List<Occupancy> deciding = overlapping.stream()
                .filter(occupancy -> occupancy.type().displayedStatus() == status)
                .toList();
        Instant occupiedUntil = deciding.isEmpty() || deciding.stream().anyMatch(o -> o.endsAt() == null)
                ? null
                : deciding.stream().map(Occupancy::endsAt).max(Comparator.naturalOrder()).orElse(null);
        return new Availability(status, occupiedUntil);
    }
}
