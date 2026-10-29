package edu.unimagdalena.reservasunimag.domain.model.resource;

import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;

public record SearchResourcesQuery(CurrentUser user, TimeSlot slot, ResourceFilter filter, PageRequest page) {
}
