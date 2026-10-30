package edu.unimagdalena.reservasunimag.domain.model.resource;

import java.time.Instant;

/**
 * El estado de un recurso en un intervalo y, si lo ocupa algo nuestro, hasta cuándo (UC8 FR-012).
 * {@code occupiedUntil} es {@code null} cuando está libre, cuando el estado lo fija solo el Módulo 1 o cuando la
 * ocupación ya no tiene una fecha honesta: un préstamo vencido y sin devolver, que es {@code overdueLoan}.
 */
public record Availability(DisplayedStatus status, Instant occupiedUntil, boolean overdueLoan) {

    static final String OVERDUE_LOAN_REASON = "El recurso está en uso y su plazo de préstamo ya venció.";

    public Availability(DisplayedStatus status, Instant occupiedUntil) {
        this(status, occupiedUntil, false);
    }

    public static Availability of(DisplayedStatus status) {
        return new Availability(status, null, false);
    }

    public boolean selectable() {
        return status.selectable();
    }

    /** Texto fijo; {@code null} cuando es seleccionable. Nunca nombra al titular (UC8 FR-005). */
    public String reason() {
        return overdueLoan ? OVERDUE_LOAN_REASON : status.reason();
    }
}
