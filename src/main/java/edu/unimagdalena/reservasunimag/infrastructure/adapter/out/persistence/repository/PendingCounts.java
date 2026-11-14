package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

/**
 * Dos conteos de una lista de pendientes en una sola consulta. En las devoluciones son el total y los cercanos al
 * umbral de pérdida; en las revisiones, las estudiantiles y las académicas.
 */
public record PendingCounts(long first, long second) {
}
