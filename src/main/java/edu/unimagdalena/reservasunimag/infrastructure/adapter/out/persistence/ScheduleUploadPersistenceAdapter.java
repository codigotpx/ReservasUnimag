package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.upload.AffectedReservation;
import edu.unimagdalena.reservasunimag.domain.model.upload.ApplyResult;
import edu.unimagdalena.reservasunimag.domain.model.upload.RowVerdict;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleUpload;
import edu.unimagdalena.reservasunimag.domain.model.upload.UploadImpact;
import edu.unimagdalena.reservasunimag.domain.model.upload.UploadStatus;
import edu.unimagdalena.reservasunimag.domain.model.upload.UploadSummary;
import edu.unimagdalena.reservasunimag.domain.model.upload.ValidatedRow;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.out.ScheduleUploadRepositoryPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Las cargas del horario y sus filas. El resumen, el impacto, el resultado y cada fila van en {@code jsonb}: son el
 * reporte tal como se mostró, y no se consultan por partes.
 */
@Component
class ScheduleUploadPersistenceAdapter implements ScheduleUploadRepositoryPort {

    private static final String SELECT = """
            SELECT s.id, s.academic_term, s.term_starts_on, s.term_ends_on, s.file_name, s.uploaded_by,
                   u.name AS uploaded_by_name, s.status, s.uploaded_at, s.expires_at, s.applied_at,
                   s.summary::text AS summary, s.impact::text AS impact, s.result::text AS result
            FROM semester_schedule s
            JOIN app_user u ON u.id = s.uploaded_by
            """;

    /** Una fila tal como se guarda en {@code schedule_upload_row.data}. */
    record RowData(int row, String resourceId, String resourceName, String courseCode, String course, String group,
                   String program, String instructor, String dayOfWeek, String start, String end, String verdict,
                   int sessions, String problem, String column, AcademicData conflictingBlock,
                   List<AffectedData> affected) {
    }

    record AffectedData(UUID reservationId, UUID holderId, String holderCode, String holderName, String holderRole,
                        Instant start, Instant end, String category) {
    }

