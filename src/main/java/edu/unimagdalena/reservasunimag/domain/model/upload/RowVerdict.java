package edu.unimagdalena.reservasunimag.domain.model.upload;

/**
 * El dictamen de una fila del archivo (plan de UC3, Decisiones de diseño). Una carga se aplica solo si ninguna fila
 * tiene un dictamen bloqueante: un choque bloquea igual que un error de formato, porque aplicar a medias dejaría una
 * clase sin espacio sin que nadie se enterara (FR-008).
 */
public enum RowVerdict {
    /** La franja está libre. */
    OK(false),
    /** Todas sus sesiones ya existen con la misma huella (FR-007). */
    UNCHANGED(false),
    /** Se cruza con reservas estudiantiles cancelables; exige confirmar (FR-005, FR-008). */
    DISPLACES_RESERVATIONS(false),
    REJECTED_FORMAT(true),
    REJECTED_RESOURCE(true),
    /** Se cruza con otra clase: una clase no desplaza a otra (FR-006). */
    ACADEMIC_CLASH(true),
    /** El activo ya está en manos de alguien, o la reserva ya está en uso (FR-010). */
    ASSET_ALREADY_PICKED_UP(true),
    RESOURCE_UNDER_MAINTENANCE(true);

    private final boolean blocking;

    RowVerdict(boolean blocking) {
        this.blocking = blocking;
    }

    public boolean blocking() {
        return blocking;
    }
}
