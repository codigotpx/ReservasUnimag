package edu.unimagdalena.reservasunimag.domain.usecase.reserveresources;

import edu.unimagdalena.reservasunimag.domain.model.reservation.LoanPeriod;
import edu.unimagdalena.reservasunimag.domain.port.out.BusinessCalendar;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DueDateCalculatorTest {

    private static BusinessCalendar weekdaysExcept(LocalDate... holidays) {
        Set<LocalDate> off = Set.of(holidays);
        return date -> date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY
                && !off.contains(date);
    }

    private final DueDateCalculator calculator = new DueDateCalculator(weekdaysExcept());

    @Test
    void tuesdayWithATermOfSevenDaysDueOnThursdayTheTenthAtTenPm() {
        LoanPeriod period = calculator.calculate(LocalDate.of(2026, 9, 1), LocalTime.of(14, 30), 7);

        // 2026-09-01 es martes: se saltan el sábado 5 y el domingo 6.
        assertThat(period.pickup()).isEqualTo(Instant.parse("2026-09-01T19:30:00Z"));
        assertThat(period.dueAt()).isEqualTo(Instant.parse("2026-09-11T03:00:00Z"));
        assertThat(period.termBusinessDays()).isEqualTo(7);
        assertThat(period.skippedNonBusinessDays())
                .containsExactly(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 6));
    }

    @Test
    void fridayWithATermOfThreeDaysDueOnTheFollowingWednesday() {
        LoanPeriod period = calculator.calculate(LocalDate.of(2026, 9, 4), LocalTime.of(9, 0), 3);

        assertThat(period.dueAt()).isEqualTo(Instant.parse("2026-09-10T03:00:00Z")); // miércoles 9, 22:00 Bogotá
        assertThat(period.skippedNonBusinessDays())
                .containsExactly(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 6));
    }

    @Test
    void aTermOfZeroIsOnSiteUseAndDueTheSameDayAtTenPm() {
        LoanPeriod period = calculator.calculate(LocalDate.of(2026, 9, 1), LocalTime.of(10, 0), 0);

        assertThat(period.dueAt()).isEqualTo(Instant.parse("2026-09-02T03:00:00Z"));
        assertThat(period.skippedNonBusinessDays()).isEmpty();
    }

    @Test
    void aHolidayDoesNotConsumeTerm() {
        var withHoliday = new DueDateCalculator(weekdaysExcept(LocalDate.of(2026, 9, 3)));

        LoanPeriod period = withHoliday.calculate(LocalDate.of(2026, 9, 1), LocalTime.of(10, 0), 3);

        // miércoles 2 (1), jueves 3 festivo, viernes 4 (2), lunes 7 (3)
        assertThat(period.dueAt()).isEqualTo(Instant.parse("2026-09-08T03:00:00Z"));
        assertThat(period.skippedNonBusinessDays()).contains(LocalDate.of(2026, 9, 3));
    }

    @Test
    void pickingUpOnAWeekendStillCountsFromTheNextDay() {
        LoanPeriod period = calculator.calculate(LocalDate.of(2026, 9, 5), LocalTime.of(10, 0), 1);

        assertThat(period.dueAt()).isEqualTo(Instant.parse("2026-09-08T03:00:00Z")); // lunes 7, 22:00
    }

    @Test
    void aNegativeTermIsAProgrammingError() {
        assertThatThrownBy(() -> calculator.calculate(LocalDate.of(2026, 9, 1), LocalTime.of(10, 0), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
