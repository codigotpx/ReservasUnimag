package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChange;

/**
 * El inicio de uso registrado. {@code alreadyStarted} indica que ya lo estaba y no se registró ni avisó nada nuevo
 * (FR-008); entonces {@code change} es el registro original.
 */
public record UseStartResult(Reservation reservation, StatusChange change, boolean alreadyStarted) {
}
