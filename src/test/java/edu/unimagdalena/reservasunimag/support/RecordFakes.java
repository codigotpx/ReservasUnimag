package edu.unimagdalena.reservasunimag.support;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordFilter;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordPage;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordReport;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRecordRepositoryPort;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** El historial de UC10 en memoria, con la misma semántica que su adaptador de persistencia. */
public final class RecordFakes {

    private RecordFakes() {
    }

    /** Como la clave primaria de {@code reservation_record}: una sola ficha por reserva. */
    public static final class InMemoryReservationRecords implements ReservationRecordRepositoryPort {

        public final Map<UUID, ReservationRecordReport> stored = new LinkedHashMap<>();

        @Override
        public void insert(ReservationRecordReport report) {
            if (stored.putIfAbsent(report.reservationId(), report) != null) {
                throw new IllegalStateException("la reserva " + report.reservationId() + " ya tiene ficha");
            }
        }

        @Override
        public Optional<ReservationRecordReport> findByReservation(UUID reservationId) {
            // Como el adaptador: el nombre del recurso no se guarda.
            return Optional.ofNullable(stored.get(reservationId)).map(report -> report.withResourceName(null));
        }

        @Override
        public ReservationRecordPage search(ReservationRecordFilter filter, PageRequest page) {
            List<ReservationRecordReport> all = stored.values().stream()
                    .filter(r -> filter.origin() == null || r.origin() == filter.origin())
                    .filter(r -> filter.delivery() == null || r.delivery().status() == filter.delivery())
                    .sorted(Comparator.comparing(ReservationRecordReport::sentAt).reversed())
                    .map(r -> r.withResourceName(null))
                    .toList();
            return new ReservationRecordPage(Page.of(all, page), new ReservationRecordPage.Summary(all.size(),
                    count(all, DeliveryStatus.SENT), count(all, DeliveryStatus.PENDING),
                    count(all, DeliveryStatus.FAILED),
                    all.stream().filter(ReservationRecordReport::sanctionable).count()));
        }

        private static long count(List<ReservationRecordReport> reports, DeliveryStatus status) {
            return reports.stream().filter(r -> r.delivery().status() == status).count();
        }
    }
}
