package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Occupancy;
import edu.unimagdalena.reservasunimag.domain.model.reservation.OccupancyType;
import edu.unimagdalena.reservasunimag.domain.port.out.OccupancyRepositoryPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

/**
 * Lectura pura de ocupaciones: una consulta nativa con rangos {@code tstzrange}, sin entidades porque UC1 no
 * modifica nada. Las reservas canceladas no ocupan; un préstamo entregado y sin devolver sí, aunque haya vencido.
 */
@Component
class OccupancyPersistenceAdapter implements OccupancyRepositoryPort {

    private static final String OCCUPANCIES = """
            SELECT r.resource_id, r.origin, r.resource_category, r.starts_at, r.ends_at,
                   p.picked_up_at, p.returned_at
            FROM reservation r
            LEFT JOIN loan p ON p.reservation_id = r.id
            WHERE r.status = 'CONFIRMADA'
              AND r.resource_id IN (:resourceIds)
              AND ( r.occupancy && tstzrange(CAST(:from AS timestamptz), CAST(:to AS timestamptz), '[)')
                 OR (p.picked_up_at IS NOT NULL AND p.returned_at IS NULL
                     AND r.starts_at < CAST(:to AS timestamptz)) )
            """;

    private final JdbcClient jdbc;

    OccupancyPersistenceAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Occupancy> findOccupancies(Collection<String> resourceIds, Instant from, Instant to) {
        if (resourceIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(OCCUPANCIES)
                .param("resourceIds", List.copyOf(resourceIds))
                .param("from", utc(from))
                .param("to", utc(to))
                .query(OccupancyPersistenceAdapter::toOccupancy)
                .list();
    }

    private static Occupancy toOccupancy(ResultSet row, int rowNumber) throws SQLException {
        String resourceId = row.getString("resource_id");
        Instant startsAt = instant(row, "starts_at");
        if ("ACADEMICO".equals(row.getString("origin"))) {
            return new Occupancy(resourceId, OccupancyType.ACADEMIC_BLOCK, startsAt, instant(row, "ends_at"));
        }
        if ("ESPACIO".equals(row.getString("resource_category"))) {
            return new Occupancy(resourceId, OccupancyType.SPACE_RESERVATION, startsAt, instant(row, "ends_at"));
        }
        boolean outstanding = row.getObject("picked_up_at") != null && row.getObject("returned_at") == null;
        return outstanding
                ? new Occupancy(resourceId, OccupancyType.LOAN_PICKED_UP, startsAt, null)
                : new Occupancy(resourceId, OccupancyType.LOAN_HELD, startsAt, instant(row, "ends_at"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        return row.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
