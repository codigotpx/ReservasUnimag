package edu.unimagdalena.reservasunimag.domain.model.compliance;

import edu.unimagdalena.reservasunimag.domain.model.error.InvalidFilterException;

import java.time.LocalDate;

/**
 * Filtros de la auditoría de consultas, todos opcionales; las fechas son días de Bogotá, ambos incluidos. Con
 * {@code personCode} el rango es obligatorio y de un semestre como mucho: sin acotar sería pedir el expediente de una
 * persona, y la auditoría existe para revisar denegaciones concretas (UC6 Contratos §3).
 */
public record SanctionCheckFilter(String personCode, SanctionCheckOutcome outcome, LocalDate from, LocalDate to) {

    public static final int MAX_MONTHS_PER_PERSON = 6;

    public SanctionCheckFilter {
        personCode = personCode == null || personCode.isBlank() ? null : personCode.strip();
        if (from != null && to != null && to.isBefore(from)) {
            throw new InvalidFilterException("to", "no puede ser anterior a from");
        }
        if (personCode != null) {
            if (from == null) {
                throw new InvalidFilterException("from", "es obligatorio cuando se filtra por personCode");
            }
            if (to == null) {
                throw new InvalidFilterException("to", "es obligatorio cuando se filtra por personCode");
            }
            if (to.isAfter(from.plusMonths(MAX_MONTHS_PER_PERSON))) {
                throw new InvalidFilterException("to",
                        "con personCode el rango no puede pasar de un semestre (" + MAX_MONTHS_PER_PERSON + " meses)");
            }
        }
    }
}
