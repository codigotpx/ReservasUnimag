package edu.unimagdalena.reservasunimag.domain.model.resource;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Occupancy;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;

import java.time.Instant;
import java.util.Collection;

/**
 * Los cinco estados que se muestran, con su prioridad (UC1, sección Estados que se muestran). Con empate de
 * prioridad gana el que se declara primero: un recurso en uso se muestra antes que reservado.
 */
public enum DisplayedStatus {
    EN_MANTENIMIENTO(1, "El recurso está en mantenimiento."),
    BLOQUEO_ACADEMICO(2, "El recurso está reservado para actividad docente en la franja consultada."),
    EN_USO(3, "El recurso está en uso en la franja consultada."),
    RESERVADO(3, "El recurso ya está reservado en la franja consultada."),
    DISPONIBLE(4, null);

    private final int priority;
    private final String reason;

    DisplayedStatus(int priority, String reason) {
        this.priority = priority;
        this.reason = reason;
    }

    /** Solo un recurso {@code DISPONIBLE} se puede seleccionar (FR-002). */
    public boolean selectable() {
        return this == DISPONIBLE;
    }

    /** Texto fijo para mostrar; {@code null} cuando el recurso es seleccionable. Nunca nombra al titular (UC8 FR-005). */
    public String reason() {
        return reason;
    }

    /**
     * El estado de un recurso en una franja: el de mayor prioridad entre el estado operativo del Módulo 1 y las
     * ocupaciones que se cruzan con la franja. El mantenimiento aplica a cualquier franja; el uso que reporta el
     * Módulo 1 solo cuenta si la franja incluye el momento actual (escenario 4).
     */
    public static DisplayedStatus resolve(OperationalStatus operational, Collection<Occupancy> occupancies,
                                          TimeSlot slot, Instant now) {
        if (operational == OperationalStatus.EN_MANTENIMIENTO) {
            return EN_MANTENIMIENTO;
        }
        DisplayedStatus result = DISPONIBLE;
        if (operational == OperationalStatus.EN_USO && slot.contains(now)) {
            result = EN_USO;
        }
        for (Occupancy occupancy : occupancies) {
            if (occupancy.overlaps(slot.startsAt(), slot.endsAt())) {
                result = stronger(result, occupancy.type().displayedStatus());
            }
        }
        return result;
    }

    private static DisplayedStatus stronger(DisplayedStatus current, DisplayedStatus candidate) {
        boolean wins = candidate.priority < current.priority
                || (candidate.priority == current.priority && candidate.ordinal() < current.ordinal());
        return wins ? candidate : current;
    }
}
