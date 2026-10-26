package edu.unimagdalena.reservasunimag.domain.usecase.reserveresources;

import edu.unimagdalena.reservasunimag.domain.model.error.IncompleteResourceDetailsException;
import edu.unimagdalena.reservasunimag.domain.model.error.InvalidCredentialsException;
import edu.unimagdalena.reservasunimag.domain.model.error.InvalidTimeSlotException;
import edu.unimagdalena.reservasunimag.domain.model.error.NotAnAssetException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationDeniedException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationShapeException;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceTakenException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Denial;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ExpectedDueDate;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.LoanPeriod;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Quota;
import edu.unimagdalena.reservasunimag.domain.model.reservation.QuotaUsage;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationResult;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.Availability;
import edu.unimagdalena.reservasunimag.domain.model.resource.DisplayedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.User;
import edu.unimagdalena.reservasunimag.domain.port.in.CheckAvailabilityPort;
import edu.unimagdalena.reservasunimag.domain.port.in.CheckSanctionsPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReportReservationPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReserveResourcesPort;
import edu.unimagdalena.reservasunimag.domain.port.in.UpdateResourceStatusPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.DenialRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;
import edu.unimagdalena.reservasunimag.domain.port.out.UserRepositoryPort;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Las reglas de UC2 en el orden de FR-002, devolviendo un solo código: la franja y la ficha (que no son
 * denegaciones sino un 400 o 404), la sanción, el cupo y la disponibilidad. Las llamadas a los Módulos 1 y 3 se
 * hacen fuera de la transacción; dentro solo va lo que debe ser atómico, y ahí se vuelve a comprobar lo que puede
 * haber cambiado mientras tanto. La restricción de exclusión de la base es la garantía final contra dos reservas
 * simultáneas (FR-004).
 */
public class ReserveResourcesUseCase implements ReserveResourcesPort {

    private final InventoryPort inventory;
    private final CheckSanctionsPort sanctions;
    private final CheckAvailabilityPort availability;
    private final ReservationRepositoryPort reservations;
    private final DenialRepositoryPort denials;
    private final UserRepositoryPort users;
    private final ReportReservationPort reportReservation;
    private final UpdateResourceStatusPort updateStatus;
    private final DueDateCalculator dueDates;
    private final TransactionPort transactions;
    private final Clock clock;
    private final int maxQuota;
    private final Duration maxSpaceDuration;

    public ReserveResourcesUseCase(InventoryPort inventory, CheckSanctionsPort sanctions,
                                   CheckAvailabilityPort availability, ReservationRepositoryPort reservations,
                                   DenialRepositoryPort denials, UserRepositoryPort users,
                                   ReportReservationPort reportReservation, UpdateResourceStatusPort updateStatus,
                                   DueDateCalculator dueDates, TransactionPort transactions, Clock clock,
                                   int maxQuota, Duration maxSpaceDuration) {
        this.inventory = inventory;
        this.sanctions = sanctions;
        this.availability = availability;
        this.reservations = reservations;
        this.denials = denials;
        this.users = users;
        this.reportReservation = reportReservation;
        this.updateStatus = updateStatus;
        this.dueDates = dueDates;
        this.transactions = transactions;
        this.clock = clock;
        this.maxQuota = maxQuota;
        this.maxSpaceDuration = maxSpaceDuration;
    }

    /** Lo que se va a apartar: el intervalo ocupado y, en un activo, el plazo aplicado. */
    private record Booking(Instant startsAt, Instant endsAt, Integer termBusinessDays) {
    }

    @Override
    public ReservationResult reserve(ReservationRequest request) {
        // Franja y ficha primero: una forma mal puesta o un recurso inexistente no son una denegación.
        Resource resource = inventory.details(request.resourceId())
                .orElseThrow(() -> new ResourceNotFoundException(request.resourceId()));
        Booking booking = plan(resource, request);

        try {
            return confirm(request.holder(), resource, booking);
        } catch (ReservationDeniedException denied) {
            record(request.holder().id(), resource.id(), denied);
            throw denied;
        } catch (ResourceTakenException taken) {
            ReservationDeniedException denied = ReservationDeniedException.resourceTaken(resource.id());
            record(request.holder().id(), resource.id(), denied);
            throw denied;
        }
    }

