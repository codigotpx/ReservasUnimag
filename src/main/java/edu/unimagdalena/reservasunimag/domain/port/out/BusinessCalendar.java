package edu.unimagdalena.reservasunimag.domain.port.out;

import java.time.LocalDate;

/** Días hábiles para el cómputo de plazos (UC2 FR-014): excluye sábados, domingos y festivos. */
public interface BusinessCalendar {

    /** Devuelve la fecha resultante de sumar {@code days} días hábiles a {@code startDate}. */
    LocalDate addBusinessDays(LocalDate startDate, int days);
}
