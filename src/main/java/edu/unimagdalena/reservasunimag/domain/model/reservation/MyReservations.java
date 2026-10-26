package edu.unimagdalena.reservasunimag.domain.model.reservation;

import java.util.List;

/**
 * Las reservas de una persona. {@code catalogUnavailable} indica que el Módulo 1 no respondió, así que los
 * {@code name} vienen {@code null}: la reserva y el cupo son datos nuestros y se entregan igual.
 */
public record MyReservations(List<Item> reservations, Quota quota, boolean catalogUnavailable) {

    public MyReservations {
        reservations = List.copyOf(reservations);
    }

    /** {@code cancellable} es si el titular todavía puede cancelarla (UC4 FR-007, FR-008). */
    public record Item(Reservation reservation, String name, boolean overdue, boolean cancellable) {
    }
}
