package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.resource.ScheduledUse;
import edu.unimagdalena.reservasunimag.domain.port.out.ScheduledUseRepositoryPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * El calendario de ocupación: las reservas confirmadas de un recurso que se cruzan con el rango, con la asignatura y el
 * programa de las académicas. No lee al titular de las estudiantiles: el calendario no lo muestra (UC8 FR-005).
 */
@Component
class ScheduledUsePersistenceAdapter implements ScheduledUseRepositoryPort {

    private static final String USES = """
            SELECT r.origin, r.starts_at, r.ends_at, b.type AS block_type, b.course, b.program
            FROM reservation r
            LEFT JOIN academic_block b ON b.reservation_id = r.id
            WHERE r.status = 'CONFIRMADA'
              AND r.resource_id = :resourceId
              AND r.occupancy && tstzrange(CAST(:from AS timestamptz), CAST(:to AS timestamptz), '[)')
            ORDER BY r.starts_at
            """;

    private final JdbcClient jdbc;

    ScheduledUsePersistenceAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ScheduledUse> findByResource(String resourceId, Instant from, Instant to) {
        return jdbc.sql(USES)
                .param("resourceId", resourceId)
                .param("from", from.atOffset(ZoneOffset.UTC))
                .param("to", to.atOffset(ZoneOffset.UTC))
                .query(ScheduledUsePersistenceAdapter::toUse)
                .list();
    }

    private static ScheduledUse toUse(ResultSet row, int rowNumber) throws SQLException {
        Instant startsAt = row.getObject("starts_at", OffsetDateTime.class).toInstant();
        Instant endsAt = row.getObject("ends_at", OffsetDateTime.class).toInstant();
        if (!"ACADEMICO".equals(row.getString("origin"))) {
            return new ScheduledUse(ScheduledUse.Kind.STUDENT_RESERVATION, startsAt, endsAt, null, null);
        }
        ScheduledUse.Kind kind = "EXTRAORDINARIO".equals(row.getString("block_type"))
                ? ScheduledUse.Kind.EXTRAORDINARY : ScheduledUse.Kind.CLASS;
        return new ScheduledUse(kind, startsAt, endsAt, row.getString("course"), row.getString("program"));
    }
}
