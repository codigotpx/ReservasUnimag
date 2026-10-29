package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.compliance.Absence;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Las ausencias de UC9. Las escrituras se llaman dentro de {@link TransactionPort#required}. */
public interface AbsenceRepositoryPort {

    /**
     * Registra el cierre de la reserva como ausencia y guarda la constancia.
     *
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.ReservationAlreadyClosedException si la reserva ya tenía un cierre, por ejemplo una cancelación
     */
    void insert(Absence absence);

    /** La ausencia de la reserva, anulada o no. */
    Optional<Absence> findByReservation(UUID reservationId);

    /** Las ausencias vigentes —no anuladas— de esas reservas, por reserva. */
    Map<UUID, Absence> findActiveByReservations(Collection<UUID> reservationIds);

    /** Marca la ausencia como anulada y suelta el cierre, para que la reserva pueda cerrarse de otra forma. */
    void markVoided(UUID reservationId, Instant voidedAt, String reason);
}
