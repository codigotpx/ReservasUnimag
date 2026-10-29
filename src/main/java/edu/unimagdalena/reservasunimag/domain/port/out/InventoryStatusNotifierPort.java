package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.reservation.UseStart;

import java.util.UUID;

/**
 * El único aviso que el Módulo 2 le manda al Módulo 1: que un uso empezó (UC7 FR-004). No hay un método que reciba un
 * estado, para que FR-012 —no mandarle ningún otro— no dependa de que nadie se equivoque. Como el aviso a Módulo 3,
 * entregar es dejarlo en la outbox dentro de la transacción en curso.
 */
public interface InventoryStatusNotifierPort {

    /** Devuelve el id del mensaje en la outbox, que es también el {@code eventId}. */
    UUID useStarted(UseStart useStart);
}
