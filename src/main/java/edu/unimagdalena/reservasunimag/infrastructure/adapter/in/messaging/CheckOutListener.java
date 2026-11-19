package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.messaging;

import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutReport;
import edu.unimagdalena.reservasunimag.domain.port.in.ReceiveCheckOutPort;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * UC12: el adaptador de entrada del topic de check-out del Módulo 3. Funciona igual que el de no asistencia: la inbox
 * y el caso de uso en una transacción, el offset después del commit, y lo roto o lo que apunta a una reserva
 * inexistente al DLT.
 */
@Component
class CheckOutListener {

    private static final Logger log = LoggerFactory.getLogger(CheckOutListener.class);
    static final String INBOX_TYPE = "CHECK_OUT";

    private final ReceiveCheckOutPort receiveCheckOut;
    private final InboxGuard inbox;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;

    CheckOutListener(ReceiveCheckOutPort receiveCheckOut, InboxGuard inbox, ObjectMapper objectMapper,
                     PlatformTransactionManager transactionManager) {
        this.receiveCheckOut = receiveCheckOut;
        this.inbox = inbox;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @KafkaListener(topics = "${reservations.kafka.topic.check-out}")
    void onMessage(ConsumerRecord<String, String> record) {
        CheckOutMessage message = CheckOutMessage.parse(objectMapper, record.value());
        transaction.executeWithoutResult(status -> {
            if (!inbox.firstTime(message.eventId(), record.topic(), INBOX_TYPE, message.reservationId(),
                    record.value())) {
                log.info("Check-out: el evento {} ya se había procesado; se descarta", message.eventId());
                return;
            }
            receiveCheckOut.receive(new CheckOutReport(message.eventId(), message.reservationId(),
                    message.occurredAt(), message.verdict(), message.carriesDamage()));
        });
    }
}
