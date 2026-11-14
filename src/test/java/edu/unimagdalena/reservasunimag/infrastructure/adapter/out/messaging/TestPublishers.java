package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging;

import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.CancellationReportJpaRepository;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.OutboxMessageJpaRepository;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.ReservationRecordJpaRepository;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.StatusChangeJpaRepository;
import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Un publicador aparte del de la aplicación, con otro Kafka u otras propiedades, sobre la misma base. */
final class TestPublishers {

    private TestPublishers() {
    }

    static OutboxPublisher of(ApplicationContext context, KafkaTemplate<String, String> kafka,
                              ReservationProperties properties) {
        return new OutboxPublisher(context.getBean(OutboxMessageJpaRepository.class),
                context.getBean(StatusChangeJpaRepository.class), context.getBean(CancellationReportJpaRepository.class),
                context.getBean(ReservationRecordJpaRepository.class), kafka,
                context.getBean(PlatformTransactionManager.class), properties);
    }
}
