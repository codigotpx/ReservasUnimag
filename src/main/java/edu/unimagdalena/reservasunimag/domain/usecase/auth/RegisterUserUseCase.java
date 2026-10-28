package edu.unimagdalena.reservasunimag.domain.usecase.auth;

import edu.unimagdalena.reservasunimag.domain.model.error.UserAlreadyRegisteredException;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.model.user.User;
import edu.unimagdalena.reservasunimag.domain.port.in.RegisterUserPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.PasswordHasherPort;
import edu.unimagdalena.reservasunimag.domain.port.out.UserRepositoryPort;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public class RegisterUserUseCase implements RegisterUserPort {

    private final UserRepositoryPort users;
    private final PasswordHasherPort passwordHasher;
    private final Clock clock;

    public RegisterUserUseCase(UserRepositoryPort users, PasswordHasherPort passwordHasher, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    @Override
    public User register(String code, String name, String email, String password) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (users.findByEmail(normalizedEmail).isPresent()) {
            throw new UserAlreadyRegisteredException();
        }
        User user = new User(UUID.randomUUID(), code, name, normalizedEmail, passwordHasher.hash(password), Role.ESTUDIANTE, true, clock.now());
        return users.save(user);
    }
}
