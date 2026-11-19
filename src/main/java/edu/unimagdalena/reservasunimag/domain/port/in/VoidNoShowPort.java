package edu.unimagdalena.reservasunimag.domain.port.in;

import java.util.UUID;

/**
 * UC9 FR-012: el Módulo 3 anula un reporte que envió por error. Se deshace la constancia, no el reparto del recurso:
 * puede que otra persona ya lo haya tomado.
 */
public interface VoidNoShowPort {

    /** {@code true} si había una ausencia vigente y quedó anulada; {@code false} si no había nada que anular. */
    boolean voidReport(UUID reservationId, String reason);
}
