package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.ExtraordinaryNeed;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.ExtraordinaryNeedResult;

/**
 * UC3 FR-005: una necesidad académica puntual. Sin {@code confirm} solo muestra el impacto; con él cancela las reservas
 * estudiantiles que se crucen y crea el bloqueo {@code EXTRAORDINARIO}.
 */
public interface RegisterExtraordinaryNeedPort {

    /**
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException  si el Módulo 1 no conoce el recurso
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.AcademicConflictException  si la franja no se puede tomar
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.InvalidTimeSlotException   fuera de 06:00–22:00 o cruzando la medianoche
     */
    ExtraordinaryNeedResult register(ExtraordinaryNeed need, boolean confirm);
}
