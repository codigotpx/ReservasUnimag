package edu.unimagdalena.reservasunimag.support;

import edu.unimagdalena.reservasunimag.domain.model.compliance.Absence;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationAlreadyClosedException;
import edu.unimagdalena.reservasunimag.domain.port.out.AbsenceRepositoryPort;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Ausencias en memoria con su cierre: una reserva ya cerrada —por ausencia o por lo que diga {@link #closed}— choca. */
public final class InMemoryAbsences implements AbsenceRepositoryPort {

    public final Map<UUID, Absence> stored = new LinkedHashMap<>();
    /** Las reservas con cierre registrado, de cualquier tipo. */
    public final Set<UUID> closed = new HashSet<>();

    @Override
    public void insert(Absence absence) {
        if (!closed.add(absence.reservationId()) || stored.containsKey(absence.reservationId())) {
            throw new ReservationAlreadyClosedException(absence.reservationId(), null);
        }
        stored.put(absence.reservationId(), absence);
    }

    @Override
    public Optional<Absence> findByReservation(UUID reservationId) {
        return Optional.ofNullable(stored.get(reservationId));
    }

    @Override
    public Map<UUID, Absence> findActiveByReservations(Collection<UUID> reservationIds) {
        return stored.values().stream()
                .filter(absence -> reservationIds.contains(absence.reservationId()) && !absence.voided())
                .collect(Collectors.toMap(Absence::reservationId, absence -> absence));
    }

    @Override
    public void markVoided(UUID reservationId, Instant voidedAt, String reason) {
        Absence a = stored.get(reservationId);
        stored.put(reservationId, new Absence(a.reservationId(), a.sourceEventId(), a.verifiedAt(), a.reportedAt(),
                a.resourceReleased(), true, voidedAt, reason));
        closed.remove(reservationId);
    }
}
