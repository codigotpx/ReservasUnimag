package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.AbsenceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AbsenceJpaRepository extends JpaRepository<AbsenceJpaEntity, UUID> {

    List<AbsenceJpaEntity> findByReservationIdInAndVoidedFalse(Collection<UUID> reservationIds);
}
