package edu.unimagdalena.reservasunimag.domain.model.common;

import java.util.List;

/** Una página de resultados con el total del conjunto completo, no solo de la página (UC1 FR-013). */
public record Page<T>(List<T> items, int page, int pageSize, long total) {

    public Page {
        items = List.copyOf(items);
    }

    /** Recorta una lista ya ordenada a la página pedida. */
    public static <T> Page<T> of(List<T> all, PageRequest request) {
        int from = Math.min(request.offset(), all.size());
        int to = Math.min(from + request.size(), all.size());
        return new Page<>(all.subList(from, to), request.page(), request.size(), all.size());
    }

    /** {@code 1} cuando no hay resultados, para que siempre exista la página que se está viendo. */
    public int totalPages() {
        return total == 0 ? 1 : (int) ((total + pageSize - 1) / pageSize);
    }
}
