package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.schedule;

import edu.unimagdalena.reservasunimag.domain.model.upload.ApplyResult;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleUpload;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

/** La carga aplicada, como la fija UC3 Contratos §2. El {@code result} es también lo que queda como constancia. */
record ScheduleConfirmationResponse(String uploadId, String academicTerm, String status, String appliedAt,
                                    UserBody appliedBy, ApplyResult result) {

    static ScheduleConfirmationResponse from(ScheduleUpload upload) {
        return new ScheduleConfirmationResponse(upload.id().toString(), upload.academicTerm(), upload.status().name(),
                IsoTimes.offset(upload.appliedAt()),
                new UserBody(upload.uploadedBy().toString(), upload.uploadedByName()), upload.result());
    }

    record UserBody(String userId, String name) {
    }
}
