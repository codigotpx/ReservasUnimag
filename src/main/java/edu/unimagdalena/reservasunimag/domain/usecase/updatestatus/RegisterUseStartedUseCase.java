package edu.unimagdalena.reservasunimag.domain.usecase.updatestatus;

import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationDeniedException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.error.UseStartRejectedException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.AcademicStarts;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.UseStartResult;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.port.in.RegisterUseStartedPort;
import edu.unimagdalena.reservasunimag.domain.port.in.UpdateResourceStatusPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.StatusChangeRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UC7 FR-004: un escritor, dos disparadores. La marca de inicio de uso se escribe solo si estaba vacía, así que un
 * segundo registro no vuelve a marcar, registrar ni avisar (FR-008). La ficha del recurso se le pide al Módulo 1
 * antes de abrir la transacción: si lo tiene en mantenimiento no se marca nada (FR-005), y si no responde se marca
 * igual —la persona ya está en el salón— y el aviso queda en la outbox.
 */
public class RegisterUseStartedUseCase implements RegisterUseStartedPort {

    private final ReservationRepositoryPort reservations;
    private final StatusChangeRepositoryPort statusChanges;
    private final UpdateResourceStatusPort updateStatus;
    private final InventoryPort inventory;
    private final TransactionPort transactions;
    private final Clock clock;
    private final Duration window;

    /** {@code window} es cuánto antes del inicio se admite registrar la llegada: el mismo plazo con que se cancela. */
    public RegisterUseStartedUseCase(ReservationRepositoryPort reservations, StatusChangeRepositoryPort statusChanges,
                                     UpdateResourceStatusPort updateStatus, InventoryPort inventory,
                                     TransactionPort transactions, Clock clock, Duration window) {
        this.reservations = reservations;
        this.statusChanges = statusChanges;
        this.updateStatus = updateStatus;
        this.inventory = inventory;
        this.transactions = transactions;
        this.clock = clock;
        this.window = window;
    }

    @Override
    public UseStartResult startByHolder(CurrentUser user, UUID reservationId) {
        Reservation seen = reservations.findById(reservationId)
                .filter(reservation -> user.id().equals(reservation.userId()))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (seen.useStarted()) {
            return alreadyStarted(seen);
        }
        requireStartable(seen, clock.now());
        requireNotUnderMaintenance(seen.resourceId());

        return transactions.required(() -> {
            Reservation locked = reservations.lockById(reservationId)
                    .orElseThrow(() -> new ReservationNotFoundException(reservationId));
            if (locked.useStarted()) {
                return alreadyStarted(locked);
            }
            Instant now = clock.now();
            requireStartable(locked, now);
            return start(locked, now);
        });
    }

    @Override
    public AcademicStarts startDueAcademicBlocks() {
        List<Reservation> due = reservations.findAcademicBlocksToStart(clock.now());
        if (due.isEmpty()) {
            return new AcademicStarts(0, 0, 0);
        }
        // Por lote y no una por una: a las 08:00 arrancan muchas clases a la vez.
        Map<String, OperationalStatus> statuses;
        try {
            statuses = inventory.operationalStatus(due.stream().map(Reservation::resourceId).distinct().toList());
        } catch (ExternalServiceUnavailableException unavailable) {
            statuses = Map.of();
        }
        int started = 0;
        int skipped = 0;
        int failed = 0;
        for (Reservation block : due) {
            if (statuses.get(block.resourceId()) == OperationalStatus.EN_MANTENIMIENTO) {
                skipped++;
                continue;
            }
            try {
                boolean done = transactions.required(() -> {
                    Reservation locked = reservations.lockById(block.id()).orElse(null);
                    if (locked == null || locked.useStarted() || locked.status() != ReservationStatus.CONFIRMADA) {
                        return false;
                    }
                    start(locked, clock.now());
                    return true;
                });
                started += done ? 1 : 0;
            } catch (RuntimeException failure) {
                // Una clase que falla no frena a las demás; se vuelve a intentar en la siguiente pasada.
                failed++;
            }
        }
        return new AcademicStarts(started, skipped, failed);
    }

    private UseStartResult start(Reservation locked, Instant now) {
        if (!reservations.markUseStarted(locked.id(), now)) {
            return alreadyStarted(locked);
        }
        Reservation started = locked.startUse(now);
        return new UseStartResult(started, updateStatus.record(started, StatusChangeReason.USE_STARTED), false);
    }

    private UseStartResult alreadyStarted(Reservation reservation) {
        var change = statusChanges.findUseStarted(reservation.id())
                .orElseThrow(() -> new IllegalStateException("la reserva " + reservation.id()
                        + " tiene el uso marcado sin su registro"));
        return new UseStartResult(reservation, change, true);
    }

    private void requireStartable(Reservation reservation, Instant now) {
        if (reservation.status() != ReservationStatus.CONFIRMADA
                || reservation.origin() == ReservationOrigin.ACADEMICO) {
            throw UseStartRejectedException.notStartable(reservation.status().name());
        }
        if (now.isBefore(reservation.startsAt().minus(window))) {
            throw UseStartRejectedException.tooEarly(reservation.startsAt(), window);
        }
        if (!now.isBefore(reservation.endsAt())) {
            throw UseStartRejectedException.finished(reservation.endsAt());
        }
    }

    private void requireNotUnderMaintenance(String resourceId) {
        try {
            inventory.details(resourceId)
                    .filter(resource -> resource.operationalStatus() == OperationalStatus.EN_MANTENIMIENTO)
                    .ifPresent(resource -> {
                        throw ReservationDeniedException.underMaintenance(resourceId);
                    });
        } catch (ExternalServiceUnavailableException unavailable) {
            // Sin respuesta del inventario se registra igual: bloquear a quien ya llegó sería lo peor de los dos
            // mundos.
        }
    }
}
