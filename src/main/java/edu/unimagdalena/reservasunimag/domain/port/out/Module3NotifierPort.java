package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecord;

/**
 * Avisos al Módulo 3. Entregar no es publicar: la implementación deja el evento en la outbox, dentro de la
 * transacción en curso, y otro proceso lo lleva al broker después del commit.
 */
public interface Module3NotifierPort {

    void reservationCreated(ReservationRecord record);
}
