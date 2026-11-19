package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.schedule;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.ExtraordinaryNeedResult;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

import java.util.List;

/** Las dos respuestas de UC3 Contratos §3: lo que pasaría, y el bloqueo creado. */
final class ExtraordinaryNeedResponse {

    private ExtraordinaryNeedResponse() {
    }

    static Object from(ExtraordinaryNeedResult result) {
        return result.applied() ? Applied.from(result) : Preview.from(result);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Preview(boolean applied, String verdict, String resourceId, String resourceName, String start, String end,
                   List<ScheduleReportResponse.AffectedBody> affectedReservations) {

        static Preview from(ExtraordinaryNeedResult result) {
            return new Preview(false, result.verdict().name(), result.resourceId(), result.resourceName(),
                    IsoTimes.offset(result.start()), IsoTimes.offset(result.end()),
                    result.affected().stream().map(ScheduleReportResponse.AffectedBody::from).toList());
        }
    }

    record Applied(boolean applied, String verdict, BlockBody block, int reservationsCancelled, int eventsQueued) {

        static Applied from(ExtraordinaryNeedResult result) {
            var block = result.block();
            var academic = result.academic();
            int cancelled = result.affected().size();
            return new Applied(true, result.verdict().name(),
                    new BlockBody(block.id().toString(), block.resourceId(), "EXTRAORDINARIO",
                            IsoTimes.offset(block.startsAt()), IsoTimes.offset(block.endsAt()), academic.course(),
                            academic.program(), academic.instructor()),
                    cancelled, cancelled);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BlockBody(String reservationId, String resourceId, String type, String start, String end, String course,
                     String program, String instructor) {
    }
}
