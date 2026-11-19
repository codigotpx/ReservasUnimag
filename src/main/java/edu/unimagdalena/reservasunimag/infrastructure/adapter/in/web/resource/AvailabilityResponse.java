package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.resource;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.resource.AvailabilityAnswer;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

/**
 * La forma exacta de UC8 Contratos §1. {@code reason} solo viaja cuando no está disponible y {@code occupiedUntil}
 * solo cuando hay una fecha que prometer; ninguno viaja como {@code null}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record AvailabilityResponse(
        String resourceId,
        String name,
        String category,
        SearchResourcesResponse.TimeSlotBody timeSlot,
        boolean available,
        String status,
        String reason,
        String occupiedUntil,
        String answeredAt) {

    static AvailabilityResponse from(AvailabilityAnswer answer) {
        var resource = answer.resource();
        return new AvailabilityResponse(resource.id(), resource.name(), resource.category().name(),
                SearchResourcesResponse.TimeSlotBody.from(answer.slot()), answer.available(), answer.status().name(),
                answer.reason(), answer.occupiedUntil() == null ? null : IsoTimes.offset(answer.occupiedUntil()),
                IsoTimes.offset(answer.answeredAt()));
    }
}
