package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.loan;

import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingCheckOut;

/** La persona de una reserva pendiente: código y nombre, nada más. */
record HolderBody(String code, String name) {

    static HolderBody of(PendingCheckOut pending) {
        return pending.holder() == null ? null : new HolderBody(pending.holder().code(), pending.holder().name());
    }
}
