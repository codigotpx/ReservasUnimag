package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordFilter;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordPage;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordReport;

import java.util.Optional;
import java.util.UUID;

/** El historial de UC10. Las escrituras se llaman dentro de {@link TransactionPort#required}. */
public interface ReservationRecordRepositoryPort {

    /**
     * Guarda la constancia de la ficha.
     *
     * @throws IllegalStateException si la reserva ya tenía ficha: una reserva produce una sola en toda su vida (FR-005)
     */
    void insert(ReservationRecordReport report);

    /** La constancia con su entrega; sin {@code resourceName}, que no se guarda. */
    Optional<ReservationRecordReport> findByReservation(UUID reservationId);

    ReservationRecordPage search(ReservationRecordFilter filter, PageRequest page);
}