    private ReservationResult confirm(CurrentUser holder, Resource resource, Booking booking) {
        Instant now = clock.now();

        sanctions.activeSanction(holder).ifPresent(sanction -> {
            throw ReservationDeniedException.activeSanction(sanction);
        });
        requireQuota(reservations.quotaOf(holder.id(), now));
        requireAvailable(resource, booking);
        User user = users.findById(holder.id()).orElseThrow(InvalidCredentialsException::new);

        Reservation reservation = transactions.required(() -> {
            // La fila del titular serializa sus reservas; con ella tomada, lo que se compruebe ya no cambia.
            reservations.lockHolder(holder.id());
            QuotaUsage usage = reservations.quotaOf(holder.id(), now);
            requireQuota(usage);
            requireAvailable(resource, booking);

            Reservation created = Reservation.confirmed(UUID.randomUUID(), holder.id(), resource.id(),
                    resource.category(), booking.startsAt(), booking.endsAt(), now,
                    booking.termBusinessDays() == null ? null : Loan.reserved(booking.termBusinessDays()));
            reservations.insert(created);
            updateStatus.record(created, StatusChangeReason.RESERVATION_CONFIRMED);
            reportReservation.reportCreated(created, resource, user);
            return created;
        });
        return new ReservationResult(reservation, resource,
                new Quota(reservations.quotaOf(holder.id(), now).used(), maxQuota));
    }

    @Override
    public ExpectedDueDate expectedDueDate(String resourceId, LocalDate date, LocalTime pickup) {
        Resource resource = inventory.details(resourceId).orElseThrow(() -> new ResourceNotFoundException(resourceId));
        if (resource.category() != ResourceCategory.ACTIVO) {
            throw new NotAnAssetException(resourceId);
        }
        return new ExpectedDueDate(resource, loanPeriod(resource, date, pickup));
    }

    private Booking plan(Resource resource, ReservationRequest request) {
        if (resource.category() == ResourceCategory.ESPACIO) {
            if (request.start() == null || request.end() == null || request.pickup() != null) {
                throw new ReservationShapeException(
                        "Un espacio se reserva con una franja (start y end), no con una hora de recogida.");
            }
            TimeSlot slot = new TimeSlot(request.date(), request.start(), request.end())
                    .requireMaxDuration(maxSpaceDuration);
            return new Booking(slot.startsAt(), slot.endsAt(), null);
        }
        if (request.pickup() == null || request.start() != null || request.end() != null) {
            throw new ReservationShapeException(
                    "Un activo se reserva con la hora de recogida (pickup), no con una franja.");
        }
        LoanPeriod period = loanPeriod(resource, request.date(), request.pickup());
        return new Booking(period.pickup(), period.dueAt(), period.termBusinessDays());
    }

    private LoanPeriod loanPeriod(Resource resource, LocalDate date, LocalTime pickup) {
        // Se recoge de 06:00 a antes de las 22:00: a las 22:00 ya no queda ocupación que apartar.
        if (pickup.isBefore(TimeSlot.OPENING) || !pickup.isBefore(TimeSlot.CLOSING)) {
            throw new InvalidTimeSlotException(InvalidTimeSlotException.Reason.TIME_SLOT_OUTSIDE_WINDOW, date, pickup,
                    pickup);
        }
        if (resource.loanTermBusinessDays() == null) {
            throw new IncompleteResourceDetailsException(resource.id());
        }
        return dueDates.calculate(date, pickup, resource.loanTermBusinessDays());
    }

    private void requireQuota(QuotaUsage usage) {
        if (usage.used() >= maxQuota) {
            throw ReservationDeniedException.quotaExhausted(usage.used(), maxQuota, usage.nextRelease());
        }
    }

    /** Traduce el motivo por el que no está libre al código que corresponde, sin decir quién lo tiene. */
    private void requireAvailable(Resource resource, Booking booking) {
        Availability found = availability.checkOne(resource, booking.startsAt(), booking.endsAt());
        if (found.selectable()) {
            return;
        }
        DisplayedStatus status = found.status();
        switch (status) {
            case BLOQUEO_ACADEMICO -> throw ReservationDeniedException.academicConflict(resource.id(),
                    found.occupiedUntil());
            case EN_MANTENIMIENTO -> throw ReservationDeniedException.underMaintenance(resource.id());
            default -> throw ReservationDeniedException.resourceTaken(resource.id());
        }
    }

    private void record(UUID userId, String resourceId, ReservationDeniedException denied) {
        denials.record(new Denial(UUID.randomUUID(), userId, resourceId, denied.code(), clock.now()));
    }
}
