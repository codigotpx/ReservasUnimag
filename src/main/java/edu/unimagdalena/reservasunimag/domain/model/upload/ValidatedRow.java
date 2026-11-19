package edu.unimagdalena.reservasunimag.domain.model.upload;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;

import java.util.List;

/**
 * Una fila del archivo con su dictamen (FR-003). Los campos de texto son los del archivo tal cual, para que la persona
 * pueda buscarlos en el CSV; con ellos se vuelve a leer la fila al confirmar. {@code sessions} es cuántas sesiones
 * futuras produce. {@code problem}, {@code column}, {@code conflictingBlock} y {@code resourceName} son {@code null}
 * cuando no aplican, y {@code affected} solo tiene elementos en {@code DISPLACES_RESERVATIONS}.
 */
public record ValidatedRow(
        int row,
        String resourceId,
        String resourceName,
        String courseCode,
        String course,
        String group,
        String program,
        String instructor,
        String dayOfWeek,
        String start,
        String end,
        RowVerdict verdict,
        int sessions,
        String problem,
        String column,
        AcademicData conflictingBlock,
        List<AffectedReservation> affected) {

    public ValidatedRow {
        affected = List.copyOf(affected);
    }

    /** La fila cruda equivalente, para volver a leerla al confirmar. */
    public RawScheduleRow toRaw() {
        var values = new java.util.HashMap<String, String>();
        values.put(RawScheduleRow.COURSE_CODE, nullToEmpty(courseCode));
        values.put(RawScheduleRow.COURSE, nullToEmpty(course));
        values.put(RawScheduleRow.GROUP, nullToEmpty(group));
        values.put(RawScheduleRow.PROGRAM, nullToEmpty(program));
        values.put(RawScheduleRow.INSTRUCTOR, nullToEmpty(instructor));
        values.put(RawScheduleRow.RESOURCE_ID, nullToEmpty(resourceId));
        values.put(RawScheduleRow.DAY_OF_WEEK, nullToEmpty(dayOfWeek));
        values.put(RawScheduleRow.START_TIME, nullToEmpty(start));
        values.put(RawScheduleRow.END_TIME, nullToEmpty(end));
        return new RawScheduleRow(row, values);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
