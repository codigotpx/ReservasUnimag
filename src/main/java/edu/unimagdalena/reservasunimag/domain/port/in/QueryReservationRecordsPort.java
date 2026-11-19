package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordFilter;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordPage;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordReport;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;

import java.util.UUID;

/** UC10 FR-006: qué ficha se le reportó al Módulo 3 de cada reserva, y cómo acabó cada envío. */
public interface QueryReservationRecordsPort {

    /**
     * La ficha de una reserva, para su titular o para Dirección de Programa.
     *
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException si no existe, no tiene ficha o es de otra persona
     */
    ReservationRecordReport find(CurrentUser user, UUID reservationId);

    /** El listado para auditar, de la más reciente a la más antigua. */
    ReservationRecordPage list(ReservationRecordFilter filter, PageRequest page);
}
