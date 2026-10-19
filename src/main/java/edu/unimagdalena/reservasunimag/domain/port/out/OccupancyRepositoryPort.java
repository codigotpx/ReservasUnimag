package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Occupancy;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Reservas, préstamos y bloqueos académicos del Módulo 2, vistos como lo que ocupan. */
public interface OccupancyRepositoryPort {

    /**
     * Las ocupaciones confirmadas de esos recursos que se cruzan con {@code [from, to)}, más los préstamos
     * entregados y sin devolver que empezaron antes de {@code to}, aunque ya hayan vencido.
     */
    List<Occupancy> findOccupancies(Collection<String> resourceIds, Instant from, Instant to);
}
