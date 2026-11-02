package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity;

import edu.unimagdalena.reservasunimag.domain.model.resource.DisplayedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.NotifiedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Una fila del historial de UC7 (FR-009). {@code notifiedStatus} lo actualiza después el publicador de la outbox con
 * SQL, en su propia transacción: JPA solo inserta y lee.
 */
@Entity
@Table(name = "status_change")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class StatusChangeJpaEntity extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "resource_id", nullable = false)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_category", nullable = false)
    private ResourceCategory resourceCategory;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "occupancy_starts_at", nullable = false)
    private Instant occupancyStartsAt;

    @Column(name = "occupancy_ends_at", nullable = false)
    private Instant occupancyEndsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status_module2", nullable = false)
    private DisplayedStatus previousStatusModule2;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private DisplayedStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusChangeReason reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "notified_status", nullable = false)
    private NotifiedStatus notifiedStatus;

    @Column(name = "outbox_message_id")
    private UUID outboxMessageId;
}
