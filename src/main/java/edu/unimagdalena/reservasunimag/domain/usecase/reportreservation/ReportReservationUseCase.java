package edu.unimagdalena.reservasunimag.domain.usecase.reportreservation;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecord;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordReport;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.user.User;
import edu.unimagdalena.reservasunimag.domain.port.in.ReportReservationPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.Module3NotifierPort;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRecordRepositoryPort;

import java.util.UUID;

/**
 * UC10: arma la ficha de la reserva, la guarda como constancia y se la entrega al notificador, que la deja en la
 * outbox. Las dos escrituras van en la transacción de la reserva, así que una denegación no deja ninguna (FR-007), y
 * el {@code eventId} las une. El Módulo 2 no decide consecuencias: solo cuenta qué se apartó (FR-003).
 */
public class ReportReservationUseCase implements ReportReservationPort {

    private final ReservationRecordRepositoryPort records;
    private final Module3NotifierPort notifier;
    private final Clock clock;

    public ReportReservationUseCase(ReservationRecordRepositoryPort records, Module3NotifierPort notifier,
                                    Clock clock) {
        this.records = records;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Override
    public void reportCreated(Reservation reservation, Resource resource, User holder) {
        report(ReservationRecord.student(UUID.randomUUID(), reservation, resource, ReservationHolder.of(holder),
                clock.now()));
    }

    @Override
    public void reportAcademicCreated(Reservation reservation, Resource resource, AcademicData academic) {
        report(ReservationRecord.academic(UUID.randomUUID(), reservation, resource, academic, clock.now()));
    }

    private void report(ReservationRecord record) {
        records.insert(ReservationRecordReport.pending(record));
        notifier.reservationCreated(record);
    }
}
