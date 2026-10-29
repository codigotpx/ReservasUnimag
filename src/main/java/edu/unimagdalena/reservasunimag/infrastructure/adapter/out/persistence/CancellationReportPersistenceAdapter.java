package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationAlreadyClosedException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReportFilter;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReportPage;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Delivery;
import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.out.CancellationReportRepositoryPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
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
 * El historial de cancelaciones reportadas (UC11 FR-010) y el cierre de la reserva que impide que conviva con una
 * ausencia (FR-011). Participa en la transacción de la cancelación; no abre ninguna.
 */
@Component
class CancellationReportPersistenceAdapter implements CancellationReportRepositoryPort {

    private static final String SELECT = """
            SELECT c.reservation_id, c.event_id, c.cancellation_origin, c.reason, c.notice_minutes, c.cancelled_at,
                   c.resource_id, c.resource_category, c.holder_user_id, c.released_starts_at, c.released_ends_at,
                   c.delivery_status, c.attempts, c.first_attempt_at, c.delivered_at, c.last_error,
                   u.code AS holder_code, u.name AS holder_name, u.role AS holder_role,
                   o.next_attempt_at
            FROM cancellation_report c
            LEFT JOIN app_user u ON u.id = c.holder_user_id
            LEFT JOIN outbox_message o ON o.id = c.event_id AND o.status = 'PENDING'
            """;

    private final JdbcClient jdbc;

    CancellationReportPersistenceAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(CancellationReport report) {
        try {
            jdbc.sql("INSERT INTO reservation_closure (reservation_id, closure_type) VALUES (:id, 'CANCELLATION')")
                    .param("id", report.reservationId())
                    .update();
        } catch (DuplicateKeyException closed) {
            throw new ReservationAlreadyClosedException(report.reservationId(), closed);
        }
        jdbc.sql("""
                        INSERT INTO cancellation_report (reservation_id, event_id, cancellation_origin,
                            reservation_status, reason, attributable_to_person, notice_minutes, cancelled_at,
                            resource_id, resource_category, holder_user_id, released_starts_at, released_ends_at)
                        VALUES (:reservationId, :eventId, :origin, :status, :reason, :attributable, :notice,
                            :cancelledAt, :resourceId, :category, :holder, :start, :end)""")
                .param("reservationId", report.reservationId())
                .param("eventId", report.eventId())
                .param("origin", report.origin().name())
                .param("status", report.status().name())
                .param("reason", report.reason())
                .param("attributable", report.attributableToPerson())
                .param("notice", report.noticeMinutes())
                .param("cancelledAt", utc(report.cancelledAt()))
                .param("resourceId", report.resourceId())
                .param("category", report.category().name())
                .param("holder", report.holder() == null ? null : report.holder().userId())
                .param("start", utc(report.releasedStart()))
                .param("end", utc(report.releasedEnd()))
                .update();
    }

    @Override
    public Optional<CancellationReport> findByReservation(UUID reservationId) {
        return jdbc.sql(SELECT + "WHERE c.reservation_id = :id")
                .param("id", reservationId)
                .query(CancellationReportPersistenceAdapter::toReport)
                .optional();
    }

    @Override
    public CancellationReportPage search(CancellationReportFilter filter, PageRequest page) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (filter.origin() != null) {
            conditions.add("c.cancellation_origin = :origin");
            params.put("origin", filter.origin().name());
        }
        if (filter.delivery() != null) {
            conditions.add("c.delivery_status = :delivery");
            params.put("delivery", filter.delivery().name());
        }
        // Días de Bogotá, ambos incluidos: desde el inicio de from hasta antes del inicio del día siguiente a to.
        if (filter.from() != null) {
            conditions.add("c.cancelled_at >= :from");
            params.put("from", filter.from().atStartOfDay(TimeSlot.ZONE).toOffsetDateTime());
        }
        if (filter.to() != null) {
            conditions.add("c.cancelled_at < :to");
            params.put("to", filter.to().plusDays(1).atStartOfDay(TimeSlot.ZONE).toOffsetDateTime());
        }
        String where = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions) + " ";

        var summary = jdbc.sql("""
                        SELECT count(*) AS total,
                               count(*) FILTER (WHERE delivery_status = 'SENT') AS sent,
                               count(*) FILTER (WHERE delivery_status = 'PENDING') AS pending,
                               count(*) FILTER (WHERE delivery_status = 'FAILED') AS failed
                        FROM cancellation_report c\s""" + where)
                .params(params)
                .query((row, n) -> new CancellationReportPage.Summary(row.getLong("total"), row.getLong("sent"),
                        row.getLong("pending"), row.getLong("failed")))
                .single();

        params.put("limit", page.size());
        params.put("offset", page.offset());
        List<CancellationReport> items = jdbc.sql(SELECT + where
                        + "ORDER BY c.cancelled_at DESC, c.reservation_id LIMIT :limit OFFSET :offset")
                .params(params)
                .query(CancellationReportPersistenceAdapter::toReport)
                .list();
        return new CancellationReportPage(new Page<>(items, page.page(), page.size(), summary.total()), summary);
    }

    private static CancellationReport toReport(ResultSet row, int rowNumber) throws SQLException {
        UUID holderId = row.getObject("holder_user_id", UUID.class);
        ReservationHolder holder = holderId == null ? null : new ReservationHolder(holderId,
                row.getString("holder_code"), row.getString("holder_name"),
                Role.valueOf(row.getString("holder_role")));
        DeliveryStatus status = DeliveryStatus.valueOf(row.getString("delivery_status"));
        int notice = row.getInt("notice_minutes");
        Integer noticeMinutes = row.wasNull() ? null : notice;
        return new CancellationReport(
                row.getObject("reservation_id", UUID.class),
                row.getObject("event_id", UUID.class),
                CancellationOrigin.valueOf(row.getString("cancellation_origin")),
                row.getString("reason"),
                noticeMinutes,
                instant(row, "cancelled_at"),
                row.getString("resource_id"),
                null,
                ResourceCategory.valueOf(row.getString("resource_category")),
                holder,
                instant(row, "released_starts_at"),
                instant(row, "released_ends_at"),
                new Delivery(status, row.getInt("attempts"), instant(row, "first_attempt_at"),
                        instant(row, "delivered_at"),
                        status == DeliveryStatus.PENDING ? instant(row, "next_attempt_at") : null,
                        row.getString("last_error")));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
