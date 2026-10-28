package edu.unimagdalena.reservasunimag.domain.model.user;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID id,
        String code,
        String name,
        String email,
        String passwordHash,
        Role role,
        boolean active,
        Instant createdAt) {

    public CurrentUser asCurrentUser() {
        return new CurrentUser(id, code, role);
    }
}
