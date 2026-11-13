package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

/**
 * Solo lo que las consultas de bloqueos académicos leen de una carga del horario: su periodo. Es de solo lectura
 * porque las cargas todavía las escribe {@code ScheduleUploadPersistenceAdapter} con SQL; cuando ese adaptador pase a
 * JPA, esta entidad se completa con el resto de columnas.
 */
@Entity
@Immutable
@Table(name = "semester_schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SemesterScheduleJpaEntity {

    @Id
    private UUID id;

    @Column(name = "academic_term", nullable = false)
    private String academicTerm;
}
