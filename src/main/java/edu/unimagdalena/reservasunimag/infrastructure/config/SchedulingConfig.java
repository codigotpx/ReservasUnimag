package edu.unimagdalena.reservasunimag.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita {@code @Scheduled}: lo usa el publicador de la outbox. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {
}
