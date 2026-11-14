package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Una ausencia de UC9. Nadie más la toca con SQL, así que la anulación se hace sobre la entidad cargada
 * ({@link #markVoided}) y la escribe el flush, sin un {@code UPDATE} aparte.
 */
@Entity
@Table(name = "absence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AbsenceJpaEntity extends AssignedIdEntity {

    @Id
    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "source_event_id", nullable = false, unique = true)
    private UUID sourceEventId;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @Column(name = "resource_released", nullable = false)
    private boolean resourceReleased;

    @Column(nullable = false)
    private boolean voided;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_reason")
    private String voidedReason;

    public void markVoided(Instant at, String reason) {
        this.voided = true;
        this.voidedAt = at;
        this.voidedReason = reason;
    }

    @Override
    public UUID getId() {
        return reservationId;
    }
}
