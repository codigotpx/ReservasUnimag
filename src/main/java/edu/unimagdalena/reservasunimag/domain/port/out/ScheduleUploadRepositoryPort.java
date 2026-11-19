package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleUpload;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Las cargas del horario con su reporte (UC3 FR-009). */
public interface ScheduleUploadRepositoryPort {

    void insert(ScheduleUpload upload);

    /** La carga con sus filas y el nombre de quien la subió. */
    Optional<ScheduleUpload> findById(UUID id);

    /** Bloquea la fila de la carga: dos confirmaciones a la vez no la aplican dos veces. */
    Optional<ScheduleUpload> lockById(UUID id);

    Page<ScheduleUpload> list(PageRequest page);

    /** Guarda el resultado; se llama dentro de la transacción que aplicó la carga. */
    void markApplied(ScheduleUpload applied);

    void markExpired(UUID id, Instant at);
}
