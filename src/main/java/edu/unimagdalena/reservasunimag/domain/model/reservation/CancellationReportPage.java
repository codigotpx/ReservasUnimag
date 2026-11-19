package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;

/**
 * Una página del listado de auditoría. {@code summary} cuenta sobre el filtro completo y no sobre la página: la
 * pregunta es "¿se entregó todo?", y la respuesta no puede depender de en qué página esté quien mira.
 */
public record CancellationReportPage(Page<CancellationReport> page, Summary summary) {

    public record Summary(long total, long sent, long pending, long failed) {
    }
}
