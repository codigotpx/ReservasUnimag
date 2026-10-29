package edu.unimagdalena.reservasunimag.domain.port.out;

import java.time.LocalDate;

/** Qué días consumen plazo de préstamo (UC2 FR-014). */
public interface BusinessCalendar {

    boolean isBusinessDay(LocalDate date);
}
