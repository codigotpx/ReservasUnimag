package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleUpload;

import java.util.UUID;

/** El histórico de cargas, que es la cara consultable de la constancia de UC3 FR-009. */
public interface QueryScheduleUploadsPort {

    /** De la más reciente a la más antigua, sin las filas. */
    Page<ScheduleUpload> list(PageRequest page);

    /**
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.ScheduleUploadNotFoundException si no existe
     */
    ScheduleUpload find(UUID uploadId);
}
