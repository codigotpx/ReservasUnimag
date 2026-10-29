package edu.unimagdalena.reservasunimag.domain.usecase.searchresources;

import edu.unimagdalena.reservasunimag.domain.model.reservation.OccupancyType;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.support.ResourceFixtures.InMemoryOccupancies;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.CLOCK;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.DAY;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.TEN_TO_TWELVE;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.occupancy;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.slot;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.space;
import static org.assertj.core.api.Assertions.assertThat;

class NextFreeSlotFinderTest {

    private static final Duration HALF_HOUR = Duration.ofMinutes(30);

    @Test
    void suggestsTheFirstSlotOfTheSameLengthWhereSomeResourceIsFree() {
        var occupancies = new InMemoryOccupancies(
                occupancy("L1", OccupancyType.ACADEMIC_BLOCK, 10, 12),
                occupancy("L1", OccupancyType.SPACE_RESERVATION, 12, 14),
                occupancy("L2", OccupancyType.ACADEMIC_BLOCK, 10, 15));
        var finder = new NextFreeSlotFinder(occupancies, CLOCK, HALF_HOUR);

        var next = finder.find(List.of(space("L1", "Lab 1", 30), space("L2", "Lab 2", 30)), TEN_TO_TWELVE);

        assertThat(next).contains(new TimeSlot(DAY, LocalTime.of(14, 0), LocalTime.of(16, 0)));
    }

    @Test
    void movesInHalfHourStepsFromTheEndOfTheQueriedSlot() {
        var occupancies = new InMemoryOccupancies(occupancy("L1", OccupancyType.SPACE_RESERVATION, 12, 13));
        var finder = new NextFreeSlotFinder(occupancies, CLOCK, HALF_HOUR);

        var next = finder.find(List.of(space("L1", "Lab 1", 30)), slot(10, 12));

        assertThat(next).contains(new TimeSlot(DAY, LocalTime.of(13, 0), LocalTime.of(15, 0)));
    }

    @Test
    void usesASingleOccupancyQueryForTheRestOfTheDay() {
        var occupancies = new InMemoryOccupancies();

        new NextFreeSlotFinder(occupancies, CLOCK, HALF_HOUR).find(List.of(space("L1", "Lab 1", 30)), TEN_TO_TWELVE);

        assertThat(occupancies.queries).isEqualTo(1);
    }

    @Test
    void answersNothingWhenThereIsNoFreeSlotUntilClosing() {
        var occupancies = new InMemoryOccupancies(occupancy("L1", OccupancyType.ACADEMIC_BLOCK, 12, 22));
        var finder = new NextFreeSlotFinder(occupancies, CLOCK, HALF_HOUR);

        assertThat(finder.find(List.of(space("L1", "Lab 1", 30)), TEN_TO_TWELVE)).isEmpty();
    }

    @Test
    void aSlotThatWouldEndAfterClosingIsNotSuggested() {
        var finder = new NextFreeSlotFinder(new InMemoryOccupancies(), CLOCK, HALF_HOUR);

        assertThat(finder.find(List.of(space("L1", "Lab 1", 30)), slot(19, 21))).isEmpty();
        assertThat(finder.find(List.of(space("L1", "Lab 1", 30)), slot(18, 20)))
                .contains(new TimeSlot(DAY, LocalTime.of(20, 0), LocalTime.of(22, 0)));
    }

    @Test
    void neverSuggestsASlotForAResourceInMaintenance() {
        var finder = new NextFreeSlotFinder(new InMemoryOccupancies(), CLOCK, HALF_HOUR);

        var next = finder.find(List.of(space("L1", "Lab 1", 30, OperationalStatus.EN_MANTENIMIENTO)), TEN_TO_TWELVE);

        assertThat(next).isEmpty();
    }
}
