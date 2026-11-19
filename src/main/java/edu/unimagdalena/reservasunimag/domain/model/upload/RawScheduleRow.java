package edu.unimagdalena.reservasunimag.domain.model.upload;

import java.util.List;
import java.util.Map;

/**
 * Una fila del archivo tal como se leyó, sin interpretar. {@code row} es su número real en el archivo, que empieza en
 * 2 porque la 1 es la cabecera.
 */
public record RawScheduleRow(int row, Map<String, String> values) {

    public static final String COURSE_CODE = "course_code";
    public static final String COURSE = "course";
    public static final String GROUP = "group";
    public static final String PROGRAM = "program";
    public static final String INSTRUCTOR = "instructor";
    public static final String RESOURCE_ID = "resource_id";
    public static final String DAY_OF_WEEK = "day_of_week";
    public static final String START_TIME = "start_time";
    public static final String END_TIME = "end_time";

    /** Las columnas sin las que la carga no empieza (Contratos §5); {@code instructor} puede faltar. */
    public static final List<String> REQUIRED_COLUMNS = List.of(COURSE_CODE, COURSE, GROUP, PROGRAM, RESOURCE_ID,
            DAY_OF_WEEK, START_TIME, END_TIME);

    public RawScheduleRow {
        values = Map.copyOf(values);
    }

    /** El valor recortado de la columna, o vacío si no vino. */
    public String value(String column) {
        String value = values.get(column);
        return value == null ? "" : value.strip();
    }
}
