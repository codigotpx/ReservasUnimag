package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;

/** Lo que se le informa a la persona antes de confirmar un préstamo (UC2 FR-001). */
public record ExpectedDueDate(Resource resource, LoanPeriod period) {
}
