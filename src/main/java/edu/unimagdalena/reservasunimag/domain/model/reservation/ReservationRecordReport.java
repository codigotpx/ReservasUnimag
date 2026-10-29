package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * La constancia de la ficha que se le reportó al Módulo 3 y de cómo acabó cada envío (UC10 FR-006). Es permanente:
 * {@code outbox_message} se purga y esto no. {@code holder} es {@code null} en una ficha académica y {@code academic}
 * en una estudiantil; {@code loanTermBusinessDays}, fuera del préstamo de un activo; y {@code resourceName}, cuando
 * el Módulo 1 no respondió: el nombre no es nuestro y no se guarda (UC1 FR-006).
 */
public record ReservationRecordReport(
        UUID reservationId,
        UUID eventId,
        ReservationOrigin origin,
        String resourceId,
        String resourceName,
        ResourceCategory category,
        ReservationHolder holder,
        AcademicData academic,
        Instant startsAt,
        Instant endsAt,
        Integer loanTermBusinessDays,
        Instant sentAt,
        Delivery delivery) {

    public ReservationRecordReport {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        Objects.requireNonNull(sentAt, "sentAt");
        Objects.requireNonNull(delivery, "delivery");
        if ((origin == ReservationOrigin.ACADEMICO) == (holder != null)) {
            throw new IllegalArgumentException("solo una ficha académica queda sin titular");
        }
    }

    /** La constancia de una ficha recién armada, todavía sin intentar entregarla. */
    public static ReservationRecordReport pending(ReservationRecord record) {
        Reservation reservation = record.reservation();
        return new ReservationRecordReport(reservation.id(), record.eventId(), reservation.origin(),
                reservation.resourceId(), record.resource().name(), reservation.category(), record.holder(),
                record.academic(), reservation.startsAt(), reservation.endsAt(),
                reservation.loan() == null ? null : reservation.loan().termBusinessDays(), record.occurredAt(),
                Delivery.PENDING);
    }

    /** Lo mismo que lleva el evento: una ficha académica nunca es sancionable (FR-008, SC-006). */
    public boolean sanctionable() {
        return origin == ReservationOrigin.ESTUDIANTIL;
    }

    public ReservationRecordReport withResourceName(String name) {
        return new ReservationRecordReport(reservationId, eventId, origin, resourceId, name, category, holder,
                academic, startsAt, endsAt, loanTermBusinessDays, sentAt, delivery);
    }
}
