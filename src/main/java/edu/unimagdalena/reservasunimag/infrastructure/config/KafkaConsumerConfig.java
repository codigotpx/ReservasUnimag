package edu.unimagdalena.reservasunimag.infrastructure.config;

import edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.in.messaging.InvalidEventException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Qué hacen los consumidores del Módulo 3 cuando un evento falla (plan general, Mensajería).
 * <ul>
 *   <li>Lo que no mejora reintentando —un evento roto o sobre una reserva que no existe— va al DLT
 *   {@code module2.dlt.<topic de origen>} con la causa en las cabeceras, y el consumidor sigue.</li>
 *   <li>Lo demás —la base caída, un bloqueo— se reintenta con espera creciente y sin tope, sin mover el offset. El
 *   consumidor se queda en ese evento: saltárselo rompería el orden de la reserva, y mandarlo al DLT escondería un
 *   reporte válido.</li>
 * </ul>
 * Spring Boot le pone este manejador a la fábrica de contenedores de {@code @KafkaListener}.
 */
@Configuration(proxyBeanMethods = false)
class KafkaConsumerConfig {

    static final String DLT_PREFIX = "module2.dlt.";

    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, failure) -> new TopicPartition(DLT_PREFIX + record.topic(), -1));
        var backOff = new ExponentialBackOff(1_000, 2.0);
        backOff.setMaxInterval(60_000);
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class, ReservationNotFoundException.class);
        return handler;
    }
}
