package edu.unimagdalena.reservasunimag.infrastructure.config;

import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** Hace que el dominio pueda pedir un bloque atómico sin conocer Spring. */
@Component
class TransactionAdapter implements TransactionPort {

    private final TransactionTemplate template;

    TransactionAdapter(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T required(Supplier<T> work) {
        return template.execute(status -> work.get());
    }
}
