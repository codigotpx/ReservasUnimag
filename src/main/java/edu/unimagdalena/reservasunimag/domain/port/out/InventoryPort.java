package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceDetails;
import java.util.Optional;

/** Consulta del inventario del Módulo 1: la ficha de un recurso con su plazo de préstamo. */
public interface InventoryPort {

    /** Devuelve la ficha del recurso, o vacío si el Módulo 1 no la tiene. */
    Optional<ResourceDetails> details(String resourceId);
}
