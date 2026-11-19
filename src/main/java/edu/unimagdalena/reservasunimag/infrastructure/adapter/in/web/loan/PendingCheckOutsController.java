package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.loan;

import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.compliance.PendingReviewFilter;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryPendingCheckOutsPort;
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

/** UC12 Contratos §3 y §4: lo que espera un check-out del Módulo 3. Solo Dirección de Programa. */
@RestController
@RequestMapping("/api")
class PendingCheckOutsController {

    private final QueryPendingCheckOutsPort pending;
    private final int pageSize;

    PendingCheckOutsController(QueryPendingCheckOutsPort pending, ReservationProperties properties) {
        this.pending = pending;
        this.pageSize = properties.pageSize();
    }

    @GetMapping("/loans/pending-returns")
    ResponseEntity<PendingReturnsResponse> pendingReturns(
            CurrentUser currentUser,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "debe ser mayor o igual a 1") int page) {
        requireDirection(currentUser);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PendingReturnsResponse.from(pending.pendingReturns(new PageRequest(page, pageSize))));
    }

    @GetMapping("/reservations/pending-reviews")
    ResponseEntity<PendingReviewsResponse> pendingReviews(
            CurrentUser currentUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) ReservationOrigin origin,
            @RequestParam(required = false) String resourceId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "debe ser mayor o igual a 1") int page) {
        requireDirection(currentUser);
        var result = pending.pendingReviews(new PendingReviewFilter(from, to, origin, resourceId),
                new PageRequest(page, pageSize));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PendingReviewsResponse.from(result));
    }

    private static void requireDirection(CurrentUser user) {
        if (user.role() != Role.DIRECCION_PROGRAMA) {
            throw new AccessDeniedException("Solo Dirección de Programa consulta los pendientes de check-out");
        }
    }
}
