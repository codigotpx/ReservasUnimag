package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity;

import edu.unimagdalena.reservasunimag.domain.model.upload.UploadStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Una carga del horario (UC3), guardada entre validar y confirmar. El resumen, el impacto y el resultado son el
 * reporte tal como se mostró y no se consultan por partes: van en {@code jsonb} como texto, y los serializa el
 * adaptador con el mismo {@code ObjectMapper} de la aplicación. Los cambios de estado son {@code UPDATE} de JPQL.
 */
@Entity
@Table(name = "semester_schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SemesterScheduleJpaEntity extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "academic_term", nullable = false)
    private String academicTerm;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    /** El instante de la validación. */
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "term_starts_on", nullable = false)
    private LocalDate termStartsOn;

    @Column(name = "term_ends_on", nullable = false)
    private LocalDate termEndsOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String impact;

    @JdbcTypeCode(SqlTypes.JSON)
    private String result;

    public SemesterScheduleJpaEntity(UUID id, String academicTerm, UUID uploadedBy, Instant uploadedAt,
                                     String fileName, LocalDate termStartsOn, LocalDate termEndsOn,
                                     UploadStatus status, Instant expiresAt, String summary, String impact) {
        this.id = id;
        this.academicTerm = academicTerm;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        this.fileName = fileName;
        this.termStartsOn = termStartsOn;
        this.termEndsOn = termEndsOn;
        this.status = status;
        this.expiresAt = expiresAt;
        this.summary = summary;
        this.impact = impact;
    }
}
