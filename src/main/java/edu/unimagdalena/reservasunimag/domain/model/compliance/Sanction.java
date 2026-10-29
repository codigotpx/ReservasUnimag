package edu.unimagdalena.reservasunimag.domain.model.compliance;

import java.time.LocalDate;

public record Sanction(String reason, LocalDate start, LocalDate end, String scope) {
}
