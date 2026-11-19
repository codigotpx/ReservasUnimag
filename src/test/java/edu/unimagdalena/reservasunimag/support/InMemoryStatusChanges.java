package edu.unimagdalena.reservasunimag.support;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.UseStart;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChange;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeFilter;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryStatusNotifierPort;
import edu.unimagdalena.reservasunimag.domain.port.out.StatusChangeRepositoryPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** El historial de UC7 y el aviso al Módulo 1 en memoria. */
public final class InMemoryStatusChanges implements StatusChangeRepositoryPort {

    public final List<StatusChange> stored = new ArrayList<>();

    @Override
    public void insert(StatusChange change) {
        stored.add(change);
    }

    @Override
    public Optional<StatusChange> findUseStarted(UUID reservationId) {
        return stored.stream()
                .filter(c -> reservationId.equals(c.reservationId()) && c.reason() == StatusChangeReason.USE_STARTED)
                .findFirst();
    }

    @Override
    public Page<StatusChange> findByResource(String resourceId, StatusChangeFilter filter, PageRequest page) {
        return Page.of(stored.stream().filter(c -> c.resourceId().equals(resourceId)).toList(), page);
    }

    /** Lo que se dejaría en la outbox hacia el Módulo 1. */
    public static final class RecordingInventoryNotifier implements InventoryStatusNotifierPort {

        public final List<UseStart> sent = new ArrayList<>();

        @Override
        public UUID useStarted(UseStart useStart) {
            sent.add(useStart);
            return UUID.randomUUID();
        }
    }
}
