package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity;

import edu.unimagdalena.reservasunimag.domain.model.upload.RowVerdict;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.util.UUID;

/**
 * Una fila del archivo de una carga con su dictamen (UC3 FR-003). {@code data} es la fila tal como se reportó, en
 * {@code jsonb}. La clave es compuesta, así que no hereda de {@link AssignedIdEntity}, pero hace lo mismo: sin
 * {@link Persistable}, {@code save()} haría un {@code SELECT} antes de cada {@code INSERT}, y una carga trae miles.
 */
@Entity
@Table(name = "schedule_upload_row")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleUploadRowJpaEntity implements Persistable<ScheduleUploadRowJpaEntity.Key> {

    @Embeddable
    public record Key(
            @Column(name = "semester_schedule_id") UUID semesterScheduleId,
            @Column(name = "row_number") int rowNumber) implements Serializable {
    }

    @EmbeddedId
    private Key id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RowVerdict verdict;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String data;

    @Transient
    private boolean isNew = true;

    public ScheduleUploadRowJpaEntity(UUID semesterScheduleId, int rowNumber, RowVerdict verdict, String data) {
        this.id = new Key(semesterScheduleId, rowNumber);
        this.verdict = verdict;
        this.data = data;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }
}
