package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.SemesterScheduleJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Las cargas del horario en JPQL. Las lecturas devuelven {@link ScheduleUploadRow} y los cambios de estado son
 * {@code UPDATE} que solo tocan la carga si sigue en el estado esperado.
 */
public interface SemesterScheduleJpaRepository extends JpaRepository<SemesterScheduleJpaEntity, UUID> {

    String SELECT_ROW = """
            SELECT new edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.ScheduleUploadRow(
                       s.id, s.academicTerm, s.termStartsOn, s.termEndsOn, s.fileName, s.uploadedBy, u.name,
                       s.status, s.uploadedAt, s.expiresAt, s.appliedAt, s.summary, s.impact, s.result)
            FROM SemesterScheduleJpaEntity s
            JOIN UserJpaEntity u ON u.id = s.uploadedBy
            """;

    @Query(SELECT_ROW + "WHERE s.id = :id")
    Optional<ScheduleUploadRow> findRow(@Param("id") UUID id);

    /** Dos confirmaciones a la vez de la misma carga se ponen en fila: la segunda ya la ve aplicada. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s.id FROM SemesterScheduleJpaEntity s WHERE s.id = :id")
    Optional<UUID> lockById(@Param("id") UUID id);

    @Query(SELECT_ROW + "ORDER BY s.uploadedAt DESC, s.id")
    List<ScheduleUploadRow> findPage(Pageable page);

    @Modifying
    @Query("""
            UPDATE SemesterScheduleJpaEntity s SET s.status = APPLIED, s.appliedAt = :at, s.result = :result
            WHERE s.id = :id""")
    void markApplied(@Param("id") UUID id, @Param("at") Instant at, @Param("result") String result);

    /** Solo vence una carga que sigue validada: una ya aplicada no cambia. */
    @Modifying
    @Query("UPDATE SemesterScheduleJpaEntity s SET s.status = EXPIRED WHERE s.id = :id AND s.status = VALIDATED")
    void markExpired(@Param("id") UUID id);
}
