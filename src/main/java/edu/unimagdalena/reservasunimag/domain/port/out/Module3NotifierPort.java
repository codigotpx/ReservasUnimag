package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowAck;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecord;

/**
 * Avisos al Módulo 3. Entregar no es publicar: la implementación deja el evento en la outbox, dentro de la
 * transacción en curso, y otro proceso lo lleva al broker después del commit.
 */
public interface Module3NotifierPort {

    void reservationCreated(ReservationRecord record);

    /** El evento lleva {@code report.eventId()} como su {@code eventId}. */
    void reservationCancelled(CancellationReport report);

    /** El acuse de un reporte de no asistencia, aceptado o no (UC9 FR-002, FR-011). */
    void noShowAcknowledged(NoShowAck ack);
}
