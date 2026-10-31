package edu.unimagdalena.reservasunimag.domain.usecase.resourceoccupancy;

import edu.unimagdalena.reservasunimag.domain.model.error.InvalidFilterException;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ScheduledUse;
import edu.unimagdalena.reservasunimag.domain.port.out.ScheduledUseRepositoryPort;
import edu.unimagdalena.reservasunimag.support.InMemoryInventory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ViewResourceOccupancyUseCaseTest {

    private static final Resource LAB = Resource.space("ESP-0412", "Laboratorio de Redes", "LABORATORIO",
            "Bloque 7, piso 2", OperationalStatus.DISPONIBLE, "Ingeniería", 30, List.of());
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    private final List<Instant[]> asked = new ArrayList<>();
    private final ScheduledUse lecture = new ScheduledUse(ScheduledUse.Kind.CLASS,
            Instant.parse("2026-11-02T13:00:00Z"), Instant.parse("2026-11-02T15:00:00Z"), "Redes", "Sistemas");
    private final ScheduledUseRepositoryPort uses = (resourceId, from, to) -> {
        asked.add(new Instant[] {from, to});
        return "ESP-0412".equals(resourceId) ? List.of(lecture) : List.of();
    };
    private final ViewResourceOccupancyUseCase useCase = new ViewResourceOccupancyUseCase(new InMemoryInventory(LAB),
            uses);

    @Test
    void aWeekGoesFromMondayAtMidnightToSaturdayAtMidnightInBogota() {
        var occupancy = useCase.view("ESP-0412", MONDAY, MONDAY.plusDays(4));

        assertThat(occupancy.resource()).isEqualTo(LAB);
        assertThat(occupancy.uses()).containsExactly(lecture);
        assertThat(asked).singleElement().satisfies(range -> {
            assertThat(range[0]).isEqualTo(ZonedDateTime.of(2026, 11, 2, 0, 0, 0, 0, TimeSlot.ZONE).toInstant());
            assertThat(range[1]).isEqualTo(ZonedDateTime.of(2026, 11, 7, 0, 0, 0, 0, TimeSlot.ZONE).toInstant());
        });
    }

    @Test
    void aBackwardsOrTooLongRangeIsRejectedBeforeAskingAnyone() {
        assertThatThrownBy(() -> useCase.view("ESP-0412", MONDAY, MONDAY.minusDays(1)))
                .isInstanceOf(InvalidFilterException.class);
        assertThatThrownBy(() -> useCase.view("ESP-0412", MONDAY, MONDAY.plusDays(31)))
                .isInstanceOf(InvalidFilterException.class);
        assertThat(useCase.view("ESP-0412", MONDAY, MONDAY.plusDays(30)).uses()).hasSize(1);
        assertThat(asked).hasSize(1);
    }

    @Test
    void anUnknownResourceIsNotFound() {
        assertThatThrownBy(() -> useCase.view("ESP-9999", MONDAY, MONDAY))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(asked).isEmpty();
    }
}
