package edu.unimagdalena.reservasunimag.domain.model.resource;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un cambio de ocupación de un recurso, como lo pide UC7 FR-009. {@code previousStatusModule2} es el estado anterior
 * según los datos del Módulo 2 —{@code DISPONIBLE}, {@code RESERVADO} o {@code BLOQUEO_ACADEMICO}—, nunca lo que
 * diga el Módulo 1. {@code outboxMessageId} es el aviso que salió, y solo existe en un inicio de uso.
 */
public record StatusChange(
        UUID id,
        String resourceId,
        ResourceCategory category,
        UUID reservationId,
        Instant occupancyStart,
        Instant occupancyEnd,
        DisplayedStatus previousStatusModule2,
        DisplayedStatus newStatus,
        StatusChangeReason reason,
        Instant changedAt,
        NotifiedStatus notifiedStatus,
        UUID outboxMessageId) {

    public StatusChange {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(notifiedStatus, "notifiedStatus");
        if ((outboxMessageId == null) != (notifiedStatus == NotifiedStatus.NOT_SENT)) {
            throw new IllegalArgumentException("solo un cambio con aviso tiene su mensaje en la outbox");
        }
        if (outboxMessageId != null && !reason.notifiesInventory()) {
            throw new IllegalArgumentException("al Módulo 1 solo se le avisa el inicio de uso (FR-012)");
        }
    }
}
