package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.AcademicBlockJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Bloqueos académicos en JPQL. Las lecturas devuelven {@link AcademicBlockRow}, unida a la reserva y a su carga. */
public interface AcademicBlockJpaRepository extends JpaRepository<AcademicBlockJpaEntity, UUID> {

    String SELECT_ROW = """
            SELECT new edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.AcademicBlockRow(
                       b.reservationId, r.resourceId, r.startsAt, r.endsAt, b.courseCode, b.course, b.groupCode,
                       b.program, b.instructor, b.type, s.academicTerm, b.sessionKey)
            FROM AcademicBlockJpaEntity b
            JOIN ReservationJpaEntity r ON r.id = b.reservationId
            LEFT JOIN SemesterScheduleJpaEntity s ON s.id = b.semesterScheduleId
            """;

    /** El cruce con {@code [from, to)} va escrito igual que en {@link ReservationJpaRepository#findOccupying}. */
    @Query(SELECT_ROW + """
            WHERE r.status = CONFIRMADA AND r.resourceId IN :resourceIds
              AND r.startsAt < :to AND r.endsAt > :from
            ORDER BY r.startsAt""")
    List<AcademicBlockRow> findConfirmed(@Param("resourceIds") Collection<String> resourceIds,
                                         @Param("from") Instant from, @Param("to") Instant to);

    @Query(SELECT_ROW + """
            WHERE r.status = CONFIRMADA AND b.type = REGULAR AND s.academicTerm = :term
              AND r.startsAt > :now
            ORDER BY r.startsAt""")
    List<AcademicBlockRow> findUpcomingRegular(@Param("term") String academicTerm, @Param("now") Instant now);

    /**
     * Un bloqueo cancelado suelta su huella, para que la misma clase se pueda volver a cargar (UC3 FR-007). Si la
     * reserva no es un bloqueo académico no toca nada.
     */
    @Modifying
    @Query("UPDATE AcademicBlockJpaEntity b SET b.sessionKey = NULL WHERE b.reservationId = :id")
    void releaseSessionKey(@Param("id") UUID reservationId);
}
