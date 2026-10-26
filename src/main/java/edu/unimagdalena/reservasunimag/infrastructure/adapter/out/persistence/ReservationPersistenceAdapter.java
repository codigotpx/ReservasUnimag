package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.error.ResourceTakenException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Cancellation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.QuotaUsage;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRepositoryPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Escritura y lectura de reservas con SQL directo. Las escrituras participan en la transacción que abre el caso de
 * uso a través de {@code TransactionPort}; este adaptador no abre ninguna.
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

    /**
     * Vigente es toda reserva confirmada de un espacio cuya franja no terminó, y todo préstamo mientras no tenga
     * devolución, aunque ya esté vencido (UC2 FR-008, FR-015).
     */
    private static final String ACTIVE = """
            r.user_id = :userId
              AND r.status = 'CONFIRMADA'
              AND ( (r.resource_category = 'ESPACIO' AND r.ends_at > :now)
                 OR (r.resource_category = 'ACTIVO' AND p.returned_at IS NULL) )
            """;

    private static final String SELECT = """
            SELECT r.id, r.user_id, r.resource_id, r.resource_category, r.origin, r.status,
                   r.starts_at, r.ends_at, r.created_at, r.use_started_at,
                   p.term_business_days, p.picked_up_at, p.returned_at
            FROM reservation r
            LEFT JOIN loan p ON p.reservation_id = r.id
            """;

    private final JdbcClient jdbc;

    ReservationPersistenceAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lockHolder(UUID userId) {
        jdbc.sql("SELECT id FROM app_user WHERE id = :id FOR UPDATE").param("id", userId).query().listOfRows();
    }

    @Override
    public QuotaUsage quotaOf(UUID userId, Instant now) {
        List<Instant> ends = jdbc.sql("""
                        SELECT r.ends_at
                        FROM reservation r
                        LEFT JOIN loan p ON p.reservation_id = r.id
                        WHERE\s""" + ACTIVE + " ORDER BY r.ends_at")
                .param("userId", userId)
                .param("now", utc(now))
                .query((row, n) -> row.getObject("ends_at", OffsetDateTime.class).toInstant())
                .list();
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
            jdbc.sql("""
                            INSERT INTO reservation (id, user_id, resource_id, resource_category, origin, status,
                                                     starts_at, ends_at, created_at)
                            VALUES (:id, :userId, :resourceId, :category, :origin, :status, :startsAt, :endsAt,
                                    :createdAt)""")
                    .param("id", reservation.id())
                    .param("userId", reservation.userId())
                    .param("resourceId", reservation.resourceId())
                    .param("category", reservation.category().name())
                    .param("origin", reservation.origin().name())
                    .param("status", reservation.status().name())
                    .param("startsAt", utc(reservation.startsAt()))
                    .param("endsAt", utc(reservation.endsAt()))
                    .param("createdAt", utc(reservation.createdAt()))
                    .update();
        } catch (DataAccessException failure) {
            if (hasSqlState(failure, EXCLUSION_VIOLATION) || hasSqlState(failure, DEADLOCK)) {
                throw new ResourceTakenException(reservation.resourceId(), failure);
            }
            throw failure;
        }
        if (reservation.loan() != null) {
            jdbc.sql("INSERT INTO loan (reservation_id, term_business_days) VALUES (:id, :term)")
                    .param("id", reservation.id())
                    .param("term", reservation.loan().termBusinessDays())
                    .update();
        }
    }

    @Override
    public List<Reservation> findByHolder(UUID userId, boolean onlyActive, Instant now) {
        String where = onlyActive ? ACTIVE : "r.user_id = :userId";
        var statement = jdbc.sql(SELECT + "WHERE " + where + " ORDER BY r.starts_at, r.id")
                .param("userId", userId);
        if (onlyActive) {
            statement = statement.param("now", utc(now));
        }
        return statement.query(ReservationPersistenceAdapter::toReservation).list();
    }

    @Override
    public Optional<Reservation> findById(UUID id) {
        return jdbc.sql(SELECT + "WHERE r.id = :id").param("id", id)
                .query(ReservationPersistenceAdapter::toReservation).optional();
    }

    @Override
    public Optional<Reservation> lockById(UUID id) {
        // FOR UPDATE OF r: el préstamo viene por LEFT JOIN, y PostgreSQL no bloquea el lado que puede ser nulo.
        return jdbc.sql(SELECT + "WHERE r.id = :id FOR UPDATE OF r").param("id", id)
                .query(ReservationPersistenceAdapter::toReservation).optional();
    }

    @Override
    public Optional<Cancellation> findCancellation(UUID id) {
        return jdbc.sql("SELECT status, cancellation_reason, cancelled_at FROM reservation WHERE id = :id")
                .param("id", id)
                .query((row, n) -> {
                    ReservationStatus status = ReservationStatus.valueOf(row.getString("status"));
                    Instant cancelledAt = instant(row, "cancelled_at");
                    if (!status.isCancellation() || cancelledAt == null) {
                        return Optional.<Cancellation>empty();
                    }
                    // cancellation_reason guarda el origen; una fila antigua sin él se deduce del estado.
                    String origin = row.getString("cancellation_reason");
                    return Optional.of(new Cancellation(origin == null ? CancellationOrigin.of(status)
                            : CancellationOrigin.valueOf(origin), null, null, cancelledAt));
                })
                .optional()
                .flatMap(cancellation -> cancellation);
    }

    @Override
    public void markCancelled(UUID id, Cancellation cancellation) {
        int updated = jdbc.sql("""
                        UPDATE reservation
                        SET status = :status, cancellation_reason = :origin, cancelled_at = :at, version = version + 1
                        WHERE id = :id AND status = 'CONFIRMADA'""")
                .param("status", cancellation.status().name())
                .param("origin", cancellation.origin().name())
                .param("at", utc(cancellation.cancelledAt()))
                .param("id", id)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("la reserva " + id + " no estaba confirmada al cancelarla");
        }
    }

    @Override
    public void markFinished(UUID id) {
        int updated = jdbc.sql("""
                        UPDATE reservation SET status = 'FINALIZADA', version = version + 1
                        WHERE id = :id AND status = 'CONFIRMADA'""")
                .param("id", id)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("la reserva " + id + " no estaba confirmada al finalizarla");
        }
    }

    @Override
    public void markReturned(UUID id, Instant returnedAt) {
        int loans = jdbc.sql("UPDATE loan SET returned_at = :at WHERE reservation_id = :id AND returned_at IS NULL")
                .param("at", utc(returnedAt))
                .param("id", id)
                .update();
        if (loans != 1) {
            throw new IllegalStateException("la reserva " + id + " no tiene un préstamo abierto");
        }
        markFinished(id);
    }

    @Override
    public boolean markUseStarted(UUID id, Instant at) {
        int marked = jdbc.sql("""
                        UPDATE reservation SET use_started_at = :at, version = version + 1
                        WHERE id = :id AND use_started_at IS NULL""")
                .param("at", utc(at))
                .param("id", id)
                .update();
        if (marked == 1) {
            // En un activo, recoger es empezar a usar: la recogida se marca con el mismo instante si faltaba.
            jdbc.sql("UPDATE loan SET picked_up_at = :at WHERE reservation_id = :id AND picked_up_at IS NULL")
                    .param("at", utc(at))
                    .param("id", id)
                    .update();
        }
        return marked == 1;
    }

    @Override
    public List<Reservation> findAcademicBlocksToStart(Instant now) {
        return jdbc.sql(SELECT + """
                        WHERE r.origin = 'ACADEMICO' AND r.status = 'CONFIRMADA' AND r.use_started_at IS NULL
                          AND r.starts_at <= :now AND r.ends_at > :now
                        ORDER BY r.starts_at, r.id""")
                .param("now", utc(now))
                .query(ReservationPersistenceAdapter::toReservation)
                .list();
    }

    @Override
    public List<Reservation> findUpcomingByResource(String resourceId, Instant now) {
        return jdbc.sql(SELECT + """
                        WHERE r.resource_id = :resourceId
                          AND r.origin = 'ESTUDIANTIL'
                          AND r.status = 'CONFIRMADA'
                          AND ( r.starts_at > :now
                             OR (p.picked_up_at IS NOT NULL AND p.returned_at IS NULL) )
                        ORDER BY r.starts_at, r.id""")
                .param("resourceId", resourceId)
                .param("now", utc(now))
                .query(ReservationPersistenceAdapter::toReservation)
                .list();
    }

    private static Reservation toReservation(ResultSet row, int rowNumber) throws SQLException {
        ResourceCategory category = ResourceCategory.valueOf(row.getString("resource_category"));
        Loan loan = category == ResourceCategory.ACTIVO
                ? new Loan(row.getInt("term_business_days"), instant(row, "picked_up_at"), instant(row, "returned_at"))
                : null;
        return new Reservation(
                row.getObject("id", UUID.class),
                row.getObject("user_id", UUID.class),
                row.getString("resource_id"),
                category,
                ReservationOrigin.valueOf(row.getString("origin")),
                ReservationStatus.valueOf(row.getString("status")),
                instant(row, "starts_at"),
                instant(row, "ends_at"),
                instant(row, "created_at"),
                loan,
                instant(row, "use_started_at"));
    }

    private static boolean hasSqlState(Throwable failure, String sqlState) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sqlState.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
