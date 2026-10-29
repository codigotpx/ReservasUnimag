package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationResult;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.CancelReservationPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ListMyReservationsPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReserveResourcesPort;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * UC2 y UC4. Solo reservan Estudiante y Monitor; Dirección de Programa consulta pero no reserva. Cancelar lo puede
 * pedir cualquiera con sesión, pero solo sobre lo suyo. La identidad sale del JWT y nunca del cuerpo.
 */
@RestController
@RequestMapping("/api")
class ReservationController {

    private final ReserveResourcesPort reserveResources;
    private final ListMyReservationsPort listMyReservations;
    private final CancelReservationPort cancelReservation;

    ReservationController(ReserveResourcesPort reserveResources, ListMyReservationsPort listMyReservations,
                          CancelReservationPort cancelReservation) {
        this.reserveResources = reserveResources;
        this.listMyReservations = listMyReservations;
        this.cancelReservation = cancelReservation;
    }

    @PostMapping("/reservations")
    ResponseEntity<ReservationResponse> create(CurrentUser currentUser, @Valid @RequestBody CreateReservationRequest body) {
        requireCanReserve(currentUser);
        ReservationResult result = reserveResources.reserve(new ReservationRequest(currentUser, body.resourceId(),
                body.date(), body.start(), body.end(), body.pickup()));
        return ResponseEntity
                .created(URI.create("/api/reservations/" + result.reservation().id()))
                .cacheControl(CacheControl.noStore())
                .body(ReservationResponse.from(result));
    }

    @GetMapping("/reservations/mine")
    ResponseEntity<MyReservationsResponse> mine(CurrentUser currentUser,
                                                @RequestParam(name = "vigentes", defaultValue = "true") boolean onlyActive) {
        requireCanReserve(currentUser);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(MyReservationsResponse.from(listMyReservations.list(currentUser, onlyActive)));
    }

    /** UC4 Contratos §1. La de otra persona es un 404, igual que la que no existe. */
    @DeleteMapping("/reservations/{reservationId}")
    ResponseEntity<CancellationResponse> cancel(CurrentUser currentUser, @PathVariable UUID reservationId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CancellationResponse.from(cancelReservation.cancelByHolder(currentUser, reservationId)));
    }

    @GetMapping("/loans/expected-due-date")
    ResponseEntity<ExpectedDueDateResponse> expectedDueDate(
            CurrentUser currentUser,
            @RequestParam String resourceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(pattern = "HH:mm") LocalTime pickup) {
        requireCanReserve(currentUser);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ExpectedDueDateResponse.from(reserveResources.expectedDueDate(resourceId, date, pickup)));
    }

    private static void requireCanReserve(CurrentUser user) {
        if (!user.role().includes(Role.ESTUDIANTE)) {
            throw new AccessDeniedException("Solo Estudiante y Monitor reservan");
        }
    }
}
