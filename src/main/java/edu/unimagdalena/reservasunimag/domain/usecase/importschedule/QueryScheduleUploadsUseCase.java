package edu.unimagdalena.reservasunimag.domain.usecase.importschedule;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.error.ScheduleUploadNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleUpload;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryScheduleUploadsPort;
import edu.unimagdalena.reservasunimag.domain.port.out.ScheduleUploadRepositoryPort;

import java.util.UUID;

/** El histórico de cargas de UC3 (FR-009). */
public class QueryScheduleUploadsUseCase implements QueryScheduleUploadsPort {

    private final ScheduleUploadRepositoryPort uploads;

    public QueryScheduleUploadsUseCase(ScheduleUploadRepositoryPort uploads) {
        this.uploads = uploads;
    }

    @Override
    public Page<ScheduleUpload> list(PageRequest page) {
        return uploads.list(page);
    }

    @Override
    public ScheduleUpload find(UUID uploadId) {
        return uploads.findById(uploadId).orElseThrow(() -> new ScheduleUploadNotFoundException(uploadId));
    }
}
