package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity;

import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckOutcome;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckPurpose;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionScope;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Una consulta al Módulo 3 (UC6 FR-009). Las columnas {@code answered*} son lo que contestó, y van nulas si no contestó
 * o no conoce a la persona; las restricciones de la tabla aseguran que cuadren con {@code outcome}.
 */
@Entity
@Table(name = "sanction_check")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SanctionCheckJpaEntity extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "person_code", nullable = false)
    private String personCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "asked_by", nullable = false)
    private SanctionCheckPurpose askedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_category")
    private ResourceCategory resourceCategory;

    @Column(name = "asked_at", nullable = false)
    private Instant askedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SanctionCheckOutcome outcome;

    @Column(name = "answered_sanction_reason")
    private String answeredSanctionReason;

    @Column(name = "answered_ends_on")
    private LocalDate answeredEndsOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "answered_scope")
    private SanctionScope answeredScope;

    @Column(name = "end_date_missing", nullable = false)
    private boolean endDateMissing;

    @Column(name = "answered_absence_count")
    private Integer answeredAbsenceCount;

    @Column(name = "answered_late_returns")
    private Integer answeredLateReturns;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @Column(name = "failure_detail")
    private String failureDetail;
}
