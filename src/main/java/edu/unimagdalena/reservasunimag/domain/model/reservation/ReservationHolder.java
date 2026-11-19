package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.model.user.User;

import java.util.UUID;

/** Quién tiene la reserva, tal como se le informa al Módulo 3. */
public record ReservationHolder(UUID userId, String code, String name, Role role) {

    public static ReservationHolder of(User user) {
        return new ReservationHolder(user.id(), user.code(), user.name(), user.role());
    }
}
