package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.scheduler;

import edu.unimagdalena.reservasunimag.domain.port.in.RegisterUseStartedPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * UC7 FR-004: una clase empieza a su hora y nadie tiene que registrarlo; el horario ya es el registro. Esta tarea solo
 * registra ese hecho y el aviso sale por la outbox. No libera ni recalcula nada: eso lo dice el rango de cada
 * ocupación en cada consulta (FR-010, FR-011). Se apaga con {@code reservations.academic.use-start-enabled}.
 */
@Component
@ConditionalOnProperty(name = "reservations.academic.use-start-enabled", havingValue = "true", matchIfMissing = true)
class AcademicUseStartScheduler {

    private static final Logger log = LoggerFactory.getLogger(AcademicUseStartScheduler.class);

    private final RegisterUseStartedPort registerUseStarted;

    AcademicUseStartScheduler(RegisterUseStartedPort registerUseStarted) {
        this.registerUseStarted = registerUseStarted;
    }

    @Scheduled(fixedDelayString = "${reservations.academic.use-start-interval:1m}")
    void startDueClasses() {
        try {
            var result = registerUseStarted.startDueAcademicBlocks();
            if (result.started() + result.skippedForMaintenance() + result.failed() > 0) {
                log.info("Clases que llegaron a su hora: {} empezadas, {} en mantenimiento, {} con error",
                        result.started(), result.skippedForMaintenance(), result.failed());
            }
        } catch (RuntimeException failure) {
            // Una caída de la base no debe matar el planificador: se vuelve a intentar en la siguiente pasada.
            log.error("No se pudo revisar las clases que empiezan: {}", failure.getMessage());
        }
    }
}
