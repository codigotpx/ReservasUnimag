package edu.unimagdalena.reservasunimag.support;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicBlock;
import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Cancellation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationResult;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleUpload;
import edu.unimagdalena.reservasunimag.domain.model.upload.UploadStatus;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.port.in.CancelReservationPort;
import edu.unimagdalena.reservasunimag.domain.port.out.AcademicBlockRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.ScheduleUploadRepositoryPort;
import edu.unimagdalena.reservasunimag.support.CancellationFakes.InMemoryReservations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Puertos falsos de UC3, apoyados en las reservas en memoria para saber qué bloqueo sigue vigente. */
public final class ScheduleFakes {

    private ScheduleFakes() {
    }

    /** Los bloqueos; uno cuya reserva ya no está confirmada deja de contar, como en la base. */
    public static final class InMemoryAcademicBlocks implements AcademicBlockRepositoryPort {

        private final InMemoryReservations reservations;
        public final Map<UUID, AcademicBlock> stored = new LinkedHashMap<>();
        public final Map<UUID, UUID> scheduleOf = new LinkedHashMap<>();

        public InMemoryAcademicBlocks(InMemoryReservations reservations) {
            this.reservations = reservations;
        }

        @Override
        public void insert(UUID reservationId, UUID scheduleId, AcademicBlock block) {
            stored.put(reservationId, block);
            scheduleOf.put(reservationId, scheduleId);
        }

        private boolean confirmed(AcademicBlock block) {
            var reservation = reservations.stored.get(block.reservationId());
            return reservation != null && reservation.status() == ReservationStatus.CONFIRMADA;
        }

        @Override
        public List<AcademicBlock> findConfirmed(Collection<String> resourceIds, Instant from, Instant to) {
            return stored.values().stream()
                    .filter(this::confirmed)
                    .filter(block -> resourceIds.contains(block.resourceId()) && block.overlaps(from, to))
                    .sorted(Comparator.comparing(AcademicBlock::startsAt))
                    .toList();
        }

        @Override
        public List<AcademicBlock> findUpcomingRegular(String academicTerm, Instant now) {
            return stored.values().stream()
                    .filter(this::confirmed)
                    .filter(block -> academicTerm.equals(block.academicTerm()) && block.startsAt().isAfter(now))
                    .toList();
        }
    }

    public static final class InMemoryScheduleUploads implements ScheduleUploadRepositoryPort {

        public final Map<UUID, ScheduleUpload> stored = new LinkedHashMap<>();

        @Override
        public void insert(ScheduleUpload upload) {
            stored.put(upload.id(), upload);
        }

        @Override
        public Optional<ScheduleUpload> findById(UUID id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public Optional<ScheduleUpload> lockById(UUID id) {
            return findById(id);
        }

        @Override
        public Page<ScheduleUpload> list(PageRequest page) {
            return Page.of(List.copyOf(stored.values()), page);
        }

        @Override
        public void markApplied(ScheduleUpload applied) {
            stored.put(applied.id(), applied);
        }

        @Override
        public void markExpired(UUID id, Instant at) {
            ScheduleUpload u = stored.get(id);
            stored.put(id, new ScheduleUpload(u.id(), u.academicTerm(), u.termStart(), u.termEnd(), u.fileName(),
                    u.uploadedBy(), u.uploadedByName(), UploadStatus.EXPIRED, u.validatedAt(), u.expiresAt(),
                    u.appliedAt(), u.summary(), u.impact(), u.result(), u.rows()));
        }
    }

    /** La cancelación del sistema sin sus efectos laterales: cambia el estado y deja constancia de la llamada. */
    public static final class RecordingCancellations implements CancelReservationPort {

        public record Call(UUID reservationId, CancellationOrigin origin, String reason) {
        }

        private final InMemoryReservations reservations;
        private final Clock clock;
        public final List<Call> calls = new ArrayList<>();

        public RecordingCancellations(InMemoryReservations reservations, Clock clock) {
            this.reservations = reservations;
            this.clock = clock;
        }

        @Override
        public CancellationResult cancelByHolder(CurrentUser user, UUID reservationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CancellationResult cancelBySystem(UUID reservationId, CancellationOrigin origin, String reason) {
            calls.add(new Call(reservationId, origin, reason));
            var cancellation = new Cancellation(origin, reason, null, clock.now());
            reservations.markCancelled(reservationId, cancellation);
            return new CancellationResult(reservations.stored.get(reservationId), cancellation, null, false);
        }
    }
}
