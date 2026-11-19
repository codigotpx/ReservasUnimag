package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.resource;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionNotice;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceWithStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.SearchResourcesResult;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * La forma exacta de UC1 Contratos §1. Nunca viaja un {@code null}: lo que no aplica se omite. La única excepción
 * son los {@code filters}, donde {@code null} significa "no filtré". {@code @JsonInclude} no se hereda a los
 * registros anidados, por eso cada uno lleva el suyo.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record SearchResourcesResponse(
        TimeSlotBody timeSlot,
        FiltersBody filters,
        PaginationBody pagination,
        boolean anySelectable,
        boolean catalogTruncated,
        String queriedAt,
        String message,
        TimeSlotBody nextTimeSlot,
        SanctionNoticeBody sanctionNotice,
        List<ResourceBody> resources) {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    static SearchResourcesResponse from(SearchResourcesResult result) {
        return new SearchResourcesResponse(
                TimeSlotBody.from(result.slot()),
                new FiltersBody(result.filter().category() == null ? null : result.filter().category().name(),
                        result.filter().type(), result.filter().minCapacity()),
                new PaginationBody(result.page().page(), result.page().pageSize(), result.page().totalPages(),
                        result.page().total()),
                result.anySelectable(),
                result.catalogTruncated(),
                offsetDateTime(result.queriedAt()),
                result.message(),
                result.nextTimeSlot() == null ? null : TimeSlotBody.from(result.nextTimeSlot()),
                result.sanctionNotice() == null ? null : SanctionNoticeBody.from(result.sanctionNotice()),
                result.page().items().stream().map(ResourceBody::from).toList());
    }

    private static String offsetDateTime(Instant instant) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                instant.atZone(TimeSlot.ZONE).toOffsetDateTime().withNano(0));
    }

    record TimeSlotBody(String date, String start, String end) {

        static TimeSlotBody from(TimeSlot slot) {
            return new TimeSlotBody(slot.date().toString(), slot.start().format(HOUR), slot.end().format(HOUR));
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    record FiltersBody(String category, String type, Integer minCapacity) {
    }

    record PaginationBody(int page, int pageSize, int totalPages, long total) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SanctionNoticeBody(String type, String reason, String until, String message) {

        static SanctionNoticeBody from(SanctionNotice notice) {
            return new SanctionNoticeBody(notice.type().name(), notice.reason(),
                    notice.until() == null ? null : notice.until().toString(), notice.message());
        }
    }

    /** Un solo objeto para espacios y activos: {@code category} dice qué atributos esperar. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ResourceBody(
            String id,
            String name,
            String category,
            String type,
            String location,
            String faculty,
            Integer maxCapacity,
            List<String> equipment,
            String condition,
            Integer loanTermBusinessDays,
            String status,
            boolean selectable,
            String reason,
            String occupiedUntil) {

        static ResourceBody from(ResourceWithStatus item) {
            var resource = item.resource();
            return new ResourceBody(resource.id(), resource.name(), resource.category().name(), resource.type(),
                    resource.location(), resource.faculty(), resource.maxCapacity(), resource.equipment(),
                    resource.condition(), resource.loanTermBusinessDays(), item.status().name(),
                    item.selectable(), item.reason(),
                    item.occupiedUntil() == null ? null : offsetDateTime(item.occupiedUntil()));
        }
    }
}
