package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecord;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

import java.util.UUID;

/**
 * {@code ReservationRecordCreated} tal como viaja al Módulo 3 (UC2 Contratos §6): el envoltorio común y, en
 * {@code data}, la ficha. Nunca lleva un {@code null}; {@code holder} y {@code academic} son excluyentes y
 * {@code loan} existe solo en un activo.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReservationRecordEvent(String eventId, String type, int version, String occurredAt, Data data) {

    public static final String TYPE = "ReservationRecordCreated";

    public static ReservationRecordEvent from(UUID eventId, ReservationRecord record) {
        var reservation = record.reservation();
        var resource = record.resource();
        boolean student = reservation.origin() == ReservationOrigin.ESTUDIANTIL;
        var holder = record.holder();
        return new ReservationRecordEvent(eventId.toString(), TYPE, 1, IsoTimes.offset(record.occurredAt()),
                new Data(
                        reservation.id().toString(),
                        reservation.origin().name(),
                        student,
                        new ResourceBody(resource.id(), resource.name(), resource.category().name(),
                                resource.type(), resource.location()),
                        student ? new HolderBody(holder.userId().toString(), holder.code(), holder.name(),
                                holder.role().name()) : null,
                        new OccupancyBody(IsoTimes.offset(reservation.startsAt()),
                                IsoTimes.offset(reservation.endsAt())),
                        reservation.category() == ResourceCategory.ACTIVO
                                ? new LoanBody(IsoTimes.offset(reservation.startsAt()),
                                IsoTimes.offset(reservation.endsAt()), reservation.loan().termBusinessDays())
                                : null));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Data(String reservationId, String origin, boolean sanctionable, ResourceBody resource,
                       HolderBody holder, OccupancyBody occupancy, LoanBody loan) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResourceBody(String id, String name, String category, String type, String location) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HolderBody(String userId, String code, String name, String role) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OccupancyBody(String start, String end) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoanBody(String pickup, String dueAt, int termBusinessDays) {
    }
}
