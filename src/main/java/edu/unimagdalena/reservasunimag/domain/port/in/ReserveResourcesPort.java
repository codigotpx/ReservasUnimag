package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.reservation.ExpectedDueDate;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationResult;

import java.time.LocalDate;
import java.time.LocalTime;

/** UC2: apartar un recurso, o decir con un código del diccionario por qué no se puede. */
public interface ReserveResourcesPort {

    /**
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.ReservationDeniedException si una regla de negocio dice que no (RES-001 a RES-004)
     */
    ReservationResult reserve(ReservationRequest request);

    /** El vencimiento que tendría un préstamo recogido a esa hora; no reserva nada ni consume cupo. */
    ExpectedDueDate expectedDueDate(String resourceId, LocalDate date, LocalTime pickup);
}
