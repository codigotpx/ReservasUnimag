package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Lo que pide la persona. La forma depende de la categoría del recurso, que decide el Módulo 1 y no el cliente:
 * un espacio lleva {@code start} y {@code end}; un activo, solo {@code pickup}. Lo que no corresponde a la
 * categoría es {@code null}.
 */
public record ReservationRequest(CurrentUser holder, String resourceId, LocalDate date, LocalTime start,
                                 LocalTime end, LocalTime pickup) {

    public static ReservationRequest forSpace(CurrentUser holder, String resourceId, LocalDate date, LocalTime start,
                                              LocalTime end) {
        return new ReservationRequest(holder, resourceId, date, start, end, null);
    }

    public static ReservationRequest forAsset(CurrentUser holder, String resourceId, LocalDate date,
                                              LocalTime pickup) {
        return new ReservationRequest(holder, resourceId, date, null, null, pickup);
    }
}
