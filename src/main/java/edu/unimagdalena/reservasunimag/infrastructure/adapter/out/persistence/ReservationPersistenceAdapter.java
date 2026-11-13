package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.error.ResourceTakenException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Cancellation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.QuotaUsage;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRepositoryPort;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.LoanJpaEntity;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.mapper.ReservationMapper;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.LoanJpaRepository;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.ReservationJpaRepository;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.UserJpaRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Escritura y lectura de reservas con JPA. Las escrituras participan en la transacción que abre el caso de uso a
 * través de {@code TransactionPort}; este adaptador no abre ninguna.
 *
 * <p>Otros adaptadores todavía leen y escriben {@code reservation} con SQL en la misma transacción. Por eso la
 * inserción se envía a la base en el acto ({@code saveAndFlush}), las lecturas son proyecciones que siempre van a la
 * base, y los cambios de estado son {@code UPDATE} de JPQL que tampoco pasan por el contexto de persistencia.
 */
@Component
class ReservationPersistenceAdapter implements ReservationRepositoryPort {

    /** Código de PostgreSQL para una violación de restricción de exclusión. */
    private static final String EXCLUSION_VIOLATION = "23P01";
    /**
     * Código de un <em>deadlock</em>. Dos inserciones simultáneas que se cruzan pueden esperarse la una a la otra al
     * comprobar la restricción de exclusión; PostgreSQL aborta a una. Para la persona es lo mismo que perder la
     * carrera: el recurso acaba de ser tomado.
     */
    private static final String DEADLOCK = "40P01";

    private final ReservationJpaRepository reservations;
    private final LoanJpaRepository loans;
    private final UserJpaRepository users;

    ReservationPersistenceAdapter(ReservationJpaRepository reservations, LoanJpaRepository loans,
                                  UserJpaRepository users) {
        this.reservations = reservations;
        this.loans = loans;
        this.users = users;
    }

    @Override
    public void lockHolder(UUID userId) {
        users.lockById(userId);
    }

    @Override
    public QuotaUsage quotaOf(UUID userId, Instant now) {
        List<Instant> ends = reservations.activeEndsOf(userId, now);
        if (ends.isEmpty()) {
            return new QuotaUsage(0, null, false);
        }
        Instant nearest = ends.get(0);
        // Si la más próxima ya pasó es un préstamo vencido y sin devolver: no hay fecha que prometer.
        return nearest.isAfter(now)
                ? new QuotaUsage(ends.size(), nearest, false)
                : new QuotaUsage(ends.size(), null, true);
    }

    @Override
    public void insert(Reservation reservation) {
        try {
            // La restricción de exclusión se comprueba al escribir: el flush hace que falle aquí y no al confirmar.
            reservations.saveAndFlush(ReservationMapper.toEntity(reservation));
        } catch (DataAccessException failure) {
            if (hasSqlState(failure, EXCLUSION_VIOLATION) || hasSqlState(failure, DEADLOCK)) {
                throw new ResourceTakenException(reservation.resourceId(), failure);
            }
            throw failure;
        }
        LoanJpaEntity loan = ReservationMapper.toLoanEntity(reservation);
        if (loan != null) {
            loans.saveAndFlush(loan);
        }
    }

    @Override
    public List<Reservation> findByHolder(UUID userId, boolean onlyActive, Instant now) {
        var rows = onlyActive ? reservations.findActiveByHolder(userId, now) : reservations.findAllByHolder(userId);
        return rows.stream().map(ReservationMapper::toDomain).toList();
    }

    @Override
    public Optional<Reservation> findById(UUID id) {
        return reservations.findRowById(id).map(ReservationMapper::toDomain);
    }

    @Override
    public Optional<Reservation> lockById(UUID id) {
        return reservations.lockById(id).flatMap(this::findById);
    }

    @Override
    public Optional<Cancellation> findCancellation(UUID id) {
        return reservations.findCancellationRow(id)
                .filter(row -> row.status().isCancellation() && row.cancelledAt() != null)
                // Una fila antigua sin origen lo deduce del estado.
                .map(row -> new Cancellation(row.origin() == null ? CancellationOrigin.of(row.status())
                        : row.origin(), null, null, row.cancelledAt()));
    }

    @Override
    public void markCancelled(UUID id, Cancellation cancellation) {
        int updated = reservations.markCancelled(id, cancellation.status(), cancellation.origin(),
                cancellation.cancelledAt());
        if (updated != 1) {
            throw new IllegalStateException("la reserva " + id + " no estaba confirmada al cancelarla");
        }
        // Un bloqueo cancelado suelta su huella, para que la misma clase se pueda volver a cargar (UC3 FR-007).
        reservations.releaseAcademicBlockKey(id);
    }

    @Override
    public void markFinished(UUID id) {
        if (reservations.markFinished(id) != 1) {
            throw new IllegalStateException("la reserva " + id + " no estaba confirmada al finalizarla");
        }
    }

    @Override
    public void markReturned(UUID id, Instant returnedAt) {
        if (loans.markReturned(id, returnedAt) != 1) {
            throw new IllegalStateException("la reserva " + id + " no tiene un préstamo abierto");
        }
        markFinished(id);
    }

    @Override
    public boolean markUseStarted(UUID id, Instant at) {
        boolean marked = reservations.markUseStarted(id, at) == 1;
        if (marked) {
            // En un activo, recoger es empezar a usar: la recogida se marca con el mismo instante si faltaba.
            loans.markPickedUp(id, at);
        }
        return marked;
    }

    @Override
    public List<Reservation> findAcademicBlocksToStart(Instant now) {
        return reservations.findAcademicBlocksToStart(now).stream().map(ReservationMapper::toDomain).toList();
    }

    @Override
    public List<Reservation> findStudentOccupying(Collection<String> resourceIds, Instant from, Instant to) {
        if (resourceIds.isEmpty()) {
            return List.of();
        }
        return reservations.findStudentOccupying(resourceIds, from, to).stream()
                .map(ReservationMapper::toDomain).toList();
    }

    @Override
    public List<Reservation> findUpcomingByResource(String resourceId, Instant now) {
        return reservations.findUpcomingByResource(resourceId, now).stream().map(ReservationMapper::toDomain).toList();
    }

    private static boolean hasSqlState(Throwable failure, String sqlState) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sqlState.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
