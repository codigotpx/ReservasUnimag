package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.port.in.RegisterUseStartedPort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * UC7 Contratos §3: el titular registra que llegó. Es la entrada provisional mientras P-02 no diga por dónde entra ese
 * hecho; el día que se decida, lo que llegue llamará al mismo puerto. La reserva de otra persona responde 404, igual
 * que al cancelar, para no confirmar que existe.
 */
@RestController
class StartUseController {

    private final RegisterUseStartedPort registerUseStarted;

    StartUseController(RegisterUseStartedPort registerUseStarted) {
        this.registerUseStarted = registerUseStarted;
    }

    @PostMapping("/api/reservations/{reservationId}/start-use")
    ResponseEntity<StartUseResponse> startUse(CurrentUser currentUser, @PathVariable UUID reservationId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(StartUseResponse.from(registerUseStarted.startByHolder(currentUser, reservationId)));
    }
}
