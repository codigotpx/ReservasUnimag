package edu.unimagdalena.reservasunimag.domain.usecase.checkavailability;

import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException.ExternalModule;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Occupancy;
import edu.unimagdalena.reservasunimag.domain.model.reservation.OccupancyType;
import edu.unimagdalena.reservasunimag.domain.model.resource.DisplayedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceWithStatus;
import edu.unimagdalena.reservasunimag.support.ResourceFixtures.InMemoryOccupancies;
import org.junit.jupiter.api.Test;

import java.util.List;

import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.CLOCK;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.TEN_TO_TWELVE;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.asset;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.at;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.occupancy;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.slot;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.space;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckAvailabilityUseCaseTest {

    private static List<DisplayedStatus> statuses(List<ResourceWithStatus> result) {
        return result.stream().map(ResourceWithStatus::status).toList();
    }

    @Test
    void givesEachResourceItsOwnStatusInOneQuery() {
        var occupancies = new InMemoryOccupancies(
                occupancy("LAB", OccupancyType.ACADEMIC_BLOCK, 10, 12),
                occupancy("SALA", OccupancyType.SPACE_RESERVATION, 11, 13),
                occupancy("OTRA", OccupancyType.SPACE_RESERVATION, 14, 16));
        var useCase = new CheckAvailabilityUseCase(occupancies, CLOCK);

        List<ResourceWithStatus> result = useCase.checkAvailability(List.of(
                space("LAB", "Laboratorio", 30),
                space("SALA", "Sala", 8),
                space("OTRA", "Otra", 8),
                space("MANT", "En mantenimiento", 8, OperationalStatus.EN_MANTENIMIENTO),
                asset("ACT", "Microscopio")), TEN_TO_TWELVE);

        assertThat(statuses(result)).containsExactly(DisplayedStatus.BLOQUEO_ACADEMICO, DisplayedStatus.RESERVADO,
                DisplayedStatus.DISPONIBLE, DisplayedStatus.EN_MANTENIMIENTO, DisplayedStatus.DISPONIBLE);
        assertThat(occupancies.queries).isEqualTo(1);
    }

    @Test
    void keepsTheOrderOfTheResourcesItReceives() {
        var useCase = new CheckAvailabilityUseCase(new InMemoryOccupancies(), CLOCK);

        List<ResourceWithStatus> result = useCase.checkAvailability(
                List.of(space("B", "B", 5), space("A", "A", 5)), TEN_TO_TWELVE);

        assertThat(result).extracting(item -> item.resource().id()).containsExactly("B", "A");
    }

    @Test
    void aPickedUpLoanThatIsOverdueKeepsTheAssetInUse() {
        var overdue = new Occupancy("ACT", OccupancyType.LOAN_PICKED_UP, at(8, 0).minusSeconds(3 * 86_400), null);
        var useCase = new CheckAvailabilityUseCase(new InMemoryOccupancies(overdue), CLOCK);

        List<ResourceWithStatus> result = useCase.checkAvailability(List.of(asset("ACT", "Microscopio")),
                slot(14, 16));

        assertThat(statuses(result)).containsExactly(DisplayedStatus.EN_USO);
    }

    @Test
    void inventoryReportingInUseCountsOnlyWhenTheSlotIncludesNow() {
        var useCase = new CheckAvailabilityUseCase(new InMemoryOccupancies(), CLOCK);
        Resource inUse = space("USO", "En uso", 8, OperationalStatus.EN_USO);

        assertThat(statuses(useCase.checkAvailability(List.of(inUse), slot(9, 11))))
                .containsExactly(DisplayedStatus.EN_USO);
        assertThat(statuses(useCase.checkAvailability(List.of(inUse), slot(14, 16))))
                .containsExactly(DisplayedStatus.DISPONIBLE);
    }

    @Test
    void doesNotQueryAnythingWhenThereAreNoResources() {
        var occupancies = new InMemoryOccupancies();

        assertThat(new CheckAvailabilityUseCase(occupancies, CLOCK).checkAvailability(List.of(), TEN_TO_TWELVE))
                .isEmpty();
        assertThat(occupancies.queries).isZero();
    }

    @Test
    void neverReportsAvailableWhenTheOccupancyStoreFails() {
        var useCase = new CheckAvailabilityUseCase((ids, from, to) -> {
            throw new ExternalServiceUnavailableException(ExternalModule.MODULO_1, "caído", null);
        }, CLOCK);

        assertThatThrownBy(() -> useCase.checkAvailability(List.of(space("A", "A", 5)), TEN_TO_TWELVE))
                .isInstanceOf(ExternalServiceUnavailableException.class);
    }
}
