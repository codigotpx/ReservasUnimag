package edu.unimagdalena.reservasunimag.domain.model.common;

/** Página pedida, en base 1. El tamaño lo decide el Módulo 2, no quien consulta (UC1 FR-009). */
public record PageRequest(int page, int size) {

    public PageRequest {
        if (page < 1) {
            throw new IllegalArgumentException("page debe ser mayor o igual a 1");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size debe ser mayor o igual a 1");
        }
    }

    public int offset() {
        return (page - 1) * size;
    }
}
