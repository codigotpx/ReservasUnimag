package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.schedule;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/** Cuerpo de UC3 Contratos §3. Sin {@code confirm} solo se previsualiza. */
record ExtraordinaryNeedRequest(
        @NotBlank(message = "es obligatorio") String resourceId,
        @NotNull(message = "es obligatorio") LocalDate date,
        @NotNull(message = "es obligatorio") @JsonFormat(pattern = "HH:mm") LocalTime start,
        @NotNull(message = "es obligatorio") @JsonFormat(pattern = "HH:mm") LocalTime end,
        @NotBlank(message = "es obligatorio") String course,
        @NotBlank(message = "es obligatorio") String courseCode,
        @NotBlank(message = "es obligatorio") String group,
        @NotBlank(message = "es obligatorio") String program,
        String instructor,
        boolean confirm) {
}
