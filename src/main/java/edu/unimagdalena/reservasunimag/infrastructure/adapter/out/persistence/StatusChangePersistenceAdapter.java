package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.DisplayedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.NotifiedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChange;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeFilter;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason;
import edu.unimagdalena.reservasunimag.domain.port.out.StatusChangeRepositoryPort;
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

/** El historial de UC7 (FR-009). Participa en la transacción de quien cambió la ocupación; no abre ninguna. */
@Component
class StatusChangePersistenceAdapter implements StatusChangeRepositoryPort {

    private static final String SELECT = """
            SELECT id, resource_id, resource_category, reservation_id, occupancy_starts_at, occupancy_ends_at,
                   previous_status_module2, new_status, reason, changed_at, notified_status, outbox_message_id
            FROM status_change
            """;

    private final JdbcClient jdbc;

    StatusChangePersistenceAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(StatusChange change) {
        jdbc.sql("""
                        INSERT INTO status_change (id, resource_id, resource_category, reservation_id,
                            occupancy_starts_at, occupancy_ends_at, previous_status_module2, new_status, reason,
                            changed_at, notified_status, outbox_message_id)
                        VALUES (:id, :resourceId, :category, :reservationId, :start, :end, :previous, :new, :reason,
                            :changedAt, :notified, :outbox)""")
                .param("id", change.id())
                .param("resourceId", change.resourceId())
                .param("category", change.category().name())
                .param("reservationId", change.reservationId())
                .param("start", utc(change.occupancyStart()))
                .param("end", utc(change.occupancyEnd()))
                .param("previous", change.previousStatusModule2().name())
                .param("new", change.newStatus().name())
                .param("reason", change.reason().name())
                .param("changedAt", utc(change.changedAt()))
                .param("notified", change.notifiedStatus().name())
                .param("outbox", change.outboxMessageId())
                .update();
    }

    @Override
    public Optional<StatusChange> findUseStarted(UUID reservationId) {
        return jdbc.sql(SELECT + "WHERE reservation_id = :id AND reason = 'USE_STARTED'")
                .param("id", reservationId)
                .query(StatusChangePersistenceAdapter::toChange)
                .optional();
    }

    @Override
    public Page<StatusChange> findByResource(String resourceId, StatusChangeFilter filter, PageRequest page) {
        List<String> conditions = new ArrayList<>(List.of("resource_id = :resourceId"));
        Map<String, Object> params = new HashMap<>();
        params.put("resourceId", resourceId);
        if (filter.from() != null) {
            conditions.add("changed_at >= :from");
            params.put("from", filter.from().atStartOfDay(TimeSlot.ZONE).toOffsetDateTime());
        }
        if (filter.to() != null) {
            conditions.add("changed_at < :to");
            params.put("to", filter.to().plusDays(1).atStartOfDay(TimeSlot.ZONE).toOffsetDateTime());
        }
        if (filter.reason() != null) {
            conditions.add("reason = :reason");
            params.put("reason", filter.reason().name());
        }
        String where = "WHERE " + String.join(" AND ", conditions) + " ";
        long total = jdbc.sql("SELECT count(*) FROM status_change " + where).params(params).query(Long.class).single();
        params.put("limit", page.size());
        params.put("offset", page.offset());
        List<StatusChange> items = jdbc.sql(SELECT + where + "ORDER BY changed_at, id LIMIT :limit OFFSET :offset")
                .params(params)
                .query(StatusChangePersistenceAdapter::toChange)
                .list();
        return new Page<>(items, page.page(), page.size(), total);
    }

    private static StatusChange toChange(ResultSet row, int rowNumber) throws SQLException {
        return new StatusChange(
                row.getObject("id", UUID.class),
                row.getString("resource_id"),
                ResourceCategory.valueOf(row.getString("resource_category")),
                row.getObject("reservation_id", UUID.class),
                instant(row, "occupancy_starts_at"),
                instant(row, "occupancy_ends_at"),
                DisplayedStatus.valueOf(row.getString("previous_status_module2")),
                DisplayedStatus.valueOf(row.getString("new_status")),
                StatusChangeReason.valueOf(row.getString("reason")),
                instant(row, "changed_at"),
                NotifiedStatus.valueOf(row.getString("notified_status")),
                row.getObject("outbox_message_id", UUID.class));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
