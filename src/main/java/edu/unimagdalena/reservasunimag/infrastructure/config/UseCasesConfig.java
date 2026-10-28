package edu.unimagdalena.reservasunimag.infrastructure.config;

import edu.unimagdalena.reservasunimag.domain.port.in.GetProfilePort;
import edu.unimagdalena.reservasunimag.domain.port.in.SignInPort;
import edu.unimagdalena.reservasunimag.domain.port.out.PasswordHasherPort;
import edu.unimagdalena.reservasunimag.domain.port.out.UserRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.usecase.auth.GetProfileUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.auth.SignInUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Los casos de uso no llevan {@code @Service}: se registran aquí, y aquí se decide su transacción. */
@Configuration(proxyBeanMethods = false)
class UseCasesConfig {

    @Bean
    SignInPort signInPort(UserRepositoryPort users, PasswordHasherPort passwordHasher) {
        return new SignInUseCase(users, passwordHasher);
    }

    @Bean
    GetProfilePort getProfilePort(UserRepositoryPort users) {
        return new GetProfileUseCase(users);
    }
}
