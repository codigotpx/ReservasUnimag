package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.reservation.UseStart;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

import java.util.UUID;

/**
 * {@code ResourceUseStarted} tal como viaja al Módulo 1 (UC7 Contratos §4). {@code newStatus} es siempre
 * {@code EN_USO}. {@code useEndsAt} es la instrucción de cierre: la hora de fin en un espacio, y ausente en un activo,
 * cuyo uso termina con la devolución. No viaja quién usa el recurso: el inventario no lo necesita.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResourceUseStartedEvent(String eventId, String type, int version, String occurredAt, String source,
                                      Data data) {

    public static final String TYPE = "ResourceUseStarted";
    static final String SOURCE = "MODULE_2";

    public static ResourceUseStartedEvent from(UUID eventId, UseStart useStart) {
        var reservation = useStart.reservation();
        return new ResourceUseStartedEvent(eventId.toString(), TYPE, 1, IsoTimes.offset(useStart.startedAt()), SOURCE,
                new Data(
                        reservation.resourceId(),
                        reservation.category().name(),
                        reservation.id().toString(),
                        new OccupancyBody(IsoTimes.offset(reservation.startsAt()),
                                IsoTimes.offset(reservation.endsAt())),
                        "EN_USO",
                        useStart.useEndsAt() == null ? null : IsoTimes.offset(useStart.useEndsAt()),
                        "USE_STARTED"));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Data(String resourceId, String resourceCategory, String reservationId, OccupancyBody occupancy,
                       String newStatus, String useEndsAt, String reason) {
    }

    public record OccupancyBody(String start, String end) {
    }
}
