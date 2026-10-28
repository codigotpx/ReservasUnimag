package edu.unimagdalena.reservasunimag.domain.usecase.reserveresources;

/** Resultado de la confirmación de una reserva: éxito o la causa de la negación. */
public enum ReservationResult {
    /** La reserva se confirmó y quedó registrada. */
    CONFIRMED,
    /** El titular ya llegó al cupo de reservas vigentes (FR-008). */
    QUOTA_EXHAUSTED,
    /** El recurso fue tomado por otra ocupación mientras se confirmaba (FR-004, SC-004). */
    RESOURCE_TAKEN
}
