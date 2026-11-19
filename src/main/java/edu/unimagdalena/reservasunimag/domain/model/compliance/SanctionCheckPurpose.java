package edu.unimagdalena.reservasunimag.domain.model.compliance;

/** Para qué se le preguntó al Módulo 3. Son los dos únicos que pueden hacerlo (UC6 FR-002). */
public enum SanctionCheckPurpose {
    /** UC1: avisar al listar recursos. Si el Módulo 3 no responde, la lista se muestra con aviso. */
    SEARCH_RESOURCES,
    /** UC2: decidir al confirmar. Si el Módulo 3 no responde, la reserva no se crea. */
    RESERVE_RESOURCES
}
