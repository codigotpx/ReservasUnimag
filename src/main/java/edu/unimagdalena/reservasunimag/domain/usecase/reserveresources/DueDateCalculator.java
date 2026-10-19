package edu.unimagdalena.reservasunimag.domain.usecase.reserveresources;

import edu.unimagdalena.reservasunimag.domain.model.reservation.LoanPeriod;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.port.out.BusinessCalendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Vencimiento de un préstamo: el plazo se cuenta en días hábiles desde el día siguiente a la recogida y la hora
 * queda fija en las 22:00 de Bogotá (UC2 FR-012 a FR-014). Un plazo de 0 es uso en sitio y vence el mismo día.
 */
public class DueDateCalculator {

    private final BusinessCalendar calendar;

    public DueDateCalculator(BusinessCalendar calendar) {
        this.calendar = calendar;
    }

    public LoanPeriod calculate(LocalDate pickupDate, LocalTime pickupTime, int termBusinessDays) {
        if (termBusinessDays < 0) {
            throw new IllegalArgumentException("el plazo no puede ser negativo");
        }
        List<LocalDate> skipped = new ArrayList<>();
        LocalDate due = pickupDate;
        int counted = 0;
        while (counted < termBusinessDays) {
            due = due.plusDays(1);
            if (calendar.isBusinessDay(due)) {
                counted++;
            } else {
                skipped.add(due);
            }
        }
        Instant pickup = pickupDate.atTime(pickupTime).atZone(TimeSlot.ZONE).toInstant();
        Instant dueAt = due.atTime(TimeSlot.CLOSING).atZone(TimeSlot.ZONE).toInstant();
        return new LoanPeriod(pickup, dueAt, termBusinessDays, skipped);
    }
}
