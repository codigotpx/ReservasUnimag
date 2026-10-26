package edu.unimagdalena.reservasunimag.domain.usecase.cancelreservation;

import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Cancellation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason;
import edu.unimagdalena.reservasunimag.domain.model.user.User;
import edu.unimagdalena.reservasunimag.domain.port.in.ReportCancellationPort;
import edu.unimagdalena.reservasunimag.domain.port.in.UpdateResourceStatusPort;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.UserRepositoryPort;

/**
 * Lo que hacen igual las tres ramas de UC4: dejar la reserva en su estado de cancelación —con eso se libera la
 * ocupación y el cupo, que se cuentan sobre las confirmadas—, dejarlo en el historial de UC7 y reportarlo por el
 * {@code <<include>>} de UC11. No hay contador de cupo que decrementar (UC2 FR-008).
 */
public class ReservationCanceller {

    private final ReservationRepositoryPort reservations;
    private final UserRepositoryPort users;
    private final InventoryPort inventory;
    private final ReportCancellationPort reportCancellation;
    private final UpdateResourceStatusPort updateStatus;

    public ReservationCanceller(ReservationRepositoryPort reservations, UserRepositoryPort users,
                                InventoryPort inventory, ReportCancellationPort reportCancellation,
                                UpdateResourceStatusPort updateStatus) {
        this.reservations = reservations;
        this.users = users;
        this.inventory = inventory;
        this.reportCancellation = reportCancellation;
        this.updateStatus = updateStatus;
    }

    /**
     * Cancela una reserva cuya fila ya está bloqueada. Debe llamarse dentro de la transacción que la bloqueó, y con
     * las reglas de la rama ya comprobadas.
     */
    public Reservation cancel(Reservation locked, Cancellation cancellation, String resourceName) {
        Reservation cancelled = locked.cancel(cancellation.origin());
        reservations.markCancelled(locked.id(), cancellation);
        updateStatus.record(cancelled, StatusChangeReason.RESERVATION_CANCELLED);
        User holder = locked.userId() == null ? null : users.findById(locked.userId()).orElse(null);
        reportCancellation.reportCancelled(cancelled, cancellation, resourceName, holder);
        return cancelled;
    }

    /**
     * El nombre del recurso para el reporte. Se pide fuera de la transacción y a lo que salga: cancelar no depende
     * del Módulo 1, así que si no responde el evento sale sin nombre (plan de UC4, Contratos §1).
     */
    public String resourceName(String resourceId) {
        try {
            return inventory.details(resourceId).map(Resource::name).orElse(null);
        } catch (ExternalServiceUnavailableException unavailable) {
            return null;
        }
    }
}
