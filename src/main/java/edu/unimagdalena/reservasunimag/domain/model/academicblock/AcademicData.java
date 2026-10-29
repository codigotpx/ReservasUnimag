package edu.unimagdalena.reservasunimag.domain.model.academicblock;

import java.util.Objects;

/**
 * La clase detrás de un bloqueo académico: lo que la identifica (código y grupo) y lo que se le informa al Módulo 3
 * en la ficha (UC10 FR-009). {@code instructor} es {@code null} si todavía no se asignó.
 */
public record AcademicData(String courseCode, String course, String group, String program, String instructor) {

    public AcademicData {
        Objects.requireNonNull(courseCode, "courseCode");
        Objects.requireNonNull(course, "course");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(program, "program");
    }

    /** Cómo se nombra la clase en un mensaje: código y grupo, nunca el docente. */
    public String label() {
        return courseCode + " grupo " + group;
    }
}
