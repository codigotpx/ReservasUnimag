package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.user.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {

    Optional<User> findByEmail(String email);

    Optional<User> findById(UUID id);

    User save(User user);
}
