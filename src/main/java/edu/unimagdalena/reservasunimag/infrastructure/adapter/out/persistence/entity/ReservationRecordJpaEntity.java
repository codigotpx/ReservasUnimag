package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity;

import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
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
 * Una ficha reportada (UC10 FR-006). JPA solo la inserta: las columnas de la entrega las pone la base al insertar y
 * después las actualiza {@code OutboxPublisher} con SQL, así que la entidad no las escribe nunca.
 */
@Entity
@Table(name = "reservation_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationRecordJpaEntity extends AssignedIdEntity {

    @Id
    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationOrigin origin;

    @Column(nullable = false)
    private boolean sanctionable;

    @Column(name = "holder_user_id")
    private UUID holderUserId;

    @Column(name = "resource_id", nullable = false)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_category", nullable = false)
    private ResourceCategory resourceCategory;

    @Column(name = "occupancy_starts_at", nullable = false)
    private Instant occupancyStartsAt;

    @Column(name = "occupancy_ends_at", nullable = false)
    private Instant occupancyEndsAt;

    @Column(name = "loan_term_business_days")
    private Integer loanTermBusinessDays;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

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

    public ReservationRecordJpaEntity(UUID reservationId, UUID eventId, ReservationOrigin origin, boolean sanctionable,
                                      UUID holderUserId, String resourceId, ResourceCategory resourceCategory,
                                      Instant occupancyStartsAt, Instant occupancyEndsAt,
                                      Integer loanTermBusinessDays, Instant sentAt) {
        this.reservationId = reservationId;
        this.eventId = eventId;
        this.origin = origin;
        this.sanctionable = sanctionable;
        this.holderUserId = holderUserId;
        this.resourceId = resourceId;
        this.resourceCategory = resourceCategory;
        this.occupancyStartsAt = occupancyStartsAt;
        this.occupancyEndsAt = occupancyEndsAt;
        this.loanTermBusinessDays = loanTermBusinessDays;
        this.sentAt = sentAt;
    }

    @Override
    public UUID getId() {
        return reservationId;
    }
}
