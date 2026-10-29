package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicBlock;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.BlockType;
import edu.unimagdalena.reservasunimag.domain.port.out.AcademicBlockRepositoryPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** La clase de cada bloqueo académico (UC3). Participa en la transacción del caso de uso; no abre ninguna. */
@Component
class AcademicBlockPersistenceAdapter implements AcademicBlockRepositoryPort {

    private static final String SELECT = """
            SELECT b.reservation_id, r.resource_id, r.starts_at, r.ends_at, b.course_code, b.course, b.group_code,
                   b.program, b.instructor, b.type, s.academic_term, b.session_key
            FROM academic_block b
            JOIN reservation r ON r.id = b.reservation_id
            LEFT JOIN semester_schedule s ON s.id = b.semester_schedule_id
            """;

    private final JdbcClient jdbc;

    AcademicBlockPersistenceAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(UUID reservationId, UUID scheduleId, AcademicBlock block) {
        AcademicData academic = block.academic();
        jdbc.sql("""
                        INSERT INTO academic_block (reservation_id, semester_schedule_id, type, course_code, course,
                                                    group_code, program, instructor, session_key)
                        VALUES (:id, :scheduleId, :type, :courseCode, :course, :group, :program, :instructor, :key)""")
                .param("id", reservationId)
                .param("scheduleId", scheduleId)
                .param("type", block.type().name())
                .param("courseCode", academic.courseCode())
                .param("course", academic.course())
                .param("group", academic.group())
                .param("program", academic.program())
                .param("instructor", academic.instructor())
                .param("key", block.sessionKey())
                .update();
    }

    @Override
    public List<AcademicBlock> findConfirmed(Collection<String> resourceIds, Instant from, Instant to) {
        if (resourceIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT + """
                        WHERE r.status = 'CONFIRMADA' AND r.resource_id IN (:resourceIds)
                          AND r.occupancy && tstzrange(CAST(:from AS timestamptz), CAST(:to AS timestamptz), '[)')
                        ORDER BY r.starts_at""")
                .param("resourceIds", List.copyOf(resourceIds))
                .param("from", utc(from))
                .param("to", utc(to))
                .query(AcademicBlockPersistenceAdapter::toBlock)
                .list();
    }

    @Override
    public List<AcademicBlock> findUpcomingRegular(String academicTerm, Instant now) {
        return jdbc.sql(SELECT + """
                        WHERE r.status = 'CONFIRMADA' AND b.type = 'REGULAR' AND s.academic_term = :term
                          AND r.starts_at > :now
                        ORDER BY r.starts_at""")
                .param("term", academicTerm)
                .param("now", utc(now))
                .query(AcademicBlockPersistenceAdapter::toBlock)
                .list();
    }

    private static AcademicBlock toBlock(ResultSet row, int rowNumber) throws SQLException {
        return new AcademicBlock(
                row.getObject("reservation_id", UUID.class),
                row.getString("resource_id"),
                row.getObject("starts_at", OffsetDateTime.class).toInstant(),
                row.getObject("ends_at", OffsetDateTime.class).toInstant(),
                new AcademicData(row.getString("course_code"), row.getString("course"), row.getString("group_code"),
                        row.getString("program"), row.getString("instructor")),
                BlockType.valueOf(row.getString("type")),
                row.getString("academic_term"),
                row.getString("session_key"));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
