package edu.unimagdalena.reservasunimag.infrastructure.config;

import edu.unimagdalena.reservasunimag.domain.port.out.BusinessCalendar;
import org.springframework.context.annotation.Configuration;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** Excluye sábados, domingos y festivos de las propiedades para los plazos (UC2 FR-014). */
@Configuration(proxyBeanMethods = false)
public class BusinessCalendarConfig implements BusinessCalendar {

    private final ReservationProperties properties;

    public BusinessCalendarConfig(ReservationProperties properties) {
        this.properties = properties;
    }

    @Override
    public LocalDate addBusinessDays(LocalDate startDate, int days) {
        LocalDate current = startDate;
        int added = 0;
        while (added < days) {
            current = current.plusDays(1);
            if (isBusinessDay(current)) {
                added++;
            }
        }
        return current;
    }

    private boolean isBusinessDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
        return !isWeekend && !properties.holidays().contains(date.toString());
    }
}
