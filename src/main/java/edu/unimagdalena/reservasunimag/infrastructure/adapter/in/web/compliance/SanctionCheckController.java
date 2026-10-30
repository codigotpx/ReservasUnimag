package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.compliance;

import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckFilter;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckOutcome;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.QuerySanctionChecksPort;
import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * UC6 FR-009: el historial de las consultas al Módulo 3, solo para Dirección de Programa. No es una pantalla de
 * consulta de sanciones (FR-002): no responde si alguien está sancionado hoy, solo qué se preguntó y qué se contestó.
 */
@RestController
@RequestMapping("/api")
class SanctionCheckController {

    private final QuerySanctionChecksPort queryChecks;
    private final int pageSize;

    SanctionCheckController(QuerySanctionChecksPort queryChecks, ReservationProperties properties) {
        this.queryChecks = queryChecks;
        this.pageSize = properties.pageSize();
    }

    @GetMapping("/sanction-checks")
    ResponseEntity<SanctionChecksResponse> list(
            CurrentUser currentUser,
            @RequestParam(required = false) String personCode,
            @RequestParam(required = false) SanctionCheckOutcome outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "debe ser mayor o igual a 1") int page) {
        if (currentUser.role() != Role.DIRECCION_PROGRAMA) {
            throw new AccessDeniedException("Solo Dirección de Programa audita las consultas de sanciones");
        }
        var result = queryChecks.list(new SanctionCheckFilter(personCode, outcome, from, to),
                new PageRequest(page, pageSize));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SanctionChecksResponse.from(result));
    }
}
