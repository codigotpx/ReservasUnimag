package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordFilter;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryReservationRecordsPort;
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
 * UC10 FR-006: qué ficha se le reportó al Módulo 3 de cada reserva. La de una reserva la ven su titular y Dirección
 * de Programa; el listado, solo Dirección de Programa.
 */
@RestController
@RequestMapping("/api")
class ReservationRecordController {

    private final QueryReservationRecordsPort queryRecords;
    private final int pageSize;

    ReservationRecordController(QueryReservationRecordsPort queryRecords, ReservationProperties properties) {
        this.queryRecords = queryRecords;
        this.pageSize = properties.pageSize();
    }

    @GetMapping("/reservations/{reservationId}/record")
    ResponseEntity<ReservationRecordResponse> record(CurrentUser currentUser, @PathVariable UUID reservationId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ReservationRecordResponse.from(queryRecords.find(currentUser, reservationId)));
    }

    @GetMapping("/reservation-records")
    ResponseEntity<ReservationRecordsResponse> list(
            CurrentUser currentUser,
            @RequestParam(required = false) ReservationOrigin origin,
            @RequestParam(required = false) DeliveryStatus delivery,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "debe ser mayor o igual a 1") int page) {
        if (currentUser.role() != Role.DIRECCION_PROGRAMA) {
            throw new AccessDeniedException("Solo Dirección de Programa audita las fichas");
        }
        var result = queryRecords.list(new ReservationRecordFilter(origin, delivery, from, to),
                new PageRequest(page, pageSize));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ReservationRecordsResponse.from(result));
    }
}
