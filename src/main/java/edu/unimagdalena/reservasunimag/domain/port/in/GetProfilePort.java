package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.User;

import java.util.Optional;

/** Datos de quien tiene la sesión, para {@code GET /api/auth/me}. Vacío si el usuario ya no existe o está inactivo. */
public interface GetProfilePort {

    Optional<User> getProfile(CurrentUser currentUser);
}
