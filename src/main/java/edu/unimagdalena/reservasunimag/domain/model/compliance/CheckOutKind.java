package edu.unimagdalena.reservasunimag.domain.model.compliance;

import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;

/**
 * Los dos check-out que llegan por el mismo topic. Lo decide la categoría del recurso de la reserva, no el evento:
 * así el Módulo 3 no puede equivocarse al etiquetarlo (plan de UC12, Decisiones de diseño).
 */
public enum CheckOutKind {
    /** Un activo volvió: cierra el préstamo y libera el periodo y el cupo (FR-001, FR-003). */
    ASSET_RETURN,
    /** Un espacio se revisó después de usarlo: registra el dictamen y no libera nada (FR-011, FR-012). */
    SPACE_REVIEW;

    public static CheckOutKind of(ResourceCategory category) {
        return category == ResourceCategory.ACTIVO ? ASSET_RETURN : SPACE_REVIEW;
    }
}
