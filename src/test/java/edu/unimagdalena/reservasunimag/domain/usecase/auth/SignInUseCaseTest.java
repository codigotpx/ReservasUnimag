package edu.unimagdalena.reservasunimag.domain.usecase.auth;

import edu.unimagdalena.reservasunimag.domain.model.error.InvalidCredentialsException;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.model.user.User;
import edu.unimagdalena.reservasunimag.domain.port.out.PasswordHasherPort;
import edu.unimagdalena.reservasunimag.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignInUseCaseTest {

    private static final User ACTIVE = user("activo@unimagdalena.edu.co", true);
    private static final User INACTIVE = user("inactivo@unimagdalena.edu.co", false);

    private final SignInUseCase useCase = new SignInUseCase(new InMemoryUsers(List.of(ACTIVE, INACTIVE)),
            new PlainHasher());

    @Test
    void returnsTheUserWhenCredentialsMatch() {
        assertThat(useCase.signIn("activo@unimagdalena.edu.co", "secreta")).isEqualTo(ACTIVE);
    }

    @Test
    void ignoresCaseAndSurroundingSpacesInTheEmail() {
        assertThat(useCase.signIn("  Activo@UNIMAGDALENA.edu.co ", "secreta")).isEqualTo(ACTIVE);
    }

    @Test
    void rejectsWrongPasswordUnknownEmailAndInactiveUserTheSameWay() {
        assertThatThrownBy(() -> useCase.signIn("activo@unimagdalena.edu.co", "otra"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> useCase.signIn("nadie@unimagdalena.edu.co", "secreta"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> useCase.signIn("inactivo@unimagdalena.edu.co", "secreta"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private static User user(String email, boolean active) {
        return new User(UUID.randomUUID(), "2021114001", "Prueba", email, "hash:secreta", Role.ESTUDIANTE, active,
                Instant.parse("2026-09-01T00:00:00Z"));
    }

    private record InMemoryUsers(List<User> users) implements UserRepositoryPort {

        @Override
        public Optional<User> findByEmail(String email) {
            return users.stream().filter(user -> user.email().equals(email)).findFirst();
        }

        @Override
        public Optional<User> findById(UUID id) {
            return users.stream().filter(user -> user.id().equals(id)).findFirst();
        }

        @Override
        public User save(User user) {
            users.add(user);
            return user;
        }
    }

    private static final class PlainHasher implements PasswordHasherPort {

        @Override
        public String hash(String rawPassword) {
            return "hash:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            return hash(rawPassword).equals(passwordHash);
        }
    }
}
