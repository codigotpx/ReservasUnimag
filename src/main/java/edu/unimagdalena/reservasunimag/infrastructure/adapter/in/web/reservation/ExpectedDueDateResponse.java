package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import edu.unimagdalena.reservasunimag.domain.model.reservation.ExpectedDueDate;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

import java.util.List;

/** UC2 Contratos §2: lo que se informa antes de confirmar un préstamo. */
record ExpectedDueDateResponse(String resourceId, String name, String pickup, String dueAt, int termBusinessDays,
                               List<String> skippedNonBusinessDays) {

    static ExpectedDueDateResponse from(ExpectedDueDate expected) {
        var period = expected.period();
        return new ExpectedDueDateResponse(expected.resource().id(), expected.resource().name(),
                IsoTimes.offset(period.pickup()), IsoTimes.offset(period.dueAt()), period.termBusinessDays(),
                period.skippedNonBusinessDays().stream().map(Object::toString).toList());
    }
}
