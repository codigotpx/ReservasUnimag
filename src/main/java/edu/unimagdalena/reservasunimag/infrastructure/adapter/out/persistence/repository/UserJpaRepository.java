package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.UserJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByEmail(String email);

    /** Toma la fila de la persona hasta que acabe la transacción; con ella se serializan sus reservas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u.id FROM UserJpaEntity u WHERE u.id = :id")
    Optional<UUID> lockById(@Param("id") UUID id);
}
