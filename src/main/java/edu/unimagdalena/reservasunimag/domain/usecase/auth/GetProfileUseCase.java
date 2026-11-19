package edu.unimagdalena.reservasunimag.domain.usecase.auth;

import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.User;
import edu.unimagdalena.reservasunimag.domain.port.in.GetProfilePort;
import edu.unimagdalena.reservasunimag.domain.port.out.UserRepositoryPort;

import java.util.Optional;

public class GetProfileUseCase implements GetProfilePort {

    private final UserRepositoryPort users;

    public GetProfileUseCase(UserRepositoryPort users) {
        this.users = users;
    }

    @Override
    public Optional<User> getProfile(CurrentUser currentUser) {
        return users.findById(currentUser.id()).filter(User::active);
    }
}
