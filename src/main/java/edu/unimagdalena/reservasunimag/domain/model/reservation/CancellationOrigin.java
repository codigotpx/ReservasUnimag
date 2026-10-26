package edu.unimagdalena.reservasunimag.domain.model.reservation;

/**
 * Quién deshizo la reserva. Cada origen deja un estado final distinto y solo el del titular se le puede atribuir a
 * la persona (UC4 FR-005, UC11 FR-003, FR-006).
 */
public enum CancellationOrigin {
    /** La persona canceló su propia reserva dentro del plazo. */
    HOLDER(ReservationStatus.CANCELADA, true),
    /** Una clase de la carga de horarios desplazó la reserva (UC3). */
    ACADEMIC_PRIORITY(ReservationStatus.CANCELADA_POR_PRIORIDAD_ACADEMICA, false),
    /** El recurso quedó fuera de servicio (UC4 FR-010). */
    RESOURCE_UNAVAILABLE(ReservationStatus.CANCELADA_POR_RECURSO_NO_DISPONIBLE, false);

    private final ReservationStatus status;
    private final boolean attributableToPerson;

    CancellationOrigin(ReservationStatus status, boolean attributableToPerson) {
        this.status = status;
        this.attributableToPerson = attributableToPerson;
    }

    public ReservationStatus status() {
        return status;
    }

    public boolean attributableToPerson() {
        return attributableToPerson;
    }

    /** El origen que corresponde a un estado de cancelación; para cuando no quedó guardado de otra forma. */
    public static CancellationOrigin of(ReservationStatus status) {
        for (CancellationOrigin origin : values()) {
            if (origin.status == status) {
                return origin;
            }
        }
        throw new IllegalArgumentException(status + " no es un estado de cancelación");
    }
}
