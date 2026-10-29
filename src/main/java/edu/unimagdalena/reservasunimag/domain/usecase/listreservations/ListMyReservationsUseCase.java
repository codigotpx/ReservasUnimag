package edu.unimagdalena.reservasunimag.domain.usecase.listreservations;

import edu.unimagdalena.reservasunimag.domain.model.compliance.Absence;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationPolicy;
import edu.unimagdalena.reservasunimag.domain.model.reservation.MyReservations;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Quota;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.port.in.ListMyReservationsPort;
import edu.unimagdalena.reservasunimag.domain.port.out.AbsenceRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRepositoryPort;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Las reservas de quien pregunta. El nombre del recurso es del Módulo 1; si no responde, la lista se entrega igual
 * sin nombres, porque la reserva, su vencimiento y el cupo son datos nuestros.
 */
public class ListMyReservationsUseCase implements ListMyReservationsPort {

    private final ReservationRepositoryPort reservations;
    private final InventoryPort inventory;
    private final AbsenceRepositoryPort absences;
    private final CancellationPolicy cancellationPolicy;
    private final Clock clock;
    private final int maxQuota;

    public ListMyReservationsUseCase(ReservationRepositoryPort reservations, InventoryPort inventory,
                                     AbsenceRepositoryPort absences, CancellationPolicy cancellationPolicy,
                                     Clock clock, int maxQuota) {
        this.reservations = reservations;
        this.inventory = inventory;
        this.absences = absences;
        this.cancellationPolicy = cancellationPolicy;
        this.clock = clock;
        this.maxQuota = maxQuota;
    }

    @Override
    public MyReservations list(CurrentUser user, boolean onlyActive) {
        Instant now = clock.now();
        List<Reservation> mine = reservations.findByHolder(user.id(), onlyActive, now).stream()
                .sorted(Comparator.comparing(Reservation::startsAt).thenComparing(Reservation::id))
                .toList();

        Map<String, String> names = new HashMap<>();
        boolean catalogUnavailable = false;
        try {
            for (String resourceId : mine.stream().map(Reservation::resourceId).distinct().toList()) {
                inventory.details(resourceId).map(Resource::name).ifPresent(name -> names.put(resourceId, name));
            }
        } catch (ExternalServiceUnavailableException unavailable) {
            names.clear();
            catalogUnavailable = true;
        }

        Map<UUID, Absence> absent = mine.isEmpty() ? Map.of()
                : absences.findActiveByReservations(mine.stream().map(Reservation::id).toList());
        List<MyReservations.Item> items = mine.stream()
                .map(reservation -> new MyReservations.Item(reservation, names.get(reservation.resourceId()),
                        reservation.overdue(now), cancellationPolicy.cancellableByHolder(reservation, now),
                        absent.get(reservation.id())))
                .toList();
        return new MyReservations(items, new Quota(reservations.quotaOf(user.id(), now).used(), maxQuota),
                catalogUnavailable);
    }
}
