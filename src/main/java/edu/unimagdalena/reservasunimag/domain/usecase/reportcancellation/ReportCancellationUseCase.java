package edu.unimagdalena.reservasunimag.domain.usecase.reportcancellation;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Cancellation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.user.User;
import edu.unimagdalena.reservasunimag.domain.port.in.ReportCancellationPort;
import edu.unimagdalena.reservasunimag.domain.port.out.CancellationReportRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Module3NotifierPort;

import java.util.UUID;

/**
 * UC11: arma el reporte de la cancelación, lo guarda como constancia y lo entrega al notificador, que lo deja en la
 * outbox. Las dos escrituras van en la transacción de la cancelación, y el {@code eventId} las une. El Módulo 2 no
 * decide consecuencias: solo cuenta qué pasó y si se le puede atribuir a la persona (FR-005).
 */
public class ReportCancellationUseCase implements ReportCancellationPort {

    private final CancellationReportRepositoryPort reports;
    private final Module3NotifierPort notifier;

    public ReportCancellationUseCase(CancellationReportRepositoryPort reports, Module3NotifierPort notifier) {
        this.reports = reports;
        this.notifier = notifier;
    }

    @Override
    public void reportCancelled(Reservation cancelled, Cancellation cancellation, String resourceName, User holder) {
        CancellationReport report = CancellationReport.pending(UUID.randomUUID(), cancelled, cancellation,
                resourceName, holder == null ? null : ReservationHolder.of(holder));
        reports.insert(report);
        notifier.reservationCancelled(report);
    }
}
