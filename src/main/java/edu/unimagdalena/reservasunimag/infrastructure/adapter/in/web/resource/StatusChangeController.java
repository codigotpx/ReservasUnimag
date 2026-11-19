package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.resource;

import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeFilter;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryStatusChangesPort;
import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** UC7 Contratos §2: la historia de la ocupación de un recurso, para auditar. Solo Dirección de Programa. */
@RestController
class StatusChangeController {

    private final QueryStatusChangesPort statusChanges;
    private final int pageSize;

    StatusChangeController(QueryStatusChangesPort statusChanges, ReservationProperties properties) {
        this.statusChanges = statusChanges;
        this.pageSize = properties.pageSize();
    }

    @GetMapping("/api/resources/{resourceId}/status-changes")
    ResponseEntity<StatusChangesResponse> history(
            CurrentUser currentUser,
            @PathVariable String resourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) StatusChangeReason reason,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "debe ser mayor o igual a 1") int page) {
        if (currentUser.role() != Role.DIRECCION_PROGRAMA) {
            throw new AccessDeniedException("Solo Dirección de Programa audita la historia de un recurso");
        }
        var history = statusChanges.history(resourceId, new StatusChangeFilter(from, to, reason),
                new PageRequest(page, pageSize));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(StatusChangesResponse.from(history));
    }
}
