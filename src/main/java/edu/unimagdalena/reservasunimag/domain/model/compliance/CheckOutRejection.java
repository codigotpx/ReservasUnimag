package edu.unimagdalena.reservasunimag.domain.model.compliance;

/**
 * Por qué un check-out bien formado no se aplica (UC12 FR-008, FR-013). Es una respuesta de negocio: va en el acuse y
 * nunca al DLT. Un check-out repetido no es un rechazo: se acepta como duplicado.
 */
public enum CheckOutRejection {
    /** La reserva del activo se canceló antes de la entrega. */
    RESERVATION_CANCELLED,
    /** El activo nunca se recogió: no hay devolución posible. */
    ASSET_NOT_PICKED_UP,
    /** La revisión llegó antes de que terminara la franja del espacio. */
    SLOT_NOT_FINISHED,
    /** El espacio no se usó: la reserva se canceló o tiene una ausencia registrada. */
    RESERVATION_NOT_USED,
    /** Una revisión de espacio sin dictamen, o una devolución de activo con dictamen o daños (FR-010, FR-011). */
    VERDICT_REQUIRED
}
