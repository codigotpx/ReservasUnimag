package edu.unimagdalena.reservasunimag.domain.usecase.auth;

import edu.unimagdalena.reservasunimag.domain.model.error.InvalidCredentialsException;
import edu.unimagdalena.reservasunimag.domain.model.user.User;
import edu.unimagdalena.reservasunimag.domain.port.in.SignInPort;
import edu.unimagdalena.reservasunimag.domain.port.out.PasswordHasherPort;
import edu.unimagdalena.reservasunimag.domain.port.out.UserRepositoryPort;

import java.util.Locale;

public class SignInUseCase implements SignInPort {

    private final UserRepositoryPort users;
    private final PasswordHasherPort passwordHasher;

    public SignInUseCase(UserRepositoryPort users, PasswordHasherPort passwordHasher) {
        this.users = users;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public User signIn(String email, String rawPassword) {
        return users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(User::active)
                .filter(user -> passwordHasher.matches(rawPassword, user.passwordHash()))
                .orElseThrow(InvalidCredentialsException::new);
    }
}
