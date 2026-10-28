package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.usecase.reserveresources.ReservationRequest;

/** Caso de uso UC2: apartar un recurso sin pedírselo a nadie (FR-002). */
public interface ReserveResourcesPort {

    /** Confirma la reserva o lanza la negación con su código del diccionario (FR-001 a FR-005). */
    Reservation reserve(ReservationRequest request);
}
