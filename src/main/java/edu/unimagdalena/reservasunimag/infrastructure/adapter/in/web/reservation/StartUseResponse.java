package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.reservation.UseStartResult;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

/**
 * El inicio de uso registrado, como lo fija UC7 Contratos §3. {@code inventoryNotice} dice cómo va el aviso al Módulo 1
 * en este instante —casi siempre {@code PENDING}: sale después del commit— y falta si la válvula del aviso está
 * apagada. Una segunda llamada devuelve lo mismo que la primera.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record StartUseResponse(String reservationId, String resourceId, String useStartedAt, String newStatus,
                        InventoryNoticeBody inventoryNotice) {

    static StartUseResponse from(UseStartResult result) {
        var reservation = result.reservation();
        var change = result.change();
        return new StartUseResponse(reservation.id().toString(), reservation.resourceId(),
                IsoTimes.offset(reservation.useStartedAt() == null ? change.changedAt() : reservation.useStartedAt()),
                change.newStatus().name(),
                change.outboxMessageId() == null ? null
                        : new InventoryNoticeBody(change.notifiedStatus().name(), change.outboxMessageId().toString()));
    }

    record InventoryNoticeBody(String status, String eventId) {
    }
}
