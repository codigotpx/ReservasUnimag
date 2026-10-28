package edu.unimagdalena.reservasunimag.domain.model.user;

import java.util.Objects;
import java.util.UUID;

/**
 * Quien hace la petición, ya identificado por el adaptador web. Los casos de uso lo reciben así y
 * nunca lo arman con datos del cuerpo o de la URL. {@code code} es lo que se le manda al Módulo 3 (UC6 FR-007).
 */
public record CurrentUser(UUID id, String code, Role role) {

    public CurrentUser {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(role, "role");
    }
}
