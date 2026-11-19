package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.mapper;

import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChange;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.StatusChangeJpaEntity;

public final class StatusChangeMapper {

    private StatusChangeMapper() {
    }

    public static StatusChangeJpaEntity toEntity(StatusChange change) {
        return new StatusChangeJpaEntity(change.id(), change.resourceId(), change.category(), change.reservationId(),
                change.occupancyStart(), change.occupancyEnd(), change.previousStatusModule2(), change.newStatus(),
                change.reason(), change.changedAt(), change.notifiedStatus(), change.outboxMessageId());
    }

    public static StatusChange toDomain(StatusChangeJpaEntity entity) {
        return new StatusChange(entity.getId(), entity.getResourceId(), entity.getResourceCategory(),
                entity.getReservationId(), entity.getOccupancyStartsAt(), entity.getOccupancyEndsAt(),
                entity.getPreviousStatusModule2(), entity.getNewStatus(), entity.getReason(), entity.getChangedAt(),
                entity.getNotifiedStatus(), entity.getOutboxMessageId());
    }
}
