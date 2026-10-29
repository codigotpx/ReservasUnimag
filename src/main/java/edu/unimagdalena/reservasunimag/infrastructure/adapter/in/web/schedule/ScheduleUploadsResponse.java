package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.schedule;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleUpload;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

import java.util.List;

/** El histórico de cargas de UC3 Contratos §4. */
record ScheduleUploadsResponse(List<Item> uploads, PaginationBody pagination) {

    static ScheduleUploadsResponse from(Page<ScheduleUpload> page) {
        return new ScheduleUploadsResponse(page.items().stream().map(Item::from).toList(),
                new PaginationBody(page.page(), page.pageSize(), page.totalPages(), page.total()));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Item(String uploadId, String academicTerm, String status, String fileName,
                ScheduleConfirmationResponse.UserBody uploadedBy, String validatedAt, String appliedAt,
                ResultBody result) {

        static Item from(ScheduleUpload upload) {
            var result = upload.result();
            return new Item(upload.id().toString(), upload.academicTerm(), upload.status().name(), upload.fileName(),
                    new ScheduleConfirmationResponse.UserBody(upload.uploadedBy().toString(), upload.uploadedByName()),
                    IsoTimes.offset(upload.validatedAt()),
                    upload.appliedAt() == null ? null : IsoTimes.offset(upload.appliedAt()),
                    result == null ? null : new ResultBody(result.blocksCreated(), result.blocksUnchanged(),
                            result.blocksWithdrawn(), result.reservationsCancelled()));
        }
    }

    record ResultBody(int blocksCreated, int blocksUnchanged, int blocksWithdrawn, int reservationsCancelled) {
    }

    record PaginationBody(int page, int pageSize, int totalPages, long total) {
    }
}
