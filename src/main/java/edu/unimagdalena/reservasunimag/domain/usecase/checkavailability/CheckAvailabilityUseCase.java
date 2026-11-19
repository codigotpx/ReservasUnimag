package edu.unimagdalena.reservasunimag.domain.usecase.checkavailability;

import edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Occupancy;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.Availability;
import edu.unimagdalena.reservasunimag.domain.model.resource.AvailabilityAnswer;
import edu.unimagdalena.reservasunimag.domain.model.resource.DisplayedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceWithStatus;
import edu.unimagdalena.reservasunimag.domain.port.in.CheckAvailabilityPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.OccupancyRepositoryPort;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * El estado de cada recurso cruza el estado operativo del Módulo 1 (que viene en la ficha) con lo que el Módulo 2
 * tiene ocupado. Solo depende de puertos de lectura: no puede modificar nada (UC8 FR-009).
 */
public class CheckAvailabilityUseCase implements CheckAvailabilityPort {

    private final InventoryPort inventory;
    private final OccupancyRepositoryPort occupancies;
    private final Clock clock;

    public CheckAvailabilityUseCase(InventoryPort inventory, OccupancyRepositoryPort occupancies, Clock clock) {
        this.inventory = inventory;
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
                .map(resource -> new ResourceWithStatus(resource, assess(resource,
                        byResource.getOrDefault(resource.id(), List.of()), slot.startsAt(), slot.endsAt(), now)))
                .toList();
    }

    @Override
    public Availability checkOne(Resource resource, Instant from, Instant to) {
        return assess(resource, occupancies.findOccupancies(List.of(resource.id()), from, to), from, to,
                clock.now());
    }

    @Override
    public AvailabilityAnswer check(String resourceId, TimeSlot slot) {
        Resource resource = inventory.details(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException(resourceId));
        Availability availability = checkOne(resource, slot.startsAt(), slot.endsAt());
        return new AvailabilityAnswer(resource, slot, availability, clock.now());
    }

    /**
     * La regla única. "Hasta cuándo" solo se promete cuando lo decide una ocupación nuestra, y nunca para un
     * préstamo ya vencido: ahí no hay fecha honesta y el motivo lo explica.
     */
    private static Availability assess(Resource resource, List<Occupancy> found, Instant from, Instant to,
                                       Instant now) {
        List<Occupancy> overlapping = found.stream().filter(occupancy -> occupancy.overlaps(from, to)).toList();
        DisplayedStatus status = DisplayedStatus.resolve(resource.operationalStatus(), overlapping, from, to, now);
        List<Occupancy> deciding = overlapping.stream()
                .filter(occupancy -> occupancy.type().displayedStatus() == status)
                .toList();
        if (deciding.isEmpty()) {
            return Availability.of(status);
        }
        if (deciding.stream().anyMatch(occupancy -> occupancy.overdueAt(now))) {
            return new Availability(status, null, true);
        }
        Instant until = deciding.stream().map(Occupancy::dueAt).max(Comparator.naturalOrder()).orElse(null);
        return new Availability(status, until);
    }
}
