package edu.unimagdalena.reservasunimag.support;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutAck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowAck;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationAlreadyClosedException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Cancellation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReportFilter;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReportPage;
import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.QuotaUsage;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecord;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.port.out.CancellationReportRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Module3NotifierPort;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Puertos falsos de UC4 y UC11, con la misma semántica que sus adaptadores de persistencia. */
public final class CancellationFakes {

    private CancellationFakes() {
    }

    /** Reservas en memoria. Cuenta el cupo como el adaptador: solo las confirmadas vigentes. */
    public static final class InMemoryReservations implements ReservationRepositoryPort {

        public final Map<UUID, Reservation> stored = new LinkedHashMap<>();
        public final Map<UUID, Cancellation> cancellations = new HashMap<>();
        public int locks;

        public Reservation add(Reservation reservation) {
            stored.put(reservation.id(), reservation);
            return reservation;
        }

        @Override
        public void lockHolder(UUID userId) {
        }

        @Override
        public QuotaUsage quotaOf(UUID userId, Instant now) {
            long used = stored.values().stream()
                    .filter(r -> r.status() == ReservationStatus.CONFIRMADA && userId.equals(r.userId()))
                    .filter(r -> r.category() == ResourceCategory.ESPACIO ? r.endsAt().isAfter(now)
                            : !r.loan().returned())
                    .count();
            return new QuotaUsage((int) used, null, false);
        }

        @Override
        public void insert(Reservation reservation) {
            add(reservation);
        }

        @Override
        public List<Reservation> findByHolder(UUID userId, boolean onlyActive, Instant now) {
            return stored.values().stream().filter(r -> userId.equals(r.userId())).toList();
        }

        @Override
        public Optional<Reservation> findById(UUID id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public Optional<Reservation> lockById(UUID id) {
            locks++;
            return findById(id);
        }

        @Override
        public Optional<Cancellation> findCancellation(UUID id) {
            Cancellation cancellation = cancellations.get(id);
            return cancellation == null ? Optional.empty()
                    : Optional.of(new Cancellation(cancellation.origin(), null, null, cancellation.cancelledAt()));
        }

        @Override
        public void markCancelled(UUID id, Cancellation cancellation) {
            stored.put(id, stored.get(id).cancel(cancellation.origin()));
            cancellations.put(id, cancellation);
        }

        @Override
        public void markFinished(UUID id) {
            Reservation r = stored.get(id);
            if (r.status() != ReservationStatus.CONFIRMADA) {
                throw new IllegalStateException("la reserva " + id + " no estaba confirmada");
            }
            stored.put(id, new Reservation(r.id(), r.userId(), r.resourceId(), r.category(), r.origin(),
                    ReservationStatus.FINALIZADA, r.startsAt(), r.endsAt(), r.createdAt(), r.loan(), r.useStartedAt()));
        }

        @Override
        public boolean markUseStarted(UUID id, Instant at) {
            Reservation r = stored.get(id);
            if (r.useStarted()) {
                return false;
            }
            stored.put(id, r.startUse(at));
            return true;
        }

        @Override
        public List<Reservation> findAcademicBlocksToStart(Instant now) {
            return stored.values().stream()
                    .filter(r -> r.origin() == ReservationOrigin.ACADEMICO)
                    .filter(r -> r.status() == ReservationStatus.CONFIRMADA && !r.useStarted())
                    .filter(r -> !r.startsAt().isAfter(now) && r.endsAt().isAfter(now))
                    .sorted(Comparator.comparing(Reservation::startsAt))
                    .toList();
        }

        @Override
        public void markReturned(UUID id, Instant returnedAt) {
            Reservation r = stored.get(id);
            stored.put(id, new Reservation(r.id(), r.userId(), r.resourceId(), r.category(), r.origin(), r.status(),
                    r.startsAt(), r.endsAt(), r.createdAt(),
                    new Loan(r.loan().termBusinessDays(), r.loan().pickedUpAt(), returnedAt)));
            markFinished(id);
        }

        @Override
        public List<Reservation> findStudentOccupying(java.util.Collection<String> resourceIds, Instant from,
                                                      Instant to) {
            return stored.values().stream()
                    .filter(r -> resourceIds.contains(r.resourceId()))
                    .filter(r -> r.origin() == ReservationOrigin.ESTUDIANTIL && r.status() == ReservationStatus.CONFIRMADA)
                    .filter(r -> r.startsAt().isBefore(to) && (r.inHands() || r.endsAt().isAfter(from)))
                    .sorted(Comparator.comparing(Reservation::startsAt))
                    .toList();
        }

        @Override
        public List<Reservation> findUpcomingByResource(String resourceId, Instant now) {
            return stored.values().stream()
                    .filter(r -> r.resourceId().equals(resourceId))
                    .filter(r -> r.origin() == ReservationOrigin.ESTUDIANTIL)
                    .filter(r -> r.status() == ReservationStatus.CONFIRMADA)
                    .filter(r -> r.startsAt().isAfter(now) || r.inHands())
                    .sorted(Comparator.comparing(Reservation::startsAt))
                    .toList();
        }
    }

