package edu.unimagdalena.reservasunimag.domain.model.academicblock;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/** Una fila del archivo ya leída: una clase que se repite cada semana en el mismo recurso y la misma franja. */
public record WeeklyClass(int row, AcademicData academic, String resourceId, DayOfWeek day, LocalTime start,
                          LocalTime end) {

    public WeeklyClass {
        Objects.requireNonNull(academic, "academic");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
    }
}
