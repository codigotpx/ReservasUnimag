package edu.unimagdalena.reservasunimag.domain.usecase.reserveresources;

import edu.unimagdalena.reservasunimag.domain.port.out.BusinessCalendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** Calcula el vencimiento de un préstamo: plazo en días hábiles desde el día siguiente a la recogida, a las 22:00 (UC2 FR-012 a FR-014). */
public class DueDateCalculator {

    public static final ZoneId ZONE = ZoneId.of("America/Bogota");
    public static final LocalTime DUE_HOUR = LocalTime.of(22, 0);

    private final BusinessCalendar businessCalendar;

    public DueDateCalculator(BusinessCalendar businessCalendar) {
        this.businessCalendar = businessCalendar;
    }

    public Instant calculateDueDate(Instant pickup, int termBusinessDays) {
        LocalDate pickupDate = LocalDate.ofInstant(pickup, ZONE);
        LocalDate dueDate = termBusinessDays <= 0
                ? pickupDate
                : businessCalendar.addBusinessDays(pickupDate, termBusinessDays);
        return dueDate.atTime(DUE_HOUR).atZone(ZONE).toInstant();
    }
}
