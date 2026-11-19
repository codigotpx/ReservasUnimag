package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReturns;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReviewFilter;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReviews;

/** Las dos listas de lo que espera un check-out del Módulo 3 (UC12 FR-009, FR-014). */
public interface QueryPendingCheckOutsPort {

    PendingReturns pendingReturns(PageRequest page);

    PendingReviews pendingReviews(PendingReviewFilter filter, PageRequest page);
}
