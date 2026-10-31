package edu.unimagdalena.reservasunimag.domain.model.resource;

import java.time.Instant;
import java.util.Objects;

/**
 * Un uso confirmado de un recurso en el calendario de ocupación. Las clases dicen qué asignatura y qué programa; una
 * reserva estudiantil no dice de quién es (UC8 FR-005), solo que está tomada.
 */
public record ScheduledUse(Kind kind, Instant startsAt, Instant endsAt, String course, String program) {

    public enum Kind {
        /** Clase del horario semestral (UC3). */
        CLASS,
        /** Necesidad extraordinaria (UC3 §3). */
        EXTRAORDINARY,
        /** Reserva de un estudiante o monitor (UC2). */
        STUDENT_RESERVATION
    }

    public ScheduledUse {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
    }
}
