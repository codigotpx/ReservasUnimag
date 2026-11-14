package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity;

import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
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
 * Una fila de {@code reservation}. La columna {@code occupancy} no se mapea: la calcula PostgreSQL a partir de
 * {@code starts_at} y {@code ends_at}, y solo la usa la restricción de exclusión. Los cambios de estado se hacen con
 * {@code UPDATE} de JPQL, así que la entidad solo se inserta; {@code version} lo sube cada uno de esos {@code UPDATE}.
 */
@Entity
@Table(name = "reservation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationJpaEntity extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "resource_id", nullable = false)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_category", nullable = false)
    private ResourceCategory resourceCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "use_started_at")
    private Instant useStartedAt;

    /** La columna se llama {@code cancellation_reason}, pero guarda el origen de la cancelación. */
    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason")
    private CancellationOrigin cancellationOrigin;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(insertable = false, updatable = false)
    private long version;

    public ReservationJpaEntity(UUID id, UUID userId, String resourceId, ResourceCategory resourceCategory,
                                ReservationOrigin origin, ReservationStatus status, Instant startsAt, Instant endsAt,
                                Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.resourceId = resourceId;
        this.resourceCategory = resourceCategory;
        this.origin = origin;
        this.status = status;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.createdAt = createdAt;
    }
}
