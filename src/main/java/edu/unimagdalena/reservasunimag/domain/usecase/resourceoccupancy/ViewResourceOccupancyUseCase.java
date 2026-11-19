package edu.unimagdalena.reservasunimag.domain.usecase.resourceoccupancy;

import edu.unimagdalena.reservasunimag.domain.model.error.InvalidFilterException;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceOccupancy;
import edu.unimagdalena.reservasunimag.domain.port.in.ViewResourceOccupancyPort;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.ScheduledUseRepositoryPort;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Cruza la ficha del Módulo 1 con las reservas confirmadas del rango. El rango va por días completos en la zona de la
 * universidad y no pasa de 31, para que una consulta no recorra un semestre entero.
 */
public class ViewResourceOccupancyUseCase implements ViewResourceOccupancyPort {

    static final int MAX_DAYS = 31;

    private final InventoryPort inventory;
    private final ScheduledUseRepositoryPort uses;

    public ViewResourceOccupancyUseCase(InventoryPort inventory, ScheduledUseRepositoryPort uses) {
        this.inventory = inventory;
        this.uses = uses;
    }

    @Override
    public ResourceOccupancy view(String resourceId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new InvalidFilterException("to", "debe ser igual o posterior a from");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new InvalidFilterException("to", "el rango no puede pasar de " + MAX_DAYS + " días");
        }
        Resource resource = inventory.details(resourceId).orElseThrow(() -> new ResourceNotFoundException(resourceId));
        return new ResourceOccupancy(resource, from, to, uses.findByResource(resource.id(),
                from.atStartOfDay(TimeSlot.ZONE).toInstant(),
                to.plusDays(1).atStartOfDay(TimeSlot.ZONE).toInstant()));
    }
}
