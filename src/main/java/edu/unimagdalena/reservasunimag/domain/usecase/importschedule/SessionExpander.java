package edu.unimagdalena.reservasunimag.domain.usecase.importschedule;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.BlockType;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.ClassSession;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.SessionKeys;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.WeeklyClass;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.port.out.BusinessCalendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * De una clase semanal a sus sesiones del periodo, una por semana. Salta los días no hábiles con el mismo calendario
 * de los préstamos (UC2), y las sesiones que ya empezaron: una carga tardía no crea bloqueos en el pasado.
 */
public class SessionExpander {

    /** Las sesiones que quedan y cuántas se omitieron por caer en un día no hábil. */
    public record Expansion(List<ClassSession> sessions, int skippedNonBusinessDay) {

        public Expansion {
            sessions = List.copyOf(sessions);
        }
    }

    private final BusinessCalendar calendar;

    public SessionExpander(BusinessCalendar calendar) {
        this.calendar = calendar;
    }

    public Expansion expand(WeeklyClass weekly, String academicTerm, LocalDate termStart, LocalDate termEnd,
                            Instant now) {
        List<ClassSession> sessions = new ArrayList<>();
        int skipped = 0;
        for (LocalDate date = termStart.with(TemporalAdjusters.nextOrSame(weekly.day()));
             !date.isAfter(termEnd); date = date.plusWeeks(1)) {
            if (!calendar.isBusinessDay(date)) {
                skipped++;
                continue;
            }
            Instant start = date.atTime(weekly.start()).atZone(TimeSlot.ZONE).toInstant();
            Instant end = date.atTime(weekly.end()).atZone(TimeSlot.ZONE).toInstant();
            if (!start.isAfter(now)) {
                continue;
            }
            sessions.add(new ClassSession(weekly.resourceId(), start, end, weekly.academic(), BlockType.REGULAR,
                    SessionKeys.of(academicTerm, weekly.resourceId(), start, end, weekly.academic().courseCode(),
                            weekly.academic().group())));
        }
        return new Expansion(sessions, skipped);
    }
}
