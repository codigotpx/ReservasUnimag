package edu.unimagdalena.reservasunimag.domain.model.reservation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** De la recogida al vencimiento de un préstamo, con los días que no consumieron plazo (UC2 FR-014). */
public record LoanPeriod(Instant pickup, Instant dueAt, int termBusinessDays, List<LocalDate> skippedNonBusinessDays) {

    public LoanPeriod {
        skippedNonBusinessDays = List.copyOf(skippedNonBusinessDays);
    }
}
