package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity;

import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutKind;
import edu.unimagdalena.reservasunimag.domain.model.compliance.Verdict;
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

/** Un check-out de UC12, con la misma clave que su reserva. Solo se inserta y se consulta. */
@Entity
@Table(name = "check_out")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CheckOutJpaEntity extends AssignedIdEntity {

    @Id
    @Column(name = "reservation_id")
    private UUID reservationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckOutKind kind;

    @Column(name = "source_event_id", nullable = false, unique = true)
    private UUID sourceEventId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    /** Solo en la revisión de un espacio. */
    @Enumerated(EnumType.STRING)
    private Verdict verdict;

    /** Solo en la devolución de un activo. */
    @Column(name = "due_at_snapshot")
    private Instant dueAtSnapshot;

    @Override
    public UUID getId() {
        return reservationId;
    }
}
