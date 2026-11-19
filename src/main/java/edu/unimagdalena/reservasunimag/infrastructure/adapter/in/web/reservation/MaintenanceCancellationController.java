package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.CancelForMaintenancePort;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC4 FR-010, Contratos §2. Solo Dirección de Programa, y existe porque nadie nos avisa todavía de que un recurso
 * quedó fuera de servicio (P-20). Cuando llegue ese aviso, quien lo reciba usará el mismo puerto y esto quedará como
 * herramienta de operación.
 */
@RestController
class MaintenanceCancellationController {

    private final CancelForMaintenancePort cancelForMaintenance;

    MaintenanceCancellationController(CancelForMaintenancePort cancelForMaintenance) {
        this.cancelForMaintenance = cancelForMaintenance;
    }

    @PostMapping("/api/resources/{resourceId}/cancel-reservations")
    ResponseEntity<Object> cancelReservations(CurrentUser currentUser, @PathVariable String resourceId,
                                              @Valid @RequestBody MaintenanceCancellationRequest body) {
        if (currentUser.role() != Role.DIRECCION_PROGRAMA) {
            throw new AccessDeniedException("Solo Dirección de Programa cancela por mantenimiento");
        }
        var result = body.confirm()
                ? cancelForMaintenance.apply(resourceId, body.reason())
                : cancelForMaintenance.preview(resourceId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(MaintenanceCancellationResponse.from(result));
    }
}
