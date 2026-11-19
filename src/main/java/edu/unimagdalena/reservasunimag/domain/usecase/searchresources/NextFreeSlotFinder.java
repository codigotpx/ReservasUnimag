package edu.unimagdalena.reservasunimag.domain.usecase.searchresources;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Occupancy;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.DisplayedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.OccupancyRepositoryPort;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Escenario 7: cuando nada es seleccionable, busca la primera franja de la misma duración, con inicio cada
 * {@code step} desde el fin de la consultada y hasta las 22:00 del mismo día, en la que algún recurso esté libre.
 * Usa el catálogo ya traído y una sola consulta de ocupaciones para el resto del día.
 */
public class NextFreeSlotFinder {

    private final OccupancyRepositoryPort occupancies;
    private final Clock clock;
    private final int stepMinutes;

    public NextFreeSlotFinder(OccupancyRepositoryPort occupancies, Clock clock, Duration step) {
        if (step.toMinutes() < 1) {
            throw new IllegalArgumentException("step debe ser de al menos un minuto");
        }
        this.occupancies = occupancies;
        this.clock = clock;
        this.stepMinutes = (int) step.toMinutes();
    }

    public Optional<TimeSlot> find(List<Resource> resources, TimeSlot slot) {
        int length = minutes(slot.end()) - minutes(slot.start());
        int closing = minutes(TimeSlot.CLOSING);
        int firstStart = minutes(slot.end());
        if (resources.isEmpty() || firstStart + length > closing) {
            return Optional.empty();
        }
        Instant restOfDayEnds = slot.date().atTime(TimeSlot.CLOSING).atZone(TimeSlot.ZONE).toInstant();
        Map<String, List<Occupancy>> byResource = occupancies
                .findOccupancies(resources.stream().map(Resource::id).toList(), slot.endsAt(), restOfDayEnds)
                .stream()
                .collect(Collectors.groupingBy(Occupancy::resourceId));
        Instant now = clock.now();
        for (int start = firstStart; start + length <= closing; start += stepMinutes) {
            TimeSlot candidate = new TimeSlot(slot.date(), LocalTime.ofSecondOfDay(start * 60L),
                    LocalTime.ofSecondOfDay((start + length) * 60L));
            boolean anyFree = resources.stream().anyMatch(resource -> DisplayedStatus.resolve(
                    resource.operationalStatus(), byResource.getOrDefault(resource.id(), List.of()), candidate, now)
                    .selectable());
            if (anyFree) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static int minutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }
}
