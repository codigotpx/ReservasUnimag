package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReportFilter;
import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryCancellationReportsPort;
import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * UC11 FR-010: qué se le reportó al Módulo 3 sobre cada cancelación. El de una reserva lo ven su titular y Dirección
 * de Programa; el listado, solo Dirección de Programa.
 */
@RestController
@RequestMapping("/api")
class CancellationReportController {

    private final QueryCancellationReportsPort queryReports;
    private final int pageSize;

    CancellationReportController(QueryCancellationReportsPort queryReports, ReservationProperties properties) {
        this.queryReports = queryReports;
        this.pageSize = properties.pageSize();
    }

    @GetMapping("/reservations/{reservationId}/cancellation-report")
    ResponseEntity<CancellationReportResponse> report(CurrentUser currentUser, @PathVariable UUID reservationId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CancellationReportResponse.from(queryReports.find(currentUser, reservationId)));
    }

    @GetMapping("/cancellation-reports")
    ResponseEntity<CancellationReportsResponse> list(
            CurrentUser currentUser,
            @RequestParam(required = false) CancellationOrigin origin,
            @RequestParam(required = false) DeliveryStatus delivery,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "debe ser mayor o igual a 1") int page) {
        if (currentUser.role() != Role.DIRECCION_PROGRAMA) {
            throw new AccessDeniedException("Solo Dirección de Programa audita los reportes");
        }
        var result = queryReports.list(new CancellationReportFilter(origin, delivery, from, to),
                new PageRequest(page, pageSize));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CancellationReportsResponse.from(result));
    }
}
