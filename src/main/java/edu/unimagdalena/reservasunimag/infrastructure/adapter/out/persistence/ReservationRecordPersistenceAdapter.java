package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Delivery;
import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordFilter;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordPage;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRecordRepositoryPort;
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
 * El historial de fichas reportadas (UC10 FR-006). La clase de una ficha académica se lee de {@code academic_block}
 * en vez de copiarse. Participa en la transacción de la reserva; no abre ninguna.
 */
@Component
class ReservationRecordPersistenceAdapter implements ReservationRecordRepositoryPort {

    private static final String SELECT = """
            SELECT r.reservation_id, r.event_id, r.origin, r.holder_user_id, r.resource_id, r.resource_category,
                   r.occupancy_starts_at, r.occupancy_ends_at, r.loan_term_business_days, r.sent_at,
                   r.delivery_status, r.attempts, r.first_attempt_at, r.delivered_at, r.last_error,
                   u.code AS holder_code, u.name AS holder_name, u.role AS holder_role,
                   a.course_code, a.course, a.group_code, a.program, a.instructor,
                   o.next_attempt_at
            FROM reservation_record r
            LEFT JOIN app_user u ON u.id = r.holder_user_id
            LEFT JOIN academic_block a ON a.reservation_id = r.reservation_id
            LEFT JOIN outbox_message o ON o.id = r.event_id AND o.status = 'PENDING'
            """;

    private final JdbcClient jdbc;

    ReservationRecordPersistenceAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(ReservationRecordReport report) {
        try {
            jdbc.sql("""
                            INSERT INTO reservation_record (reservation_id, event_id, origin, sanctionable,
                                holder_user_id, resource_id, resource_category, occupancy_starts_at, occupancy_ends_at,
                                loan_term_business_days, sent_at)
                            VALUES (:reservationId, :eventId, :origin, :sanctionable, :holder, :resourceId, :category,
                                :start, :end, :term, :sentAt)""")
                    .param("reservationId", report.reservationId())
                    .param("eventId", report.eventId())
                    .param("origin", report.origin().name())
                    .param("sanctionable", report.sanctionable())
                    .param("holder", report.holder() == null ? null : report.holder().userId())
                    .param("resourceId", report.resourceId())
                    .param("category", report.category().name())
                    .param("start", utc(report.startsAt()))
                    .param("end", utc(report.endsAt()))
                    .param("term", report.loanTermBusinessDays())
                    .param("sentAt", utc(report.sentAt()))
                    .update();
        } catch (DuplicateKeyException repeated) {
            throw new IllegalStateException("la reserva " + report.reservationId() + " ya tiene ficha", repeated);
        }
    }

    @Override
    public Optional<ReservationRecordReport> findByReservation(UUID reservationId) {
        return jdbc.sql(SELECT + "WHERE r.reservation_id = :id")
                .param("id", reservationId)
                .query(ReservationRecordPersistenceAdapter::toReport)
                .optional();
    }

    @Override
    public ReservationRecordPage search(ReservationRecordFilter filter, PageRequest page) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (filter.origin() != null) {
            conditions.add("r.origin = :origin");
            params.put("origin", filter.origin().name());
        }
        if (filter.delivery() != null) {
            conditions.add("r.delivery_status = :delivery");
            params.put("delivery", filter.delivery().name());
        }
        // Días de Bogotá, ambos incluidos: desde el inicio de from hasta antes del inicio del día siguiente a to.
        if (filter.from() != null) {
            conditions.add("r.sent_at >= :from");
            params.put("from", filter.from().atStartOfDay(TimeSlot.ZONE).toOffsetDateTime());
        }
        if (filter.to() != null) {
            conditions.add("r.sent_at < :to");
            params.put("to", filter.to().plusDays(1).atStartOfDay(TimeSlot.ZONE).toOffsetDateTime());
        }
        String where = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions) + " ";

        var summary = jdbc.sql("""
                        SELECT count(*) AS total,
                               count(*) FILTER (WHERE delivery_status = 'SENT') AS sent,
                               count(*) FILTER (WHERE delivery_status = 'PENDING') AS pending,
                               count(*) FILTER (WHERE delivery_status = 'FAILED') AS failed,
                               count(*) FILTER (WHERE sanctionable) AS sanctionable
                        FROM reservation_record r\s""" + where)
                .params(params)
                .query((row, n) -> new ReservationRecordPage.Summary(row.getLong("total"), row.getLong("sent"),
                        row.getLong("pending"), row.getLong("failed"), row.getLong("sanctionable")))
                .single();

        params.put("limit", page.size());
        params.put("offset", page.offset());
        List<ReservationRecordReport> items = jdbc.sql(SELECT + where
                        + "ORDER BY r.sent_at DESC, r.reservation_id LIMIT :limit OFFSET :offset")
                .params(params)
                .query(ReservationRecordPersistenceAdapter::toReport)
                .list();
        return new ReservationRecordPage(new Page<>(items, page.page(), page.size(), summary.total()), summary);
    }

    private static ReservationRecordReport toReport(ResultSet row, int rowNumber) throws SQLException {
        UUID holderId = row.getObject("holder_user_id", UUID.class);
        ReservationHolder holder = holderId == null ? null : new ReservationHolder(holderId,
                row.getString("holder_code"), row.getString("holder_name"),
                Role.valueOf(row.getString("holder_role")));
        String courseCode = row.getString("course_code");
        AcademicData academic = courseCode == null ? null : new AcademicData(courseCode, row.getString("course"),
                row.getString("group_code"), row.getString("program"), row.getString("instructor"));
        int term = row.getInt("loan_term_business_days");
        Integer termBusinessDays = row.wasNull() ? null : term;
        DeliveryStatus status = DeliveryStatus.valueOf(row.getString("delivery_status"));
        return new ReservationRecordReport(
                row.getObject("reservation_id", UUID.class),
                row.getObject("event_id", UUID.class),
                ReservationOrigin.valueOf(row.getString("origin")),
                row.getString("resource_id"),
                null,
                ResourceCategory.valueOf(row.getString("resource_category")),
                holder,
                academic,
                instant(row, "occupancy_starts_at"),
                instant(row, "occupancy_ends_at"),
                termBusinessDays,
                instant(row, "sent_at"),
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
