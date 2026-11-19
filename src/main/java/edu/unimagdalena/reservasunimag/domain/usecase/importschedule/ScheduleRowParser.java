package edu.unimagdalena.reservasunimag.domain.usecase.importschedule;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.WeeklyClass;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.upload.RawScheduleRow;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Interpreta una fila cruda según UC3 Contratos §5. Nunca lanza: una fila mal escrita sale con su problema y la
 * columna donde está, para que el reporte señale exactamente qué corregir.
 */
public final class ScheduleRowParser {

    /** Una fila leída: la clase, o el problema que lo impidió. */
    public record ParsedRow(RawScheduleRow raw, WeeklyClass weekly, String problem, String column) {

        public boolean valid() {
            return weekly != null;
        }
    }

    private static final Pattern HOUR = Pattern.compile("\\d{2}:\\d{2}");
    private static final Map<String, DayOfWeek> DAYS = Map.of(
            "LUNES", DayOfWeek.MONDAY,
            "MARTES", DayOfWeek.TUESDAY,
            "MIERCOLES", DayOfWeek.WEDNESDAY,
            "JUEVES", DayOfWeek.THURSDAY,
            "VIERNES", DayOfWeek.FRIDAY,
            "SABADO", DayOfWeek.SATURDAY);

    private ScheduleRowParser() {
    }

    public static ParsedRow parse(RawScheduleRow raw) {
        for (String column : RawScheduleRow.REQUIRED_COLUMNS) {
            if (raw.value(column).isEmpty()) {
                return problem(raw, "La columna " + column + " está vacía.", column);
            }
        }
        String day = raw.value(RawScheduleRow.DAY_OF_WEEK);
        if ("DOMINGO".equals(day)) {
            return problem(raw, "Los domingos no hay clases.", RawScheduleRow.DAY_OF_WEEK);
        }
        DayOfWeek dayOfWeek = DAYS.get(day);
        if (dayOfWeek == null) {
            return problem(raw, "La columna day_of_week no es un día válido: va de LUNES a SABADO, sin tildes y en "
                    + "mayúsculas.", RawScheduleRow.DAY_OF_WEEK);
        }
        LocalTime start = hour(raw.value(RawScheduleRow.START_TIME));
        if (start == null) {
            return problem(raw, "La columna start_time no tiene el formato HH:mm.", RawScheduleRow.START_TIME);
        }
        LocalTime end = hour(raw.value(RawScheduleRow.END_TIME));
        if (end == null) {
            return problem(raw, "La columna end_time no tiene el formato HH:mm.", RawScheduleRow.END_TIME);
        }
        if (!end.isAfter(start)) {
            return problem(raw, "La hora de fin debe ser posterior a la de inicio, el mismo día.",
                    RawScheduleRow.END_TIME);
        }
        if (start.isBefore(TimeSlot.OPENING) || end.isAfter(TimeSlot.CLOSING)) {
            return problem(raw, "La clase debe estar entre las 06:00 y las 22:00.", RawScheduleRow.START_TIME);
        }
        String instructor = raw.value(RawScheduleRow.INSTRUCTOR);
        AcademicData academic = new AcademicData(raw.value(RawScheduleRow.COURSE_CODE),
                raw.value(RawScheduleRow.COURSE), raw.value(RawScheduleRow.GROUP), raw.value(RawScheduleRow.PROGRAM),
                instructor.isEmpty() ? null : instructor);
        return new ParsedRow(raw, new WeeklyClass(raw.row(), academic, raw.value(RawScheduleRow.RESOURCE_ID),
                dayOfWeek, start, end), null, null);
    }

    private static LocalTime hour(String value) {
        if (!HOUR.matcher(value).matches()) {
            return null;
        }
        try {
            return LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    private static ParsedRow problem(RawScheduleRow raw, String problem, String column) {
        return new ParsedRow(raw, null, problem, column);
    }
}
