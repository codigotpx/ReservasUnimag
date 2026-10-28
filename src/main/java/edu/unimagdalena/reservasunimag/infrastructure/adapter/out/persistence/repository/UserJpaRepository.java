package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByEmail(String email);
}
