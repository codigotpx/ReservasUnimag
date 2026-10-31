package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.resource;

import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.ViewResourceOccupancyPort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** El calendario de ocupación de un espacio, solo para Dirección de Programa. Es una foto: no se guarda en caché. */
@RestController
class OccupancyController {

    private final ViewResourceOccupancyPort occupancy;

    OccupancyController(ViewResourceOccupancyPort occupancy) {
        this.occupancy = occupancy;
    }

    @GetMapping("/api/resources/{resourceId}/occupancy")
    ResponseEntity<OccupancyResponse> view(
            CurrentUser currentUser,
            @PathVariable String resourceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (currentUser.role() != Role.DIRECCION_PROGRAMA) {
            throw new AccessDeniedException("Solo Dirección de Programa ve la ocupación");
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(OccupancyResponse.from(occupancy.view(resourceId, from, to)));
    }
}
