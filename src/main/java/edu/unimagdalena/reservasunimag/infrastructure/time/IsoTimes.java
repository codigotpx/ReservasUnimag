package edu.unimagdalena.reservasunimag.infrastructure.time;

import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/** Instantes como los fija el contrato: ISO-8601 con desplazamiento de Bogotá y sin fracciones, nunca sin zona. */
public final class IsoTimes {

    private IsoTimes() {
    }

    public static String offset(Instant instant) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                instant.atZone(TimeSlot.ZONE).toOffsetDateTime().withNano(0));
    }
}
