package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceOccupancy;

import java.time.LocalDate;

/**
 * El calendario de ocupación de un espacio para Dirección de Programa: qué clases y reservas lo tienen tomado entre dos
 * días. Solo lee; la disponibilidad de una franja sigue siendo de UC8.
 */
public interface ViewResourceOccupancyPort {

    /** Lanza ResourceNotFoundException si el recurso no existe e InvalidFilterException si el rango no tiene sentido. */
    ResourceOccupancy view(String resourceId, LocalDate from, LocalDate to);
}