    /** El historial con su clave primaria por reserva: un segundo reporte de la misma reserva choca. */
    public static final class InMemoryCancellationReports implements CancellationReportRepositoryPort {

        public final Map<UUID, CancellationReport> stored = new LinkedHashMap<>();

        @Override
        public void insert(CancellationReport report) {
            if (stored.containsKey(report.reservationId())) {
                throw new ReservationAlreadyClosedException(report.reservationId(), null);
            }
            stored.put(report.reservationId(), report);
        }

        @Override
        public Optional<CancellationReport> findByReservation(UUID reservationId) {
            return Optional.ofNullable(stored.get(reservationId));
        }

        @Override
        public CancellationReportPage search(CancellationReportFilter filter, PageRequest page) {
            List<CancellationReport> all = stored.values().stream()
                    .filter(r -> filter.origin() == null || r.origin() == filter.origin())
                    .filter(r -> filter.delivery() == null || r.delivery().status() == filter.delivery())
                    .sorted(Comparator.comparing(CancellationReport::cancelledAt).reversed())
                    .toList();
            return new CancellationReportPage(Page.of(all, page), new CancellationReportPage.Summary(all.size(),
                    count(all, DeliveryStatus.SENT), count(all, DeliveryStatus.PENDING),
                    count(all, DeliveryStatus.FAILED)));
        }

        private static long count(List<CancellationReport> reports, DeliveryStatus status) {
            return reports.stream().filter(r -> r.delivery().status() == status).count();
        }
    }

    /** Lo que se dejaría en la outbox. */
    public static final class RecordingNotifier implements Module3NotifierPort {

        public final List<ReservationRecord> created = new ArrayList<>();
        public final List<CancellationReport> cancelled = new ArrayList<>();
        public final List<NoShowAck> noShowAcks = new ArrayList<>();
        public final List<CheckOutAck> checkOutAcks = new ArrayList<>();

        @Override
        public void reservationCreated(ReservationRecord record) {
            created.add(record);
        }

        @Override
        public void reservationCancelled(CancellationReport report) {
            cancelled.add(report);
        }

        @Override
        public void noShowAcknowledged(NoShowAck ack) {
            noShowAcks.add(ack);
        }

        @Override
        public void checkOutAcknowledged(CheckOutAck ack) {
            checkOutAcks.add(ack);
        }
    }

    /**
     * Transacción que se deshace como la de verdad: si el bloque lanza, se restauran las reservas, el historial y lo
     * notificado tal como estaban.
     */
    public static final class RollbackTransactions implements TransactionPort {

        private final InMemoryReservations reservations;
        private final InMemoryCancellationReports reports;
        private final RecordingNotifier notifier;
        public int opened;

        public RollbackTransactions(InMemoryReservations reservations, InMemoryCancellationReports reports,
                                    RecordingNotifier notifier) {
            this.reservations = reservations;
            this.reports = reports;
            this.notifier = notifier;
        }

        @Override
        public <T> T required(Supplier<T> work) {
            opened++;
            var stored = new LinkedHashMap<>(reservations.stored);
            var cancellations = new HashMap<>(reservations.cancellations);
            var storedReports = new LinkedHashMap<>(reports.stored);
            var notified = new ArrayList<>(notifier.cancelled);
            try {
                return work.get();
            } catch (RuntimeException failure) {
                reservations.stored.clear();
                reservations.stored.putAll(stored);
                reservations.cancellations.clear();
                reservations.cancellations.putAll(cancellations);
                reports.stored.clear();
                reports.stored.putAll(storedReports);
                notifier.cancelled.clear();
                notifier.cancelled.addAll(notified);
                throw failure;
            }
        }
    }
}
