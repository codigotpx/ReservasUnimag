package edu.unimagdalena.reservasunimag.domain.usecase.reserveresources;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicBlock;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.ClassSession;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason;
import edu.unimagdalena.reservasunimag.domain.port.in.ReportReservationPort;
import edu.unimagdalena.reservasunimag.domain.port.in.UpdateResourceStatusPort;
import edu.unimagdalena.reservasunimag.domain.port.out.AcademicBlockRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRepositoryPort;

import java.util.UUID;

/**
 * La rama académica de UC2: cada clase entra por aquí, como dibuja el {@code <<include>>} de UC3 (P-06), y pasa igual
 * por UC7 y UC10. Se salta lo que no aplica a una clase —sanción, cupo, tope de 2 horas y titular— y recibe la ficha
 * del recurso ya consultada, para no preguntarle al Módulo 1 una vez por sesión. La restricción de exclusión sigue
 * siendo la garantía final contra un cruce.
 */
public class AcademicBlockCreator {

    private final ReservationRepositoryPort reservations;
    private final AcademicBlockRepositoryPort academicBlocks;
    private final UpdateResourceStatusPort updateStatus;
    private final ReportReservationPort reportReservation;
    private final Clock clock;

    public AcademicBlockCreator(ReservationRepositoryPort reservations, AcademicBlockRepositoryPort academicBlocks,
                                UpdateResourceStatusPort updateStatus, ReportReservationPort reportReservation,
                                Clock clock) {
        this.reservations = reservations;
        this.academicBlocks = academicBlocks;
        this.updateStatus = updateStatus;
        this.reportReservation = reportReservation;
        this.clock = clock;
    }

    /**
     * Crea el bloqueo de una sesión. Debe llamarse dentro de una transacción. {@code scheduleId} y {@code academicTerm}
     * son {@code null} en una necesidad extraordinaria.
     *
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.ResourceTakenException si algo confirmado ocupa ya esa franja
     */
    public Reservation create(ClassSession session, Resource resource, UUID scheduleId, String academicTerm) {
        Reservation block = new Reservation(UUID.randomUUID(), null, resource.id(), resource.category(),
                ReservationOrigin.ACADEMICO, ReservationStatus.CONFIRMADA, session.startsAt(), session.endsAt(),
                clock.now(), null);
        reservations.insert(block);
        academicBlocks.insert(block.id(), scheduleId, new AcademicBlock(block.id(), resource.id(), session.startsAt(),
                session.endsAt(), session.academic(), session.type(), academicTerm, session.sessionKey()));
        updateStatus.record(block, StatusChangeReason.ACADEMIC_BLOCK_CREATED);
        reportReservation.reportAcademicCreated(block, resource, session.academic());
        return block;
    }
}
