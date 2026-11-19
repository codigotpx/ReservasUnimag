package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.resource;

import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.AvailabilityAnswer;
import edu.unimagdalena.reservasunimag.domain.port.in.CheckAvailabilityPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * {@code GET /api/resources/{resourceId}/availability}: UC8 Contratos §1. Es una cortesía para la interfaz —revalidar
 * antes de confirmar— y no sustituye la revalidación de UC2 dentro de su transacción. Cualquier rol con sesión.
 */
@RestController
@RequestMapping("/api/resources/{resourceId}/availability")
class AvailabilityController {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityController.class);

    private final CheckAvailabilityPort availability;

    AvailabilityController(CheckAvailabilityPort availability) {
        this.availability = availability;
    }

    @GetMapping
    ResponseEntity<AvailabilityResponse> check(
            @PathVariable String resourceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(pattern = "HH:mm") LocalTime start,
            @RequestParam @DateTimeFormat(pattern = "HH:mm") LocalTime end) {
        TimeSlot slot = new TimeSlot(date, start, end);
        long started = System.nanoTime();
        AvailabilityAnswer answer = availability.check(resourceId, slot);
        // Sin titular: la respuesta no lo tiene y el log tampoco (UC8 FR-005).
        log.info("Disponibilidad de {} el {} {}-{}: {} en {} ms", resourceId, date, start, end, answer.status(),
                (System.nanoTime() - started) / 1_000_000);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AvailabilityResponse.from(answer));
    }
}
