package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity;

import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Una cancelación reportada (UC11 FR-010). Como en {@link ReservationRecordJpaEntity}, JPA solo la inserta y nunca
 * escribe las columnas de la entrega, que son de {@code OutboxPublisher}.
 */
@Entity
@Table(name = "cancellation_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CancellationReportJpaEntity extends AssignedIdEntity {

    @Id
    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_origin", nullable = false)
    private CancellationOrigin cancellationOrigin;

    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_status", nullable = false)
    private ReservationStatus reservationStatus;

    @Column(nullable = false)
    private String reason;

    @Column(name = "attributable_to_person", nullable = false)
    private boolean attributableToPerson;

    @Column(name = "notice_minutes")
    private Integer noticeMinutes;

    @Column(name = "cancelled_at", nullable = false)
    private Instant cancelledAt;

    @Column(name = "resource_id", nullable = false)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_category", nullable = false)
    private ResourceCategory resourceCategory;

    @Column(name = "holder_user_id")
    private UUID holderUserId;

    @Column(name = "released_starts_at", nullable = false)
    private Instant releasedStartsAt;

    @Column(name = "released_ends_at", nullable = false)
    private Instant releasedEndsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", insertable = false, updatable = false)
    private DeliveryStatus deliveryStatus;

    @Column(insertable = false, updatable = false)
    private int attempts;

    @Column(name = "first_attempt_at", insertable = false, updatable = false)
    private Instant firstAttemptAt;

    @Column(name = "delivered_at", insertable = false, updatable = false)
    private Instant deliveredAt;

    @Column(name = "last_error", insertable = false, updatable = false)
    private String lastError;

    public CancellationReportJpaEntity(UUID reservationId, UUID eventId, CancellationOrigin cancellationOrigin,
                                       ReservationStatus reservationStatus, String reason,
                                       boolean attributableToPerson, Integer noticeMinutes, Instant cancelledAt,
                                       String resourceId, ResourceCategory resourceCategory, UUID holderUserId,
                                       Instant releasedStartsAt, Instant releasedEndsAt) {
        this.reservationId = reservationId;
        this.eventId = eventId;
        this.cancellationOrigin = cancellationOrigin;
        this.reservationStatus = reservationStatus;
        this.reason = reason;
        this.attributableToPerson = attributableToPerson;
        this.noticeMinutes = noticeMinutes;
        this.cancelledAt = cancelledAt;
        this.resourceId = resourceId;
        this.resourceCategory = resourceCategory;
        this.holderUserId = holderUserId;
        this.releasedStartsAt = releasedStartsAt;
        this.releasedEndsAt = releasedEndsAt;
    }

    @Override
    public UUID getId() {
        return reservationId;
    }
}
