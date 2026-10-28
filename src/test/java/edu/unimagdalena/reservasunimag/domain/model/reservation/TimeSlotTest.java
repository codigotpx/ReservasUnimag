package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.error.DurationExceededException;
import edu.unimagdalena.reservasunimag.domain.model.error.InvalidTimeSlotException;
import edu.unimagdalena.reservasunimag.domain.model.error.InvalidTimeSlotException.Reason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeSlotTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 1);

    private static TimeSlot slot(String start, String end) {
        return new TimeSlot(DAY, LocalTime.parse(start), LocalTime.parse(end));
    }

    private static Instant at(String time) {
        return DAY.atTime(LocalTime.parse(time)).atZone(TimeSlot.ZONE).toInstant();
    }

    @ParameterizedTest
    @CsvSource({"06:00,08:00", "20:00,22:00", "10:00,10:01"})
    void acceptsSlotsInsideTheOperatingWindow(String start, String end) {
        assertThat(slot(start, end).start()).isEqualTo(LocalTime.parse(start));
    }

    @ParameterizedTest
    @CsvSource({
            "05:00,07:00,TIME_SLOT_OUTSIDE_WINDOW",
            "05:59,06:30,TIME_SLOT_OUTSIDE_WINDOW",
            "21:00,22:01,TIME_SLOT_OUTSIDE_WINDOW",
            "21:00,01:00,TIME_SLOT_CROSSES_MIDNIGHT",
            "20:00,00:00,TIME_SLOT_CROSSES_MIDNIGHT",
            "10:00,10:00,TIME_SLOT_END_NOT_AFTER_START",
            "12:00,10:00,TIME_SLOT_END_NOT_AFTER_START"
    })
    void rejectsInvalidSlotsWithTheirReason(String start, String end, Reason reason) {
        assertThatThrownBy(() -> slot(start, end))
                .isInstanceOfSatisfying(InvalidTimeSlotException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo(reason);
                    assertThat(exception.date()).isEqualTo(DAY);
                });
    }

    @Test
    void aSlotLongerThanTheMaximumDurationIsRejectedWithDurationExceeded() {
        assertThatThrownBy(() -> slot("09:00", "12:00"))
                .isInstanceOfSatisfying(DurationExceededException.class, exception -> {
                    assertThat(exception.limitMinutes()).isEqualTo(120);
                    assertThat(exception.requestedMinutes()).isEqualTo(180);
                });
    }

    @Test
    void aSlotExactlyAtTheMaximumDurationIsAccepted() {
        assertThat(slot("09:00", "11:00").start()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void convertsToInstantsInBogotaTime() {
        TimeSlot slot = slot("10:00", "12:00");

        assertThat(slot.startsAt()).isEqualTo(Instant.parse("2026-09-01T15:00:00Z"));
        assertThat(slot.endsAt()).isEqualTo(Instant.parse("2026-09-01T17:00:00Z"));
    }

    @Test
    void adjacentSlotsDoNotOverlap() {
        TimeSlot slot = slot("10:00", "12:00");

        assertThat(slot.overlaps(at("12:00"), at("14:00"))).isFalse();
        assertThat(slot.overlaps(at("08:00"), at("10:00"))).isFalse();
    }

    @Test
    void oneMinuteAndPartialOverlapsCount() {
        TimeSlot slot = slot("10:00", "12:00");

        assertThat(slot.overlaps(at("11:59"), at("13:00"))).isTrue();
        assertThat(slot.overlaps(at("09:00"), at("10:01"))).isTrue();
        assertThat(slot.overlaps(at("10:30"), at("11:00"))).isTrue();
        assertThat(slot.overlaps(at("08:00"), at("14:00"))).isTrue();
    }

    @Test
    void aMultiDayLoanOverlapsEverySlotItCovers() {
        Instant loanStart = at("08:00");
        Instant loanEnd = DAY.plusDays(3).atTime(22, 0).atZone(TimeSlot.ZONE).toInstant();

        assertThat(slot("16:00", "18:00").overlaps(loanStart, loanEnd)).isTrue();
        assertThat(new TimeSlot(DAY.plusDays(2), LocalTime.of(6, 0), LocalTime.of(8, 0))
                .overlaps(loanStart, loanEnd)).isTrue();
    }
}
