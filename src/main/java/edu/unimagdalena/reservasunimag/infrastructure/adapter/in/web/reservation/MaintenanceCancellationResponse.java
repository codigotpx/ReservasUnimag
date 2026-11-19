package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.reservation.MaintenanceCancellation;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

import java.util.List;

/** Las dos respuestas de UC4 Contratos §2: qué se cancelaría, y qué se canceló. */
final class MaintenanceCancellationResponse {

    private MaintenanceCancellationResponse() {
    }

    static Object from(MaintenanceCancellation result) {
        return result.applied() ? Applied.from(result) : Preview.from(result);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Preview(boolean applied, String resourceId, String resourceName, List<ToCancel> reservationsToCancel,
                   List<SkippedBody> skipped) {

        static Preview from(MaintenanceCancellation result) {
            return new Preview(false, result.resourceId(), result.resourceName(),
                    result.affected().stream().map(ToCancel::from).toList(),
                    result.skipped().stream().map(SkippedBody::from).toList());
        }
    }

    record Applied(boolean applied, String resourceId, int reservationsCancelled, int skipped, int eventsQueued,
                   String appliedAt) {

        static Applied from(MaintenanceCancellation result) {
            int cancelled = result.affected().size();
            // Cada cancelación deja exactamente un evento en la outbox, en la misma transacción.
            return new Applied(true, result.resourceId(), cancelled, result.skipped().size(), cancelled,
                    IsoTimes.offset(result.appliedAt()));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ToCancel(String reservationId, HolderBody holder, String start, String end, String category) {

        static ToCancel from(MaintenanceCancellation.Affected affected) {
            var reservation = affected.reservation();
            var holder = affected.holder();
            return new ToCancel(reservation.id().toString(),
                    holder == null ? null : new HolderBody(holder.code(), holder.name()),
                    IsoTimes.offset(reservation.startsAt()), IsoTimes.offset(reservation.endsAt()),
                    reservation.category().name());
        }
    }

    record HolderBody(String code, String name) {
    }

    record SkippedBody(String reservationId, String reason) {

        static SkippedBody from(MaintenanceCancellation.Skipped skipped) {
            return new SkippedBody(skipped.reservation().id().toString(), skipped.reason());
        }
    }
}
