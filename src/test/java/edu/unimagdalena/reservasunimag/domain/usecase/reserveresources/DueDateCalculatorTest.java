package edu.unimagdalena.reservasunimag.domain.usecase.reserveresources;

import edu.unimagdalena.reservasunimag.domain.port.out.BusinessCalendar;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class DueDateCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private final BusinessCalendar businessCalendar = new FixedBusinessCalendar(null);
    private final DueDateCalculator dueDateCalculator = new DueDateCalculator(businessCalendar);

    @Test
    void aPickupOfTuesdayWithSevenBusinessDaysEndsThursdayAtTenPM() {
        Instant due = dueDateCalculator.calculateDueDate(Instant.parse("2026-09-01T14:30:00-05:00"), 7);

        assertThat(due.atZone(ZONE).toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(due.atZone(ZONE).toLocalTime()).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    void aPickupOfFridayWithThreeBusinessDaysEndsNextWednesday() {
        Instant due = dueDateCalculator.calculateDueDate(Instant.parse("2026-09-04T10:00:00-05:00"), 3);

        assertThat(due.atZone(ZONE).toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(due.atZone(ZONE).toLocalTime()).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    void aZeroTermEndsTheSameDayAtTenPM() {
        Instant due = dueDateCalculator.calculateDueDate(Instant.parse("2026-09-02T09:00:00-05:00"), 0);

        assertThat(due.atZone(ZONE).toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(due.atZone(ZONE).toLocalTime()).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    void aHolidayDoesNotConsumeBusinessDays() {
        DueDateCalculator calculatorWithHoliday = new DueDateCalculator(new FixedBusinessCalendar(LocalDate.of(2026, 9, 3)));

        Instant due = calculatorWithHoliday.calculateDueDate(Instant.parse("2026-09-01T14:30:00-05:00"), 2);

        assertThat(due.atZone(ZONE).toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 4));
        assertThat(due.atZone(ZONE).toLocalTime()).isEqualTo(LocalTime.of(22, 0));
    }

    private static final class FixedBusinessCalendar implements BusinessCalendar {

        private final LocalDate holiday;

        private FixedBusinessCalendar(LocalDate holiday) {
            this.holiday = holiday;
        }

        @Override
        public LocalDate addBusinessDays(LocalDate startDate, int days) {
            LocalDate current = startDate;
            int added = 0;
            while (added < days) {
                current = current.plusDays(1);
                if (!isHolidayOrWeekend(current)) {
                    added++;
                }
            }
            return current;
        }

        private boolean isHolidayOrWeekend(LocalDate date) {
            return date.equals(holiday) || date.getDayOfWeek().getValue() >= 6;
        }
    }
}
