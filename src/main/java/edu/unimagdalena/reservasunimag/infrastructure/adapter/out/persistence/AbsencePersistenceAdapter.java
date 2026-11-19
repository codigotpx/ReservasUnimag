package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.compliance.Absence;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationAlreadyClosedException;
import edu.unimagdalena.reservasunimag.domain.port.out.AbsenceRepositoryPort;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.AbsenceJpaEntity;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.ReservationClosureJpaEntity;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.AbsenceJpaRepository;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.ReservationClosureJpaRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Las ausencias de UC9 y su cierre en {@code reservation_closure}, que es lo que impide que una reserva quede a la vez
 * ausente y cancelada (UC11 FR-011), con JPA. Participa en la transacción del caso de uso; no abre ninguna.
 */
@Component
class AbsencePersistenceAdapter implements AbsenceRepositoryPort {

    private static final int MAX_REASON = 500;

    private final AbsenceJpaRepository absences;
    private final ReservationClosureJpaRepository closures;

    AbsencePersistenceAdapter(AbsenceJpaRepository absences, ReservationClosureJpaRepository closures) {
        this.absences = absences;
        this.closures = closures;
    }

    @Override
    public void insert(Absence absence) {
        try {
            // El flush hace que el cierre repetido falle aquí, antes de escribir la ausencia.
            closures.saveAndFlush(new ReservationClosureJpaEntity(absence.reservationId(),
                    ReservationClosureJpaEntity.Type.ABSENCE));
        } catch (DataAccessException failure) {
            if (SqlStates.has(failure, SqlStates.UNIQUE_VIOLATION)) {
                throw new ReservationAlreadyClosedException(absence.reservationId(), failure);
            }
            throw failure;
        }
        absences.saveAndFlush(new AbsenceJpaEntity(absence.reservationId(), absence.sourceEventId(),
                absence.verifiedAt(), absence.reportedAt(), absence.resourceReleased(), absence.voided(),
                absence.voidedAt(), absence.voidedReason()));
    }

    @Override
    public Optional<Absence> findByReservation(UUID reservationId) {
        return absences.findById(reservationId).map(AbsencePersistenceAdapter::toAbsence);
    }

    @Override
    public Map<UUID, Absence> findActiveByReservations(Collection<UUID> reservationIds) {
        if (reservationIds.isEmpty()) {
            return Map.of();
        }
        return absences.findByReservationIdInAndVoidedFalse(reservationIds).stream()
                .map(AbsencePersistenceAdapter::toAbsence)
                .collect(Collectors.toMap(Absence::reservationId, Function.identity()));
    }

    @Override
    public void markVoided(UUID reservationId, Instant voidedAt, String reason) {
        String kept = reason == null || reason.length() <= MAX_REASON ? reason : reason.substring(0, MAX_REASON);
        absences.findById(reservationId).ifPresent(absence -> absence.markVoided(voidedAt, kept));
        closures.deleteOfType(reservationId, ReservationClosureJpaEntity.Type.ABSENCE);
    }

    private static Absence toAbsence(AbsenceJpaEntity entity) {
        return new Absence(entity.getReservationId(), entity.getSourceEventId(), entity.getVerifiedAt(),
                entity.getReportedAt(), entity.isResourceReleased(), entity.isVoided(), entity.getVoidedAt(),
                entity.getVoidedReason());
    }
}
