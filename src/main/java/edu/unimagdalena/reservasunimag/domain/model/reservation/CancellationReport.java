package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * La constancia de lo que se le reportó al Módulo 3 sobre una cancelación y de cómo acabó cada envío (UC11 FR-010).
 * {@code eventId} es el del evento en la outbox. {@code holder} es {@code null} en un bloqueo académico, y
 * {@code resourceName} cuando el Módulo 1 no respondió: el nombre no es nuestro y no se guarda (UC1 FR-006).
 */
public record CancellationReport(
        UUID reservationId,
        UUID eventId,
        CancellationOrigin origin,
        String reason,
        Integer noticeMinutes,
        Instant cancelledAt,
        String resourceId,
        String resourceName,
        ResourceCategory category,
        ReservationHolder holder,
        Instant releasedStart,
        Instant releasedEnd,
        Delivery delivery) {

    public static final String HOLDER_REASON = "Cancelada por el titular.";

    public CancellationReport {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(cancelledAt, "cancelledAt");
        Objects.requireNonNull(delivery, "delivery");
        if ((origin == CancellationOrigin.HOLDER) != (noticeMinutes != null)) {
            throw new IllegalArgumentException("la antelación va si y solo si cancela el titular");
        }
    }

    /** El reporte recién armado, todavía sin intentar entregarlo. */
    public static CancellationReport pending(UUID eventId, Reservation cancelled, Cancellation cancellation,
                                             String resourceName, ReservationHolder holder) {
        return new CancellationReport(cancelled.id(), eventId, cancellation.origin(), cancellation.reason(),
                cancellation.noticeMinutes(), cancellation.cancelledAt(), cancelled.resourceId(), resourceName,
                cancelled.category(), holder, cancelled.startsAt(), cancelled.endsAt(), Delivery.PENDING);
    }

    public ReservationStatus status() {
        return origin.status();
    }

    /** De esto depende que el Módulo 3 no sancione a nadie por una cancelación que no pidió (UC11 FR-006). */
    public boolean attributableToPerson() {
        return origin.attributableToPerson();
    }

    public CancellationReport withResourceName(String name) {
        return new CancellationReport(reservationId, eventId, origin, reason, noticeMinutes, cancelledAt, resourceId,
                name, category, holder, releasedStart, releasedEnd, delivery);
    }

    /**
     * El resultado de los envíos. {@code nextAttemptAt} solo existe mientras está {@code PENDING} y quede un intento
     * programado; {@code lastError} es {@code null} si el último intento no falló.
     */
    public record Delivery(DeliveryStatus status, int attempts, Instant firstAttemptAt, Instant deliveredAt,
                           Instant nextAttemptAt, String lastError) {

        public static final Delivery PENDING = new Delivery(DeliveryStatus.PENDING, 0, null, null, null, null);

        public Delivery {
            Objects.requireNonNull(status, "status");
        }
    }
}
