package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.error.ResourceTakenException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Cancellation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.QuotaUsage;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    Optional<Reservation> findById(UUID id);

    /**
     * Lee la reserva bloqueando su fila: dos cancelaciones simultáneas se ponen en fila y la segunda la ve cancelada.
     */
    Optional<Reservation> lockById(UUID id);

    /**
     * Cómo se canceló una reserva ya cancelada: su origen y su instante, sin motivo ni antelación. Vacío si no está
     * cancelada.
     */
    Optional<Cancellation> findCancellation(UUID id);

    /**
     * Deja la reserva en el estado que corresponde a la cancelación. No borra nada: la restricción de exclusión solo
     * mira las confirmadas, así que cambiar el estado basta para liberar la ocupación (UC4 FR-002).
     */
    void markCancelled(UUID id, Cancellation cancellation);

    /**
     * Las reservas estudiantiles confirmadas del recurso que todavía tienen algo por delante: las que no han empezado
     * y los préstamos en manos de alguien, ordenadas por inicio (UC4 FR-010).
     */
    List<Reservation> findUpcomingByResource(String resourceId, Instant now);

    /**
     * Deja la reserva {@code FINALIZADA}. Con eso deja de ocupar el recurso y el cupo, igual que una cancelada, sin
     * decir que se canceló (plan de UC9, Decisiones de diseño).
     */
    void markFinished(UUID id);

    /**
     * Cierra el préstamo de un activo: guarda la devolución real y deja la reserva {@code FINALIZADA}, con lo que el
     * periodo y el cupo quedan libres (UC12 FR-002, FR-003).
     */
    void markReturned(UUID id, Instant returnedAt);

    /**
     * Marca el inicio de uso, y en un activo también la recogida, solo si no estaba marcado. Devuelve si esta llamada
     * fue la que lo marcó: de eso sale que dos peticiones a la vez no produzcan dos avisos (UC7 FR-008).
     */
    boolean markUseStarted(UUID id, Instant at);

    /** Los bloqueos académicos confirmados que ya empezaron, no han terminado y no tienen el uso marcado. */
    List<Reservation> findAcademicBlocksToStart(Instant now);

    /**
     * Las reservas estudiantiles confirmadas de esos recursos que ocupan algo de {@code [from, to)}: las que se cruzan,
     * y los préstamos en manos de alguien que empezaron antes de {@code to}, que ocupan sin fin (UC3, UC7 FR-011).
     */
    List<Reservation> findStudentOccupying(Collection<String> resourceIds, Instant from, Instant to);
}
