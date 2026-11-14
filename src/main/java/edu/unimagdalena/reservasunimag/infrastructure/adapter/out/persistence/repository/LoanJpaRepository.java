package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.LoanJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/** Los préstamos se leen junto con su reserva ({@link ReservationJpaRepository}); aquí solo se escriben. */
public interface LoanJpaRepository extends JpaRepository<LoanJpaEntity, UUID> {

    @Modifying
    @Query("""
            UPDATE LoanJpaEntity p SET p.returnedAt = :at
            WHERE p.reservationId = :id AND p.returnedAt IS NULL""")
    int markReturned(@Param("id") UUID reservationId, @Param("at") Instant at);

    @Modifying
    @Query("""
            UPDATE LoanJpaEntity p SET p.pickedUpAt = :at
            WHERE p.reservationId = :id AND p.pickedUpAt IS NULL""")
    int markPickedUp(@Param("id") UUID reservationId, @Param("at") Instant at);
}
