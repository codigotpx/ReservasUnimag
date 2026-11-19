package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutAck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutKind;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * {@code CheckOutAcknowledged} tal como viaja al Módulo 3 (UC12 Contratos §2). Nunca lleva un {@code null}: un activo
 * aceptado trae el vencimiento y si se cerró el préstamo; un espacio aceptado, el dictamen y {@code slotReleased},
 * siempre {@code false}; uno rechazado, el motivo. {@code occurredAt} de {@code data} es cuándo pasó, con la precisión
 * que llegó.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckOutAckEvent(String eventId, String type, int version, String occurredAt, Data data) {

    public static final String TYPE = "CheckOutAcknowledged";

    public static CheckOutAckEvent from(UUID eventId, CheckOutAck ack) {
        var checkOut = ack.checkOut();
        boolean accepted = ack.accepted();
        boolean asset = accepted && checkOut.kind() == CheckOutKind.ASSET_RETURN;
        boolean space = accepted && checkOut.kind() == CheckOutKind.SPACE_REVIEW;
        boolean fresh = accepted && !ack.duplicate();
        return new CheckOutAckEvent(eventId.toString(), TYPE, 1, IsoTimes.offset(ack.occurredAt()),
                new Data(
                        ack.reservation().id().toString(),
                        ack.sourceEventId().toString(),
                        accepted ? checkOut.kind().name() : null,
                        accepted,
                        ack.duplicate() ? Boolean.TRUE : null,
                        accepted ? exact(checkOut.occurredAt()) : null,
                        accepted ? IsoTimes.offset(checkOut.receivedAt()) : null,
                        asset ? IsoTimes.offset(checkOut.dueAtSnapshot()) : null,
                        asset ? fresh : null,
                        asset ? fresh : null,
                        space ? checkOut.verdict().name() : null,
                        space ? fresh : null,
                        space ? Boolean.FALSE : null,
                        accepted ? null : ack.rejection().name(),
                        ack.detail(),
                        ack.acceptedFrom() == null ? null : IsoTimes.offset(ack.acceptedFrom())));
    }

    /** Sin redondear: una devolución a las 22:00:00 y otra a las 22:00:01 se distinguen (edge case de las 22:00). */
    private static String exact(Instant instant) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atZone(TimeSlot.ZONE));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Data(String reservationId, String sourceEventId, String kind, boolean accepted, Boolean duplicate,
                       String occurredAt, String receivedAt, String dueAt, Boolean loanClosed, Boolean quotaReleased,
                       String verdict, Boolean verdictRecorded, Boolean slotReleased, String rejection, String detail,
                       String acceptedFrom) {
    }
}
