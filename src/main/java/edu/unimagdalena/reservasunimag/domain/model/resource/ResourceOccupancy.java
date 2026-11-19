package edu.unimagdalena.reservasunimag.domain.model.resource;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** La ocupación de un espacio entre dos días, ambos incluidos, con su ficha del Módulo 1. */
public record ResourceOccupancy(Resource resource, LocalDate from, LocalDate to, List<ScheduledUse> uses) {

    public ResourceOccupancy {
        Objects.requireNonNull(resource, "resource");
        uses = List.copyOf(uses);
    }
}
