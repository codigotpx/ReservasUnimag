package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.auth;

import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.model.user.User;

record ProfileResponse(String id, String code, String name, Role role) {

    static ProfileResponse from(User user) {
        return new ProfileResponse(user.id().toString(), user.code(), user.name(), user.role());
    }
}
