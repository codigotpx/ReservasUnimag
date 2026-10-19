package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.resource;

import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceFilter;
import edu.unimagdalena.reservasunimag.domain.model.resource.SearchResourcesQuery;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.port.in.SearchResourcesPort;
import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;

/** {@code GET /api/resources}: UC1 Contratos §1. Lo mostrado es la foto del momento, así que va {@code no-store}. */
@RestController
@RequestMapping("/api/resources")
class ResourceController {

    private final SearchResourcesPort searchResources;
    private final int pageSize;

    ResourceController(SearchResourcesPort searchResources, ReservationProperties properties) {
        this.searchResources = searchResources;
        this.pageSize = properties.pageSize();
    }

    @GetMapping
    ResponseEntity<SearchResourcesResponse> search(
            CurrentUser currentUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(pattern = "HH:mm") LocalTime start,
            @RequestParam @DateTimeFormat(pattern = "HH:mm") LocalTime end,
            @RequestParam(required = false) ResourceCategory category,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @Min(value = 1, message = "debe ser mayor o igual a 1") Integer minCapacity,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "debe ser mayor o igual a 1") int page) {
        var query = new SearchResourcesQuery(currentUser, new TimeSlot(date, start, end),
                new ResourceFilter(category, type, minCapacity), new PageRequest(page, pageSize));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SearchResourcesResponse.from(searchResources.search(query)));
    }
}
