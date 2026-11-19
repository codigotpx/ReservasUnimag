package edu.unimagdalena.reservasunimag.infrastructure.config;

import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    java.time.Clock systemClock() {
        return java.time.Clock.system(TimeSlot.ZONE);
    }

    @Bean
    Clock clock(java.time.Clock systemClock) {
        return systemClock::instant;
    }
}