    record ImpactData(Set<UUID> reservationsToCancel, Set<UUID> blocksToWithdraw) {
    }

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    ScheduleUploadPersistenceAdapter(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insert(ScheduleUpload upload) {
        jdbc.sql("""
                        INSERT INTO semester_schedule (id, academic_term, uploaded_by, uploaded_at, file_name,
                            term_starts_on, term_ends_on, status, expires_at, summary, impact)
                        VALUES (:id, :term, :uploadedBy, :validatedAt, :fileName, :start, :end, :status, :expiresAt,
                            CAST(:summary AS jsonb), CAST(:impact AS jsonb))""")
                .param("id", upload.id())
                .param("term", upload.academicTerm())
                .param("uploadedBy", upload.uploadedBy())
                .param("validatedAt", utc(upload.validatedAt()))
                .param("fileName", upload.fileName())
                .param("start", upload.termStart())
                .param("end", upload.termEnd())
                .param("status", upload.status().name())
                .param("expiresAt", upload.expiresAt() == null ? null : utc(upload.expiresAt()))
                .param("summary", objectMapper.writeValueAsString(upload.summary()))
                .param("impact", objectMapper.writeValueAsString(
                        new ImpactData(upload.impact().reservationsToCancel(), upload.impact().blocksToWithdraw())))
                .update();
        for (ValidatedRow row : upload.rows()) {
            jdbc.sql("""
                            INSERT INTO schedule_upload_row (semester_schedule_id, row_number, verdict, data)
                            VALUES (:id, :row, :verdict, CAST(:data AS jsonb))""")
                    .param("id", upload.id())
                    .param("row", row.row())
                    .param("verdict", row.verdict().name())
                    .param("data", objectMapper.writeValueAsString(toData(row)))
                    .update();
        }
    }

    @Override
    public Optional<ScheduleUpload> findById(UUID id) {
        return jdbc.sql(SELECT + "WHERE s.id = :id").param("id", id).query(this::toUpload).optional()
                .map(upload -> withRows(upload, rowsOf(id)));
    }

    @Override
    public Optional<ScheduleUpload> lockById(UUID id) {
        return jdbc.sql(SELECT + "WHERE s.id = :id FOR UPDATE OF s").param("id", id).query(this::toUpload)
                .optional()
                .map(upload -> withRows(upload, rowsOf(id)));
    }

    @Override
    public Page<ScheduleUpload> list(PageRequest page) {
        long total = jdbc.sql("SELECT count(*) FROM semester_schedule").query(Long.class).single();
        List<ScheduleUpload> items = jdbc.sql(SELECT + "ORDER BY s.uploaded_at DESC, s.id LIMIT :limit OFFSET :offset")
                .param("limit", page.size())
                .param("offset", page.offset())
                .query(this::toUpload)
                .list();
        return new Page<>(items, page.page(), page.size(), total);
    }

    @Override
    public void markApplied(ScheduleUpload applied) {
        jdbc.sql("""
                        UPDATE semester_schedule
                        SET status = 'APPLIED', applied_at = :at, result = CAST(:result AS jsonb)
                        WHERE id = :id""")
                .param("at", utc(applied.appliedAt()))
                .param("result", objectMapper.writeValueAsString(applied.result()))
                .param("id", applied.id())
                .update();
    }

    @Override
    public void markExpired(UUID id, Instant at) {
        jdbc.sql("UPDATE semester_schedule SET status = 'EXPIRED' WHERE id = :id AND status = 'VALIDATED'")
                .param("id", id)
                .update();
    }

    private List<ValidatedRow> rowsOf(UUID id) {
        return jdbc.sql("""
                        SELECT data::text FROM schedule_upload_row WHERE semester_schedule_id = :id
                        ORDER BY row_number""")
                .param("id", id)
                .query(String.class)
                .list().stream()
                .map(json -> fromData(objectMapper.readValue(json, RowData.class)))
                .toList();
    }

    private ScheduleUpload toUpload(ResultSet row, int rowNumber) throws SQLException {
        ImpactData impact = objectMapper.readValue(row.getString("impact"), ImpactData.class);
        String result = row.getString("result");
        return new ScheduleUpload(
                row.getObject("id", UUID.class),
                row.getString("academic_term"),
                row.getObject("term_starts_on", LocalDate.class),
                row.getObject("term_ends_on", LocalDate.class),
                row.getString("file_name"),
                row.getObject("uploaded_by", UUID.class),
                row.getString("uploaded_by_name"),
                UploadStatus.valueOf(row.getString("status")),
                instant(row, "uploaded_at"),
                instant(row, "expires_at"),
                instant(row, "applied_at"),
                objectMapper.readValue(row.getString("summary"), UploadSummary.class),
                new UploadImpact(impact.reservationsToCancel(), impact.blocksToWithdraw()),
                result == null ? null : objectMapper.readValue(result, ApplyResult.class),
                List.of());
    }

    private static ScheduleUpload withRows(ScheduleUpload upload, List<ValidatedRow> rows) {
        return new ScheduleUpload(upload.id(), upload.academicTerm(), upload.termStart(), upload.termEnd(),
                upload.fileName(), upload.uploadedBy(), upload.uploadedByName(), upload.status(), upload.validatedAt(),
                upload.expiresAt(), upload.appliedAt(), upload.summary(), upload.impact(), upload.result(), rows);
    }

    private static RowData toData(ValidatedRow row) {
        return new RowData(row.row(), row.resourceId(), row.resourceName(), row.courseCode(), row.course(),
                row.group(), row.program(), row.instructor(), row.dayOfWeek(), row.start(), row.end(),
                row.verdict().name(), row.sessions(), row.problem(), row.column(), row.conflictingBlock(),
                row.affected().stream().map(a -> new AffectedData(a.reservationId(),
                        a.holder() == null ? null : a.holder().userId(),
                        a.holder() == null ? null : a.holder().code(),
                        a.holder() == null ? null : a.holder().name(),
                        a.holder() == null ? null : a.holder().role().name(),
                        a.start(), a.end(), a.category().name())).toList());
    }

    private static ValidatedRow fromData(RowData data) {
        return new ValidatedRow(data.row(), data.resourceId(), data.resourceName(), data.courseCode(), data.course(),
                data.group(), data.program(), data.instructor(), data.dayOfWeek(), data.start(), data.end(),
                RowVerdict.valueOf(data.verdict()), data.sessions(), data.problem(), data.column(),
                data.conflictingBlock(),
                data.affected() == null ? List.of() : data.affected().stream().map(a -> new AffectedReservation(
                        a.reservationId(),
                        a.holderId() == null ? null : new ReservationHolder(a.holderId(), a.holderCode(),
                                a.holderName(), Role.valueOf(a.holderRole())),
                        a.start(), a.end(), ResourceCategory.valueOf(a.category()))).toList());
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
