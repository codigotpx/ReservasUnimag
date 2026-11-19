package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.ScheduleUploadRowJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ScheduleUploadRowJpaRepository
        extends JpaRepository<ScheduleUploadRowJpaEntity, ScheduleUploadRowJpaEntity.Key> {

    /** El JSON de cada fila de la carga, en el orden del archivo. */
    @Query("""
            SELECT r.data FROM ScheduleUploadRowJpaEntity r
            WHERE r.id.semesterScheduleId = :id
            ORDER BY r.id.rowNumber""")
    List<String> findDataOf(@Param("id") UUID semesterScheduleId);
}
