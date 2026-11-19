package edu.unimagdalena.reservasunimag.domain.model.academicblock;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/** Una necesidad académica puntual que se impone sobre las reservas estudiantiles (UC3 FR-005). */
public record ExtraordinaryNeed(String resourceId, LocalDate date, LocalTime start, LocalTime end,
                                AcademicData academic) {

    public ExtraordinaryNeed {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(academic, "academic");
    }
}
