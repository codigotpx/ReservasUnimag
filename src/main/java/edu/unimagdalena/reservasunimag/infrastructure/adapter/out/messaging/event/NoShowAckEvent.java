package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowAck;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

import java.util.UUID;

/**
 * {@code NoShowReportAcknowledged} tal como viaja al Módulo 3 (UC9 Contratos §2). Nunca lleva un {@code null}: un
 * acuse aceptado trae la ausencia, la persona y el tiempo apartado; uno rechazado, el motivo y lo que lo explica.
 * {@code duplicate} solo aparece en un reporte repetido.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NoShowAckEvent(String eventId, String type, int version, String occurredAt, Data data) {

    public static final String TYPE = "NoShowReportAcknowledged";

    public static NoShowAckEvent from(UUID eventId, NoShowAck ack) {
        var reservation = ack.reservation();
        var absence = ack.absence();
        boolean accepted = ack.accepted();
        return new NoShowAckEvent(eventId.toString(), TYPE, 1, IsoTimes.offset(ack.occurredAt()),
                new Data(
                        reservation.id().toString(),
                        ack.sourceEventId().toString(),
                        accepted,
                        ack.duplicate() ? Boolean.TRUE : null,
                        accepted ? IsoTimes.offset(absence.reportedAt()) : null,
                        accepted ? absence.resourceReleased() : null,
                        accepted && ack.holderCode() != null ? new HolderBody(ack.holderCode()) : null,
                        accepted ? new TimeBody(IsoTimes.offset(reservation.startsAt()),
                                IsoTimes.offset(reservation.endsAt())) : null,
                        accepted ? null : ack.rejection().name(),
                        ack.acceptedFrom() == null ? null : IsoTimes.offset(ack.acceptedFrom()),
                        ack.reservationStatus() == null ? null : ack.reservationStatus().name(),
                        ack.detail()));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Data(String reservationId, String sourceEventId, boolean accepted, Boolean duplicate,
                       String absenceRegisteredAt, Boolean resourceReleased, HolderBody holder,
                       TimeBody reservedTime, String rejection, String acceptedFrom, String reservationStatus,
                       String detail) {
    }

    public record HolderBody(String code) {
    }

    public record TimeBody(String start, String end) {
    }
}
