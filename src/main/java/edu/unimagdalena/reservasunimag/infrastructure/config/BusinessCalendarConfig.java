package edu.unimagdalena.reservasunimag.infrastructure.config;

import edu.unimagdalena.reservasunimag.domain.port.out.BusinessCalendar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.DayOfWeek;
import java.util.Set;

/** Sábados, domingos y los festivos de {@code reservations.holidays} no consumen plazo de préstamo (UC2 FR-014). */
@Configuration(proxyBeanMethods = false)
class BusinessCalendarConfig {

    @Bean
    BusinessCalendar businessCalendar(ReservationProperties properties) {
        Set<java.time.LocalDate> holidays = Set.copyOf(properties.holidays());
        return date -> date.getDayOfWeek() != DayOfWeek.SATURDAY
                && date.getDayOfWeek() != DayOfWeek.SUNDAY
                && !holidays.contains(date);
    }
}
