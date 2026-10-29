package edu.unimagdalena.reservasunimag.domain.model.academicblock;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.upload.AffectedReservation;
import edu.unimagdalena.reservasunimag.domain.model.upload.RowVerdict;

import java.time.Instant;
import java.util.List;

/**
 * Lo que pasaría, o lo que pasó, con una necesidad extraordinaria. {@code block} es la reserva académica creada y
 * existe solo si {@code applied}; {@code resourceName} es {@code null} si el Módulo 1 no lo dio.
 */
public record ExtraordinaryNeedResult(boolean applied, RowVerdict verdict, String resourceId, String resourceName,
                                      Instant start, Instant end, AcademicData academic,
                                      List<AffectedReservation> affected, Reservation block) {

    public ExtraordinaryNeedResult {
        affected = List.copyOf(affected);
    }
}
