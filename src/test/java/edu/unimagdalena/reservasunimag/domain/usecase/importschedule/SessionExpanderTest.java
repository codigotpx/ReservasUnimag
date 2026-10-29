package edu.unimagdalena.reservasunimag.domain.usecase.importschedule;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.WeeklyClass;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** De una clase semanal a las sesiones del periodo (plan de UC3, Decisiones de diseño). */
class SessionExpanderTest {

    private static final LocalDate TERM_START = LocalDate.of(2026, 8, 10);
    private static final LocalDate TERM_END = LocalDate.of(2026, 11, 27);
    private static final Instant BEFORE_TERM = LocalDate.of(2026, 8, 1).atStartOfDay(TimeSlot.ZONE).toInstant();
    private static final AcademicData NETWORKS = new AcademicData("IS-402", "Redes", "1", "Sistemas", null);

    private static WeeklyClass on(DayOfWeek day) {
        return new WeeklyClass(2, NETWORKS, "ESP-0412", day, LocalTime.of(8, 0), LocalTime.of(10, 0));
    }

    private static SessionExpander expander(Set<LocalDate> holidays, boolean saturdayIsBusiness) {
        return new SessionExpander(date -> !holidays.contains(date) && date.getDayOfWeek() != DayOfWeek.SUNDAY
                && (saturdayIsBusiness || date.getDayOfWeek() != DayOfWeek.SATURDAY));
    }

    @Test
    void aMondayClassInASixteenWeekTermHasSixteenSessions() {
        var expansion = expander(Set.of(), false).expand(on(DayOfWeek.MONDAY), "2026-2", TERM_START, TERM_END,
                BEFORE_TERM);

        assertThat(expansion.sessions()).hasSize(16);
        assertThat(expansion.sessions().get(0).startsAt())
                .isEqualTo(LocalDate.of(2026, 8, 10).atTime(8, 0).atZone(TimeSlot.ZONE).toInstant());
        assertThat(expansion.skippedNonBusinessDay()).isZero();
    }

    @Test
    void aHolidayOnMondayLeavesFifteenAndIsReported() {
        var expansion = expander(Set.of(LocalDate.of(2026, 8, 17)), false).expand(on(DayOfWeek.MONDAY), "2026-2",
                TERM_START, TERM_END, BEFORE_TERM);

        assertThat(expansion.sessions()).hasSize(15);
        assertThat(expansion.skippedNonBusinessDay()).isEqualTo(1);
    }

    @Test
    void saturdayGeneratesSessionsOnlyIfItIsABusinessDay() {
        assertThat(expander(Set.of(), true).expand(on(DayOfWeek.SATURDAY), "2026-2", TERM_START, TERM_END,
                BEFORE_TERM).sessions()).hasSize(15);
        var skipped = expander(Set.of(), false).expand(on(DayOfWeek.SATURDAY), "2026-2", TERM_START, TERM_END,
                BEFORE_TERM);
        assertThat(skipped.sessions()).isEmpty();
        assertThat(skipped.skippedNonBusinessDay()).isEqualTo(15);
    }

    @Test
    void aLateUploadDoesNotCreateSessionsInThePast() {
        Instant midTerm = LocalDate.of(2026, 10, 1).atStartOfDay(TimeSlot.ZONE).toInstant();

        assertThat(expander(Set.of(), false).expand(on(DayOfWeek.MONDAY), "2026-2", TERM_START, TERM_END, midTerm)
                .sessions()).hasSize(8).allSatisfy(session -> assertThat(session.startsAt()).isAfter(midTerm));
    }

    @Test
    void theSameClassAlwaysGetsTheSameKeyAndAnotherGroupAnother() {
        var first = expander(Set.of(), false).expand(on(DayOfWeek.MONDAY), "2026-2", TERM_START, TERM_END,
                BEFORE_TERM);
        var again = expander(Set.of(), false).expand(on(DayOfWeek.MONDAY), "2026-2", TERM_START, TERM_END,
                BEFORE_TERM);
        var otherGroup = expander(Set.of(), false).expand(new WeeklyClass(2,
                new AcademicData("IS-402", "Redes", "2", "Sistemas", null), "ESP-0412", DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(10, 0)), "2026-2", TERM_START, TERM_END, BEFORE_TERM);

        assertThat(first.sessions().get(0).sessionKey()).isEqualTo(again.sessions().get(0).sessionKey());
        assertThat(first.sessions().get(0).sessionKey()).isNotEqualTo(otherGroup.sessions().get(0).sessionKey());
        assertThat(first.sessions()).extracting(s -> s.sessionKey()).doesNotHaveDuplicates();
    }
}
