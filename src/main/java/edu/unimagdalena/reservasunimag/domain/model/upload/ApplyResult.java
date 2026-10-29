package edu.unimagdalena.reservasunimag.domain.model.upload;

/**
 * Lo que hizo la confirmación. {@code eventsQueued} son las cancelaciones que quedaron en la outbox (UC11); las fichas
 * de los bloqueos las encola UC2 por su cuenta y no se cuentan.
 */
public record ApplyResult(int blocksCreated, int blocksUnchanged, int blocksWithdrawn, int reservationsCancelled,
                          int eventsQueued) {
}
