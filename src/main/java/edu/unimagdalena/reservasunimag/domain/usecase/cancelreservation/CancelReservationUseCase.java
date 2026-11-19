package edu.unimagdalena.reservasunimag.domain.usecase.cancelreservation;

import edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Cancellation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationPolicy;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationResult;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Quota;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.port.in.CancelReservationPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * UC4. Las tres ramas entran por aquí con un {@link CancellationOrigin} distinto y comparten todo lo de después; lo
 * único propio del titular es comprobar que la reserva es suya y que sigue en plazo. La transacción es corta y no
 * llama a nadie por HTTP: bloquea la fila, sale sin tocar nada si ya estaba cancelada, comprueba y escribe.
 */
public class CancelReservationUseCase implements CancelReservationPort {

    private final ReservationRepositoryPort reservations;
    private final ReservationCanceller canceller;
    private final CancellationPolicy policy;
    private final TransactionPort transactions;
    private final Clock clock;
    private final int maxQuota;

    public CancelReservationUseCase(ReservationRepositoryPort reservations, ReservationCanceller canceller,
                                    CancellationPolicy policy, TransactionPort transactions, Clock clock,
                                    int maxQuota) {
        this.reservations = reservations;
        this.canceller = canceller;
        this.policy = policy;
        this.transactions = transactions;
        this.clock = clock;
        this.maxQuota = maxQuota;
    }

    @Override
    public CancellationResult cancelByHolder(CurrentUser user, UUID reservationId) {
        // La de otra persona responde igual que la que no existe: no se confirma que el identificador exista.
        Reservation seen = reservations.findById(reservationId)
                .filter(reservation -> user.id().equals(reservation.userId()))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        String resourceName = seen.status() == ReservationStatus.CONFIRMADA
                ? canceller.resourceName(seen.resourceId())
                : null;

        CancellationResult result = transactions.required(() -> {
            Reservation locked = lock(reservationId);
            if (locked.status().isCancellation()) {
                return alreadyCancelled(locked);
            }
            Instant now = clock.now();
            policy.requireCancellableByHolder(locked, now);
            Cancellation cancellation = new Cancellation(CancellationOrigin.HOLDER, CancellationReport.HOLDER_REASON,
                    policy.noticeMinutes(locked, now), now);
            return new CancellationResult(canceller.cancel(locked, cancellation, resourceName), cancellation, null,
                    false);
        });
        return result.withQuota(quotaOf(user.id()));
    }

    @Override
    public CancellationResult cancelBySystem(UUID reservationId, CancellationOrigin origin, String reason) {
        if (origin == CancellationOrigin.HOLDER) {
            throw new IllegalArgumentException("la cancelación del titular entra por cancelByHolder");
        }
        Objects.requireNonNull(reason, "reason");
        Reservation seen = reservations.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        String resourceName = seen.status() == ReservationStatus.CONFIRMADA
                ? canceller.resourceName(seen.resourceId())
                : null;

        CancellationResult result = transactions.required(() -> {
            Reservation locked = lock(reservationId);
            if (locked.status().isCancellation()) {
                return alreadyCancelled(locked);
            }
            policy.requireOpen(locked);
            Cancellation cancellation = new Cancellation(origin, reason, null, clock.now());
            return new CancellationResult(canceller.cancel(locked, cancellation, resourceName), cancellation, null,
                    false);
        });
        return seen.userId() == null ? result : result.withQuota(quotaOf(seen.userId()));
    }

    private Reservation lock(UUID reservationId) {
        return reservations.lockById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }

    /** La segunda petición ve lo que dejó la primera y no reescribe nada ni encola otro evento (UC11 SC-003). */
    private CancellationResult alreadyCancelled(Reservation locked) {
        Cancellation original = reservations.findCancellation(locked.id())
                .orElseThrow(() -> new IllegalStateException("la reserva " + locked.id() + " está "
                        + locked.status() + " sin marca de cancelación"));
        return new CancellationResult(locked, original, null, true);
    }

    private Quota quotaOf(UUID userId) {
        return new Quota(reservations.quotaOf(userId, clock.now()).used(), maxQuota);
    }
}
