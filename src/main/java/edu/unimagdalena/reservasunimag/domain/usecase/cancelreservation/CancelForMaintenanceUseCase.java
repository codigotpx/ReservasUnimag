package edu.unimagdalena.reservasunimag.domain.usecase.cancelreservation;

import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Cancellation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.MaintenanceCancellation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.MaintenanceCancellation.Affected;
import edu.unimagdalena.reservasunimag.domain.model.reservation.MaintenanceCancellation.Skipped;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.port.in.CancelForMaintenancePort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;
import edu.unimagdalena.reservasunimag.domain.port.out.UserRepositoryPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * UC4 FR-010: el recurso queda fuera de servicio y sus reservas futuras se cancelan con
 * {@code CANCELADA_POR_RECURSO_NO_DISPONIBLE}, sin penalizar a nadie. Reusa el mismo camino de la cancelación, así que
 * cada una deja su evento. Un activo ya entregado se salta: no se le puede quitar de las manos a nadie.
 */
public class CancelForMaintenanceUseCase implements CancelForMaintenancePort {

    static final String ASSET_ALREADY_PICKED_UP = "ASSET_ALREADY_PICKED_UP";

    private final ReservationRepositoryPort reservations;
    private final UserRepositoryPort users;
    private final InventoryPort inventory;
    private final ReservationCanceller canceller;
    private final TransactionPort transactions;
    private final Clock clock;

    public CancelForMaintenanceUseCase(ReservationRepositoryPort reservations, UserRepositoryPort users,
                                       InventoryPort inventory, ReservationCanceller canceller,
                                       TransactionPort transactions, Clock clock) {
        this.reservations = reservations;
        this.users = users;
        this.inventory = inventory;
        this.canceller = canceller;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public MaintenanceCancellation preview(String resourceId) {
        Instant now = clock.now();
        List<Reservation> upcoming = reservations.findUpcomingByResource(resourceId, now);
        String name = resourceNameOrNotFound(resourceId, upcoming.isEmpty());
        List<Affected> affected = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (Reservation reservation : upcoming) {
            if (reservation.inHands()) {
                skipped.add(new Skipped(reservation, ASSET_ALREADY_PICKED_UP));
            } else {
                affected.add(new Affected(reservation, holderOf(reservation)));
            }
        }
        return new MaintenanceCancellation(resourceId, name, affected, skipped, null);
    }

    @Override
    public MaintenanceCancellation apply(String resourceId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("el motivo es obligatorio: va al Módulo 3 y a la auditoría");
        }
        Instant now = clock.now();
        String name = resourceNameOrNotFound(resourceId,
                reservations.findUpcomingByResource(resourceId, now).isEmpty());

        return transactions.required(() -> {
            List<Affected> affected = new ArrayList<>();
            List<Skipped> skipped = new ArrayList<>();
            // Se vuelven a leer dentro de la transacción: lo que se cancela es lo que hay ahora, no lo previsualizado.
            for (Reservation upcoming : reservations.findUpcomingByResource(resourceId, now)) {
                Optional<Reservation> locked = reservations.lockById(upcoming.id());
                if (locked.isEmpty() || locked.get().status() != ReservationStatus.CONFIRMADA) {
                    continue;
                }
                Reservation reservation = locked.get();
                if (reservation.inHands()) {
                    skipped.add(new Skipped(reservation, ASSET_ALREADY_PICKED_UP));
                    continue;
                }
                Cancellation cancellation = new Cancellation(CancellationOrigin.RESOURCE_UNAVAILABLE, reason.strip(),
                        null, now);
                affected.add(new Affected(canceller.cancel(reservation, cancellation, name), holderOf(reservation)));
            }
            return new MaintenanceCancellation(resourceId, name, affected, skipped, now);
        });
    }

    /** Sin reservas por delante y sin ficha en el Módulo 1, el recurso no existe; con reservas, el nombre es un extra. */
    private String resourceNameOrNotFound(String resourceId, boolean noReservations) {
        Optional<Resource> resource;
        try {
            resource = inventory.details(resourceId);
        } catch (ExternalServiceUnavailableException unavailable) {
            return null;
        }
        if (resource.isEmpty() && noReservations) {
            throw new ResourceNotFoundException(resourceId);
        }
        return resource.map(Resource::name).orElse(null);
    }

    private ReservationHolder holderOf(Reservation reservation) {
        return users.findById(reservation.userId()).map(ReservationHolder::of).orElse(null);
    }
}
