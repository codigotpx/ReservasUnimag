package edu.unimagdalena.reservasunimag.domain.usecase.reportreservation;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecord;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.user.User;
import edu.unimagdalena.reservasunimag.domain.port.in.ReportReservationPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.Module3NotifierPort;

/** UC10: arma la ficha de la reserva y se la entrega al notificador, que la deja en la outbox. */
public class ReportReservationUseCase implements ReportReservationPort {

    private final Module3NotifierPort notifier;
    private final Clock clock;

    public ReportReservationUseCase(Module3NotifierPort notifier, Clock clock) {
        this.notifier = notifier;
        this.clock = clock;
    }

    @Override
    public void reportCreated(Reservation reservation, Resource resource, User holder) {
        notifier.reservationCreated(
                new ReservationRecord(reservation, resource, ReservationHolder.of(holder), clock.now()));
    }
}
