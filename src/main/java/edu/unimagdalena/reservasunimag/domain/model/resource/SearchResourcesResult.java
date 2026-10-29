package edu.unimagdalena.reservasunimag.domain.model.resource;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionNotice;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;

import java.time.Instant;

/**
 * Resultado de la consulta. {@code message}, {@code nextTimeSlot} y {@code sanctionNotice} son {@code null}
 * cuando no aplican.
 */
public record SearchResourcesResult(
        TimeSlot slot,
        ResourceFilter filter,
        Page<ResourceWithStatus> page,
        boolean anySelectable,
        boolean catalogTruncated,
        Instant queriedAt,
        String message,
        TimeSlot nextTimeSlot,
        SanctionNotice sanctionNotice) {
}
