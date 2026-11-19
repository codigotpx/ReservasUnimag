package edu.unimagdalena.reservasunimag.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReservationProperties.class)
class PropertiesConfig {
}
