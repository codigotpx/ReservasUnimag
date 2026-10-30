package edu.unimagdalena.reservasunimag.domain.model.compliance;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;

/**
 * Una página de la auditoría de consultas. {@code summary} cuenta sobre el filtro completo; {@code notVerified} es el
 * número que vigila SC-004: cuántas veces no se pudo comprobar.
 */
public record SanctionCheckPage(Page<SanctionCheck> page, Summary summary) {

    public record Summary(long total, long clear, long sanctioned, long noHistory, long notVerified) {
    }
}
