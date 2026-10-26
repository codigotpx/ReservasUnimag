package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOut;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutKind;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingCheckOut;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReturns;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReviewFilter;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReviews;
import edu.unimagdalena.reservasunimag.domain.model.compliance.Verdict;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.out.CheckOutRepositoryPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Los check-out de UC12 y las dos listas de pendientes, que son consultas sobre las reservas y no tablas propias.
 * Participa en la transacción del caso de uso; no abre ninguna.
 */
@Component
class CheckOutPersistenceAdapter implements CheckOutRepositoryPort {

    private static final String PENDING_SELECT = """
            SELECT r.id, r.user_id, r.resource_id, r.resource_category, r.origin, r.status,
                   r.starts_at, r.ends_at, r.created_at, r.use_started_at,
                   p.term_business_days, p.picked_up_at, p.returned_at,
                   u.code AS holder_code, u.name AS holder_name, u.role AS holder_role
            FROM reservation r
            LEFT JOIN loan p ON p.reservation_id = r.id
            LEFT JOIN app_user u ON u.id = r.user_id
            """;

    /** Un préstamo entregado, sin devolver y vencido (FR-009). */
    private static final String PENDING_RETURN = """
            r.resource_category = 'ACTIVO' AND r.status = 'CONFIRMADA'
              AND p.picked_up_at IS NOT NULL AND p.returned_at IS NULL AND r.ends_at < :now
            """;

    /**
     * Un espacio cuya franja terminó, que se usó —ni cancelado ni con ausencia vigente— y que no tiene check-out
     * (FR-014). Un espacio sigue CONFIRMADA al terminar su franja; FINALIZADA solo entra si su ausencia se anuló.
     */
    private static final String PENDING_REVIEW = """
            r.resource_category = 'ESPACIO' AND r.status IN ('CONFIRMADA', 'FINALIZADA') AND r.ends_at <= :now
              AND NOT EXISTS (SELECT 1 FROM check_out c WHERE c.reservation_id = r.id)
              AND NOT EXISTS (SELECT 1 FROM absence a WHERE a.reservation_id = r.id AND NOT a.voided)
            """;

    private final JdbcClient jdbc;

    CheckOutPersistenceAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(CheckOut checkOut) {
        jdbc.sql("""
                        INSERT INTO check_out (reservation_id, kind, source_event_id, occurred_at, received_at, verdict,
                                               due_at_snapshot)
                        VALUES (:id, :kind, :sourceEventId, :occurredAt, :receivedAt, :verdict, :dueAt)""")
                .param("id", checkOut.reservationId())
                .param("kind", checkOut.kind().name())
                .param("sourceEventId", checkOut.sourceEventId())
                .param("occurredAt", utc(checkOut.occurredAt()))
                .param("receivedAt", utc(checkOut.receivedAt()))
                .param("verdict", checkOut.verdict() == null ? null : checkOut.verdict().name())
                .param("dueAt", checkOut.dueAtSnapshot() == null ? null : utc(checkOut.dueAtSnapshot()))
                .update();
    }

    @Override
    public Optional<CheckOut> findByReservation(UUID reservationId) {
        return jdbc.sql("""
                        SELECT reservation_id, kind, source_event_id, occurred_at, received_at, verdict, due_at_snapshot
                        FROM check_out WHERE reservation_id = :id""")
                .param("id", reservationId)
                .query((row, n) -> new CheckOut(
                        row.getObject("reservation_id", UUID.class),
                        CheckOutKind.valueOf(row.getString("kind")),
                        row.getObject("source_event_id", UUID.class),
                        instant(row, "occurred_at"),
                        instant(row, "received_at"),
                        row.getString("verdict") == null ? null : Verdict.valueOf(row.getString("verdict")),
                        instant(row, "due_at_snapshot")))
                .optional();
    }

