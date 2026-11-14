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
import edu.unimagdalena.reservasunimag.domain.model.upload.UploadSummary;
import edu.unimagdalena.reservasunimag.domain.model.upload.ValidatedRow;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.out.ScheduleUploadRepositoryPort;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.ScheduleUploadRowJpaEntity;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.SemesterScheduleJpaEntity;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.ScheduleUploadRow;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.ScheduleUploadRowJpaRepository;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.SemesterScheduleJpaRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Las cargas del horario y sus filas, con JPA. El resumen, el impacto, el resultado y cada fila van en {@code jsonb}:
 * son el reporte tal como se mostró, y no se consultan por partes. Este adaptador los serializa con el
 * {@code ObjectMapper} de la aplicación y las entidades los guardan como texto JSON.
 */
@Component
class ScheduleUploadPersistenceAdapter implements ScheduleUploadRepositoryPort {

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

    private final SemesterScheduleJpaRepository schedules;
    private final ScheduleUploadRowJpaRepository rows;
    private final ObjectMapper objectMapper;

    ScheduleUploadPersistenceAdapter(SemesterScheduleJpaRepository schedules, ScheduleUploadRowJpaRepository rows,
                                     ObjectMapper objectMapper) {
        this.schedules = schedules;
        this.rows = rows;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insert(ScheduleUpload upload) {
        schedules.saveAndFlush(new SemesterScheduleJpaEntity(upload.id(), upload.academicTerm(), upload.uploadedBy(),
                upload.validatedAt(), upload.fileName(), upload.termStart(), upload.termEnd(), upload.status(),
                upload.expiresAt(), objectMapper.writeValueAsString(upload.summary()),
                objectMapper.writeValueAsString(
                        new ImpactData(upload.impact().reservationsToCancel(), upload.impact().blocksToWithdraw()))));
        rows.saveAllAndFlush(upload.rows().stream()
                .map(row -> new ScheduleUploadRowJpaEntity(upload.id(), row.row(), row.verdict(),
                        objectMapper.writeValueAsString(toData(row))))
                .toList());
    }

    @Override
    public Optional<ScheduleUpload> findById(UUID id) {
        return schedules.findRow(id).map(this::toUpload).map(upload -> withRows(upload, rowsOf(id)));
    }

    @Override
    public Optional<ScheduleUpload> lockById(UUID id) {
        return schedules.lockById(id).flatMap(this::findById);
    }

    @Override
    public Page<ScheduleUpload> list(PageRequest page) {
        long total = schedules.count();
        List<ScheduleUpload> items = schedules.findPage(
                        org.springframework.data.domain.PageRequest.of(page.page() - 1, page.size())).stream()
                .map(this::toUpload)
                .toList();
        return new Page<>(items, page.page(), page.size(), total);
    }

    @Override
    public void markApplied(ScheduleUpload applied) {
        schedules.markApplied(applied.id(), applied.appliedAt(), objectMapper.writeValueAsString(applied.result()));
    }

    @Override
    public void markExpired(UUID id, Instant at) {
        schedules.markExpired(id);
    }

    private List<ValidatedRow> rowsOf(UUID id) {
        return rows.findDataOf(id).stream()
                .map(json -> fromData(objectMapper.readValue(json, RowData.class)))
                .toList();
    }

    private ScheduleUpload toUpload(ScheduleUploadRow row) {
        ImpactData impact = objectMapper.readValue(row.impact(), ImpactData.class);
        return new ScheduleUpload(row.id(), row.academicTerm(), row.termStartsOn(), row.termEndsOn(), row.fileName(),
                row.uploadedBy(), row.uploadedByName(), row.status(), row.uploadedAt(), row.expiresAt(),
                row.appliedAt(), objectMapper.readValue(row.summary(), UploadSummary.class),
                new UploadImpact(impact.reservationsToCancel(), impact.blocksToWithdraw()),
                row.result() == null ? null : objectMapper.readValue(row.result(), ApplyResult.class),
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
}
