package edu.unimagdalena.reservasunimag.domain.model.reservation;

/** Cuántas reservas vigentes tiene una persona de las que puede tener (UC2 FR-008). */
public record Quota(int used, int max) {
}
