package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Cuerpo de {@code POST /api/reservations}: un espacio lleva {@code start} y {@code end}; un activo, solo
 * {@code pickup}. No lleva categoría ni origen: la categoría la dice el Módulo 1 y el origen es siempre estudiantil
 * con el titular del JWT, así que nadie puede forzar la forma equivocada.
 */
record CreateReservationRequest(
        @NotBlank(message = "es obligatorio") String resourceId,
        @NotNull(message = "es obligatorio") LocalDate date,
        LocalTime start,
        LocalTime end,
        LocalTime pickup) {
}
