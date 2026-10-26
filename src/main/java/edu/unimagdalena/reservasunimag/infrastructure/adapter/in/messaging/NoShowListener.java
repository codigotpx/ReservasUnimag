package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.messaging;

import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowReport;
import edu.unimagdalena.reservasunimag.domain.port.in.ReceiveNoShowPort;
import edu.unimagdalena.reservasunimag.domain.port.in.VoidNoShowPort;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * UC9: el adaptador de entrada del topic de no asistencia del Módulo 3, al mismo nivel que un controlador REST. La
 * inbox y el caso de uso van en una sola transacción; el offset se confirma cuando este método termina, ya con la
 * transacción confirmada. Un evento roto o sobre una reserva que no existe sale de aquí con excepción y acaba en el
 * DLT ({@link edu.unimagdalena.reservasunimag.infrastructure.config.KafkaConsumerConfig}); un rechazo de negocio no
 * es una excepción, es un acuse.
 */
@Component
class NoShowListener {

    private static final Logger log = LoggerFactory.getLogger(NoShowListener.class);
    static final String INBOX_TYPE = "NO_SHOW";

    private final ReceiveNoShowPort receiveNoShow;
    private final VoidNoShowPort voidNoShow;
    private final InboxGuard inbox;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;

    NoShowListener(ReceiveNoShowPort receiveNoShow, VoidNoShowPort voidNoShow, InboxGuard inbox,
                   ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.receiveNoShow = receiveNoShow;
        this.voidNoShow = voidNoShow;
        this.inbox = inbox;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @KafkaListener(topics = "${reservations.kafka.topic.no-show}")
    void onMessage(ConsumerRecord<String, String> record) {
        NoShowMessage message = NoShowMessage.parse(objectMapper, record.value());
        transaction.executeWithoutResult(status -> {
            if (!inbox.firstTime(message.eventId(), record.topic(), INBOX_TYPE, message.reservationId(),
                    record.value())) {
                log.info("No asistencia: el evento {} ya se había procesado; se descarta", message.eventId());
                return;
            }
            switch (message.type()) {
                case NoShowReported -> receiveNoShow.receive(
                        new NoShowReport(message.eventId(), message.reservationId(), message.verifiedAt()));
                case NoShowReportVoided -> {
                    boolean voided = voidNoShow.voidReport(message.reservationId(), message.voidedReason());
                    log.info("No asistencia de la reserva {}: anulación {}", message.reservationId(),
                            voided ? "aplicada" : "sin ausencia vigente que anular");
                }
            }
        });
    }
}
