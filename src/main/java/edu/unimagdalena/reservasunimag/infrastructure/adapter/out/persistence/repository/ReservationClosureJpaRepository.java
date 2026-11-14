package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.ReservationClosureJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ReservationClosureJpaRepository extends JpaRepository<ReservationClosureJpaEntity, UUID> {

    /** Borra el cierre solo si es de ese tipo: anular una ausencia no reabre una reserva cancelada. */
    @Modifying
    @Query("""
            DELETE FROM ReservationClosureJpaEntity c
            WHERE c.reservationId = :id AND c.closureType = :type""")
    void deleteOfType(@Param("id") UUID reservationId, @Param("type") ReservationClosureJpaEntity.Type type);
}
