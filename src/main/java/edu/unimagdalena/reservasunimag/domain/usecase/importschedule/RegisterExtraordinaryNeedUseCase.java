package edu.unimagdalena.reservasunimag.domain.usecase.importschedule;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicBlock;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.BlockType;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.ClassSession;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.ExtraordinaryNeed;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.ExtraordinaryNeedResult;
import edu.unimagdalena.reservasunimag.domain.model.error.AcademicConflictException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationDeniedException;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceTakenException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.upload.AffectedReservation;
import edu.unimagdalena.reservasunimag.domain.model.upload.RowVerdict;
import edu.unimagdalena.reservasunimag.domain.port.in.CancelReservationPort;
import edu.unimagdalena.reservasunimag.domain.port.in.RegisterExtraordinaryNeedPort;
import edu.unimagdalena.reservasunimag.domain.port.out.AcademicBlockRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;
import edu.unimagdalena.reservasunimag.domain.port.out.UserRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.usecase.reserveresources.AcademicBlockCreator;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * UC3 FR-005: la misma maquinaria que la carga, con una sola franja. Se impone sobre las reservas estudiantiles, pero
 * no sobre otra clase (FR-006) ni sobre un activo que ya salió del inventario (FR-010). El tope de 2 horas no aplica:
 * es académica. Al ser una sola franja, el impacto se muestra en la misma petición y se aplica con {@code confirm}.
 */
public class RegisterExtraordinaryNeedUseCase implements RegisterExtraordinaryNeedPort {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final InventoryPort inventory;
    private final AcademicBlockRepositoryPort academicBlocks;
    private final ReservationRepositoryPort reservations;
    private final UserRepositoryPort users;
    private final CancelReservationPort cancelReservation;
    private final AcademicBlockCreator blockCreator;
    private final TransactionPort transactions;

    public RegisterExtraordinaryNeedUseCase(InventoryPort inventory, AcademicBlockRepositoryPort academicBlocks,
                                            ReservationRepositoryPort reservations, UserRepositoryPort users,
                                            CancelReservationPort cancelReservation,
                                            AcademicBlockCreator blockCreator, TransactionPort transactions) {
        this.inventory = inventory;
        this.academicBlocks = academicBlocks;
        this.reservations = reservations;
        this.users = users;
        this.cancelReservation = cancelReservation;
        this.blockCreator = blockCreator;
        this.transactions = transactions;
    }

    @Override
    public ExtraordinaryNeedResult register(ExtraordinaryNeed need, boolean confirm) {
        TimeSlot slot = new TimeSlot(need.date(), need.start(), need.end());
        Resource resource = inventory.details(need.resourceId())
                .orElseThrow(() -> new ResourceNotFoundException(need.resourceId()));
        if (resource.operationalStatus() == OperationalStatus.EN_MANTENIMIENTO) {
            throw new AcademicConflictException(RowVerdict.RESOURCE_UNDER_MAINTENANCE,
                    "El recurso está en mantenimiento. Esta necesidad necesita otro espacio.", null);
        }
        ClassSession session = new ClassSession(resource.id(), slot.startsAt(), slot.endsAt(), need.academic(),
                BlockType.EXTRAORDINARIO, null);
        List<AffectedReservation> affected = affected(session);
        RowVerdict verdict = affected.isEmpty() ? RowVerdict.OK : RowVerdict.DISPLACES_RESERVATIONS;
        if (!confirm) {
            return new ExtraordinaryNeedResult(false, verdict, resource.id(), resource.name(), session.startsAt(),
                    session.endsAt(), need.academic(), affected, null);
        }
        try {
            Reservation block = transactions.required(() -> {
                for (AffectedReservation reservation : affected) {
                    cancelReservation.cancelBySystem(reservation.reservationId(), CancellationOrigin.ACADEMIC_PRIORITY,
                            "La franja se asignó a actividad docente: " + need.academic().label() + ".");
                }
                return blockCreator.create(session, resource, null, null);
            });
            return new ExtraordinaryNeedResult(true, verdict, resource.id(), resource.name(), session.startsAt(),
                    session.endsAt(), need.academic(), affected, block);
        } catch (ResourceTakenException taken) {
            throw ReservationDeniedException.resourceTaken(resource.id());
        }
    }

    /** Las reservas que la necesidad cancelaría; lanza si la franja no se puede tomar. */
    private List<AffectedReservation> affected(ClassSession session) {
        for (AcademicBlock block : academicBlocks.findConfirmed(List.of(session.resourceId()), session.startsAt(),
                session.endsAt())) {
            var start = block.startsAt().atZone(TimeSlot.ZONE);
            var end = block.endsAt().atZone(TimeSlot.ZONE);
            throw new AcademicConflictException(RowVerdict.ACADEMIC_CLASH, "Se cruza con la clase "
                    + block.academic().label() + ", de " + start.format(HOUR) + " a " + end.format(HOUR) + ". Una "
                    + "clase no desplaza a otra: resuélvanlo entre los responsables.", block.academic());
        }
        List<Reservation> occupying = reservations.findStudentOccupying(List.of(session.resourceId()),
                session.startsAt(), session.endsAt()).stream()
                .filter(reservation -> SlotConflicts.occupies(reservation, session))
                .toList();
        if (occupying.stream().anyMatch(SlotConflicts::untouchable)) {
            throw new AcademicConflictException(RowVerdict.ASSET_ALREADY_PICKED_UP, "El recurso ya está en manos "
                    + "de una persona o en uso en esa franja. Hay que pedirlo de vuelta antes de bloquearlo.", null);
        }
        return occupying.stream()
                .map(reservation -> new AffectedReservation(reservation.id(),
                        users.findById(reservation.userId()).map(ReservationHolder::of).orElse(null),
                        reservation.startsAt(), reservation.endsAt(), reservation.category()))
                .toList();
    }
}
