package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.reservation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de UC4 Contratos §2. Sin {@code confirm} solo se previsualiza. */
record MaintenanceCancellationRequest(
        @NotBlank(message = "es obligatorio: va al Módulo 3 y a la auditoría")
        @Size(max = 500, message = "admite hasta 500 caracteres") String reason,
        boolean confirm) {
}
