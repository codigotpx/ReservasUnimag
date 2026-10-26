package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReportFilter;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReportPage;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;

import java.util.UUID;

/** UC11 FR-010: qué se le reportó al Módulo 3 sobre una cancelación, y cómo acabó cada envío. */
public interface QueryCancellationReportsPort {

    /**
     * El reporte de una reserva, para su titular o para Dirección de Programa.
     *
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException si no existe, no está cancelada o es de otra persona
     */
    CancellationReport find(CurrentUser user, UUID reservationId);

    /** El listado para auditar, del más reciente al más antiguo. */
    CancellationReportPage list(CancellationReportFilter filter, PageRequest page);
}
