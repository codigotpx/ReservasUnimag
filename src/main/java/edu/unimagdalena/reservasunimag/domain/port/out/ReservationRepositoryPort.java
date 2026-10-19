package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.error.ResourceTakenException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.QuotaUsage;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Reservas del Módulo 2. Los métodos de escritura se llaman dentro de {@link TransactionPort#required}. */
public interface ReservationRepositoryPort {

    /** Serializa las reservas de una misma persona: dos peticiones suyas no se cuelan las dos en el cupo. */
    void lockHolder(UUID userId);

    /** Cuenta las reservas vigentes de la persona (UC2 FR-008). */
    QuotaUsage quotaOf(UUID userId, Instant now);

    /**
     * Guarda la reserva y, si es un activo, su préstamo.
     *
     * @throws ResourceTakenException si la ocupación se cruza con otra confirmada del mismo recurso
     */
    void insert(Reservation reservation);

    /** Las reservas de la persona, de la más próxima a la más lejana; solo las vigentes si {@code onlyActive}. */
    List<Reservation> findByHolder(UUID userId, boolean onlyActive, Instant now);
}
