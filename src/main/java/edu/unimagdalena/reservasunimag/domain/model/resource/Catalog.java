package edu.unimagdalena.reservasunimag.domain.model.resource;

import java.util.List;

/** Lo que devolvió el Módulo 1. {@code truncated} indica que superó el tope de páginas y no se recorrió entero. */
public record Catalog(List<Resource> resources, boolean truncated) {

    public Catalog {
        resources = List.copyOf(resources);
    }
}