    @Override
    public PendingReturns pendingReturns(Instant now, Duration lossThreshold, PageRequest page) {
        // Cerca del umbral: le quedan menos de 48 horas, es decir vence antes de now - umbral + 48 h.
        var counts = jdbc.sql("""
                        SELECT count(*) AS total, count(*) FILTER (WHERE r.ends_at <= :nearDue) AS near
                        FROM reservation r JOIN loan p ON p.reservation_id = r.id
                        WHERE\s""" + PENDING_RETURN)
                .param("now", utc(now))
                .param("nearDue", utc(now.minus(lossThreshold).plus(PendingReturns.NEAR_THRESHOLD)))
                .query().singleRow();
        List<PendingCheckOut> items = jdbc.sql(PENDING_SELECT + "WHERE " + PENDING_RETURN
                        + "ORDER BY r.ends_at, r.id LIMIT :limit OFFSET :offset")
                .param("now", utc(now))
                .param("limit", page.size())
                .param("offset", page.offset())
                .query(CheckOutPersistenceAdapter::toPending)
                .list();
        long total = ((Number) counts.get("total")).longValue();
        return new PendingReturns(new Page<>(items, page.page(), page.size(), total),
                ((Number) counts.get("near")).longValue(), lossThreshold, now);
    }

    @Override
    public PendingReviews pendingReviews(PendingReviewFilter filter, Instant now, PageRequest page) {
        List<String> conditions = new ArrayList<>(List.of(PENDING_REVIEW));
        Map<String, Object> params = new HashMap<>();
        params.put("now", utc(now));
        if (filter.from() != null) {
            conditions.add("r.starts_at >= :from");
            params.put("from", filter.from().atStartOfDay(TimeSlot.ZONE).toOffsetDateTime());
        }
        if (filter.to() != null) {
            conditions.add("r.starts_at < :to");
            params.put("to", filter.to().plusDays(1).atStartOfDay(TimeSlot.ZONE).toOffsetDateTime());
        }
        if (filter.origin() != null) {
            conditions.add("r.origin = :origin");
            params.put("origin", filter.origin().name());
        }
        if (filter.resourceId() != null) {
            conditions.add("r.resource_id = :resourceId");
            params.put("resourceId", filter.resourceId());
        }
        String where = "WHERE " + String.join(" AND ", conditions) + " ";

        var counts = jdbc.sql("""
                        SELECT count(*) FILTER (WHERE r.origin = 'ESTUDIANTIL') AS student,
                               count(*) FILTER (WHERE r.origin = 'ACADEMICO') AS academic
                        FROM reservation r\s""" + where)
                .params(params)
                .query().singleRow();
        long student = ((Number) counts.get("student")).longValue();
        long academic = ((Number) counts.get("academic")).longValue();

        params.put("limit", page.size());
        params.put("offset", page.offset());
        List<PendingCheckOut> items = jdbc.sql(PENDING_SELECT + where
                        + "ORDER BY r.ends_at, r.id LIMIT :limit OFFSET :offset")
                .params(params)
                .query(CheckOutPersistenceAdapter::toPending)
                .list();
        return new PendingReviews(new Page<>(items, page.page(), page.size(), student + academic), student, academic,
                now);
    }

    private static PendingCheckOut toPending(ResultSet row, int rowNumber) throws SQLException {
        ResourceCategory category = ResourceCategory.valueOf(row.getString("resource_category"));
        Loan loan = category == ResourceCategory.ACTIVO
                ? new Loan(row.getInt("term_business_days"), instant(row, "picked_up_at"), instant(row, "returned_at"))
                : null;
        UUID userId = row.getObject("user_id", UUID.class);
        Reservation reservation = new Reservation(
                row.getObject("id", UUID.class), userId, row.getString("resource_id"), category,
                ReservationOrigin.valueOf(row.getString("origin")), ReservationStatus.valueOf(row.getString("status")),
                instant(row, "starts_at"), instant(row, "ends_at"), instant(row, "created_at"), loan,
                instant(row, "use_started_at"));
        ReservationHolder holder = userId == null ? null : new ReservationHolder(userId, row.getString("holder_code"),
                row.getString("holder_name"), Role.valueOf(row.getString("holder_role")));
        return new PendingCheckOut(reservation, holder, null);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
