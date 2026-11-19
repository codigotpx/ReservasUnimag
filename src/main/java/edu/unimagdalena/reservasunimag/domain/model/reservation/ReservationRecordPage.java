package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;

/**
 * Una página del listado de fichas. {@code summary} cuenta sobre el filtro completo, igual que en UC11, y además
 * cuántas son sancionables: con {@code origin=ACADEMICO} tiene que dar cero, y esa es la comprobación de SC-006.
 */
public record ReservationRecordPage(Page<ReservationRecordReport> page, Summary summary) {

    public record Summary(long total, long sent, long pending, long failed, long sanctionable) {
    }
}
