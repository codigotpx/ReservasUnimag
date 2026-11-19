package edu.unimagdalena.reservasunimag.domain.model.resource;

/**
 * Por qué cambió la ocupación de un recurso (UC7 FR-009), con el estado que deja. Solo {@link #USE_STARTED} se le
 * avisa al Módulo 1: es el único estado físico que nace de una reserva (FR-004, FR-012).
 */
public enum StatusChangeReason {
    /** UC2 confirma una reserva. */
    RESERVATION_CONFIRMED(DisplayedStatus.RESERVADO),
    /** UC3 crea un bloqueo académico. */
    ACADEMIC_BLOCK_CREATED(DisplayedStatus.BLOQUEO_ACADEMICO),
    /** Alguien registra su llegada, o una clase llega a su hora. */
    USE_STARTED(DisplayedStatus.EN_USO),
    /** UC4, en cualquiera de sus tres ramas. */
    RESERVATION_CANCELLED(DisplayedStatus.DISPONIBLE),
    /** UC9 registra una ausencia. */
    NO_SHOW_REGISTERED(DisplayedStatus.DISPONIBLE),
    /** UC12 cierra un préstamo. */
    CHECK_OUT_RECEIVED(DisplayedStatus.DISPONIBLE),
    /** UC12, al declarar perdido un préstamo; reservado para cuando exista el umbral de pérdida (P-23). */
    LOAN_DECLARED_LOST(DisplayedStatus.DISPONIBLE);

    private final DisplayedStatus newStatus;

    StatusChangeReason(DisplayedStatus newStatus) {
        this.newStatus = newStatus;
    }

    public DisplayedStatus newStatus() {
        return newStatus;
    }

    /** Si este cambio se le avisa al Módulo 1. Es FR-012 escrito en un solo sitio. */
    public boolean notifiesInventory() {
        return this == USE_STARTED;
    }

    /** Si el recurso estaba libre antes: los dos motivos que crean una ocupación. */
    public boolean createsOccupancy() {
        return this == RESERVATION_CONFIRMED || this == ACADEMIC_BLOCK_CREATED;
    }
}
