package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity;

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
 * El cierre de una reserva: su clave primaria es lo que impide que quede a la vez ausente y cancelada (UC11 FR-011).
 * Solo se inserta y se borra. Los cierres por cancelación todavía los escribe
 * {@code CancellationReportPersistenceAdapter} con SQL.
 */
@Entity
@Table(name = "reservation_closure")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationClosureJpaEntity extends AssignedIdEntity {

    public enum Type {
        CANCELLATION,
        ABSENCE
    }

    @Id
    @Column(name = "reservation_id")
    private UUID reservationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "closure_type", nullable = false)
    private Type closureType;

    /** Lo pone la base al insertar. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public ReservationClosureJpaEntity(UUID reservationId, Type closureType) {
        this.reservationId = reservationId;
        this.closureType = closureType;
    }

    @Override
    public UUID getId() {
        return reservationId;
    }
}
