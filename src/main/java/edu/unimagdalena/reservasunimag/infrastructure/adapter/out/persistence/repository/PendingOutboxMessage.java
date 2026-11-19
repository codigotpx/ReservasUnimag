package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import java.util.UUID;

/** Lo que el publicador necesita de un evento pendiente para mandarlo. */
public record PendingOutboxMessage(UUID id, String type, UUID reservationId, String payload, int attempts) {
}
