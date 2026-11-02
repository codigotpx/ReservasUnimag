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

/** Una denegación de UC2 (FR-005). Solo el id del titular, no sus datos. */
@Entity
@Table(name = "denial")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DenialJpaEntity extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "resource_id", nullable = false)
    private String resourceId;

    /** El código del diccionario tal como se muestra, "RES-004", no el nombre de la constante. */
    @Column(nullable = false)
    private String code;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
