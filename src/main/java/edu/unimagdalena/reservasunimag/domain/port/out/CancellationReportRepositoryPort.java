package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReportFilter;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReportPage;

import java.util.Optional;
import java.util.UUID;

/** El historial de UC11. Las escrituras se llaman dentro de {@link TransactionPort#required}. */
public interface CancellationReportRepositoryPort {

    /**
     * Registra el cierre de la reserva como cancelación y guarda el reporte.
     *
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.ReservationAlreadyClosedException si la reserva ya tenía un cierre
     */
    void insert(CancellationReport report);

    /** El reporte con su entrega; sin {@code resourceName}, que no se guarda. */
    Optional<CancellationReport> findByReservation(UUID reservationId);

    CancellationReportPage search(CancellationReportFilter filter, PageRequest page);
}
