package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.resource;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceOccupancy;
import edu.unimagdalena.reservasunimag.domain.model.resource.ScheduledUse;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

import java.util.List;

/**
 * El calendario de un espacio. {@code operationalStatus} es el estado del Módulo 1 ahora: el mantenimiento no trae
 * franjas, así que se muestra como aviso y no como bloques.
 */
record OccupancyResponse(String resourceId, String name, String category, String location,
                         String operationalStatus, String from, String to, List<UseBody> uses) {

    static OccupancyResponse from(ResourceOccupancy occupancy) {
        var resource = occupancy.resource();
        return new OccupancyResponse(resource.id(), resource.name(), resource.category().name(), resource.location(),
                resource.operationalStatus().name(), occupancy.from().toString(), occupancy.to().toString(),
                occupancy.uses().stream().map(UseBody::from).toList());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record UseBody(String kind, String start, String end, String course, String program) {

        static UseBody from(ScheduledUse use) {
            return new UseBody(use.kind().name(), IsoTimes.offset(use.startsAt()), IsoTimes.offset(use.endsAt()),
                    use.course(), use.program());
        }
    }
}
