package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.BlockType;
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

import java.util.UUID;

/**
 * La clase de un bloqueo académico, con la misma clave que su reserva. {@code sessionKey} es la huella que hace
 * idempotente la recarga del horario; al cancelar el bloqueo se pone a {@code null} con un {@code UPDATE} de JPQL.
 */
@Entity
@Table(name = "academic_block")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AcademicBlockJpaEntity extends AssignedIdEntity {

    @Id
    @Column(name = "reservation_id")
    private UUID reservationId;

    /** {@code null} en una necesidad extraordinaria, que llega sin carga de horario. */
    @Column(name = "semester_schedule_id")
    private UUID semesterScheduleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BlockType type;

    @Column(name = "course_code", nullable = false)
    private String courseCode;

    @Column(nullable = false)
    private String course;

    @Column(name = "group_code", nullable = false)
    private String groupCode;

    @Column(nullable = false)
    private String program;

    private String instructor;

    @Column(name = "session_key")
    private String sessionKey;

    @Override
    public UUID getId() {
        return reservationId;
    }
}
