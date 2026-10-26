package edu.unimagdalena.reservasunimag.domain.model.compliance;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;

import java.time.Duration;
import java.time.Instant;

/**
 * Las reservas de espacio terminadas sin revisión, de la más antigua a la más reciente (FR-014). Crece con cada
 * reserva que termina, así que siempre se pagina; el resumen separa las estudiantiles de las académicas.
 */
public record PendingReviews(Page<PendingCheckOut> page, long student, long academic, Instant now) {

    public long finishedDaysAgo(PendingCheckOut pending) {
        return Duration.between(pending.reservation().endsAt(), now).toDays();
    }
}
