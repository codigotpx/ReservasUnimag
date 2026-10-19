package edu.unimagdalena.reservasunimag.infrastructure.config;

import edu.unimagdalena.reservasunimag.domain.port.in.CheckAvailabilityPort;
import edu.unimagdalena.reservasunimag.domain.port.in.CheckSanctionsPort;
import edu.unimagdalena.reservasunimag.domain.port.in.GetProfilePort;
import edu.unimagdalena.reservasunimag.domain.port.in.SearchResourcesPort;
import edu.unimagdalena.reservasunimag.domain.port.in.SignInPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.CompliancePort;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.OccupancyRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.PasswordHasherPort;
import edu.unimagdalena.reservasunimag.domain.port.out.UserRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.usecase.auth.GetProfileUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.auth.SignInUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.checkavailability.CheckAvailabilityUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.checksanctions.CheckSanctionsUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.searchresources.NextFreeSlotFinder;
import edu.unimagdalena.reservasunimag.domain.usecase.searchresources.SearchResourcesUseCase;
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

    @Bean
    CheckAvailabilityPort checkAvailabilityPort(OccupancyRepositoryPort occupancies, Clock clock) {
        return new CheckAvailabilityUseCase(occupancies, clock);
    }

    @Bean
    CheckSanctionsPort checkSanctionsPort(CompliancePort compliance) {
        return new CheckSanctionsUseCase(compliance);
    }

    @Bean
    NextFreeSlotFinder nextFreeSlotFinder(OccupancyRepositoryPort occupancies, Clock clock,
                                          ReservationProperties properties) {
        return new NextFreeSlotFinder(occupancies, clock, properties.nextSlotStep());
    }

    @Bean
    SearchResourcesPort searchResourcesPort(InventoryPort inventory, CheckAvailabilityPort availability,
                                            CheckSanctionsPort sanctions, NextFreeSlotFinder nextFreeSlot,
                                            Clock clock) {
        return new SearchResourcesUseCase(inventory, availability, sanctions, nextFreeSlot, clock);
    }
}
