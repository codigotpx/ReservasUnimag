package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

/**
 * Los conteos de un historial de envíos al Módulo 3, en una sola consulta. {@code sanctionable} solo tiene sentido
 * en las fichas (UC10); en las cancelaciones la consulta no lo pide.
 */
public record DeliveryCounts(long total, long sent, long pending, long failed, long sanctionable) {
}
