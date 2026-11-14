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
 * El préstamo de un activo, con la misma clave que su reserva. Las consultas lo unen a la reserva con un
 * {@code LEFT JOIN ... ON}: sin asociación mapeada, una lectura nunca carga más de lo que pide.
 */
@Entity
@Table(name = "loan")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class LoanJpaEntity extends AssignedIdEntity {

    @Id
    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "term_business_days", nullable = false)
    private int termBusinessDays;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Override
    public UUID getId() {
        return reservationId;
    }
}
