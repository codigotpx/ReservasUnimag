package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

record RegisterRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{10}$", message = "El código estudiantil debe tener exactamente 10 números") String code,
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank String password) {
}
