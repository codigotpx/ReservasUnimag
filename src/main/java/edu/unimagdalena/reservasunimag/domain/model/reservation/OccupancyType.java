package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.resource.DisplayedStatus;

/** Qué es lo que ocupa un recurso, y por tanto qué estado le toca mostrar. */
public enum OccupancyType {
    SPACE_RESERVATION(DisplayedStatus.RESERVADO),
    /** Préstamo apartado y aún sin recoger. */
    LOAN_HELD(DisplayedStatus.RESERVADO),
    /** Préstamo entregado y sin devolver: ocupa aunque haya pasado su vencimiento (UC2 FR-015). */
    LOAN_PICKED_UP(DisplayedStatus.EN_USO),
    ACADEMIC_BLOCK(DisplayedStatus.BLOQUEO_ACADEMICO);

    private final DisplayedStatus displayedStatus;

    OccupancyType(DisplayedStatus displayedStatus) {
        this.displayedStatus = displayedStatus;
    }

    public DisplayedStatus displayedStatus() {
        return displayedStatus;
    }
}
