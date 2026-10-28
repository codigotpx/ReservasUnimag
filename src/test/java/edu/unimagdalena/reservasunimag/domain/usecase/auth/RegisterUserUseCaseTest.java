package edu.unimagdalena.reservasunimag.domain.usecase.auth;

import edu.unimagdalena.reservasunimag.domain.model.error.UserAlreadyRegisteredException;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.model.user.User;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.PasswordHasherPort;
import edu.unimagdalena.reservasunimag.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegisterUserUseCaseTest {

    private final RegisterUserUseCase useCase = new RegisterUserUseCase(new InMemoryUsers(new ArrayList<>()), new PlainHasher(),
            new FixedClock());

    @Test
    void registersTheUserAsAStudentWithTheHashedPassword() {
        User user = useCase.register("2021114002", "Estudiante Nuevo", "nuevo@unimagdalena.edu.co", "secreta");

        assertThat(user.email()).isEqualTo("nuevo@unimagdalena.edu.co");
        assertThat(user.code()).isEqualTo("2021114002");
        assertThat(user.name()).isEqualTo("Estudiante Nuevo");
        assertThat(user.role()).isEqualTo(Role.ESTUDIANTE);
        assertThat(user.active()).isTrue();
        assertThat(user.passwordHash()).isEqualTo("hash:secreta");
    }

    @Test
    void normalizesTheEmailBeforeCheckingForDuplicates() {
        useCase.register("2021114002", "Estudiante Nuevo", "  Nuevo@UNIMAGDALENA.edu.co ", "secreta");

        assertThatThrownBy(() -> useCase.register("2021114003", "Otro Estudiante", "nuevo@unimagdalena.edu.co", "secreta"))
                .isInstanceOf(UserAlreadyRegisteredException.class);
    }

    private record InMemoryUsers(List<User> users) implements UserRepositoryPort {

        public InMemoryUsers {
            users = new ArrayList<>(users);
        }

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

    private static final class FixedClock implements Clock {

        @Override
        public Instant now() {
            return Instant.parse("2026-09-01T00:00:00Z");
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
