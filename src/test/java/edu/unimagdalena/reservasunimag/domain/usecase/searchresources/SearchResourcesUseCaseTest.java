package edu.unimagdalena.reservasunimag.domain.usecase.searchresources;

import edu.unimagdalena.reservasunimag.support.InMemorySanctionChecks;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionScope;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.compliance.Sanction;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionNotice;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException.ExternalModule;
import edu.unimagdalena.reservasunimag.domain.model.reservation.OccupancyType;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.Catalog;
import edu.unimagdalena.reservasunimag.domain.model.resource.DisplayedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceFilter;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceWithStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.SearchResourcesQuery;
import edu.unimagdalena.reservasunimag.domain.model.resource.SearchResourcesResult;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.port.out.CompliancePort;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import edu.unimagdalena.reservasunimag.domain.usecase.checkavailability.CheckAvailabilityUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.checksanctions.CheckSanctionsUseCase;
import edu.unimagdalena.reservasunimag.support.ResourceFixtures.InMemoryOccupancies;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.CLOCK;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.DIRECTOR;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.MONITOR;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.NOW;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.STUDENT;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.TEN_TO_TWELVE;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.asset;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.occupancy;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.space;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchResourcesUseCaseTest {

    private static final ResourceFilter NO_FILTER = new ResourceFilter(null, null, null);
    private static final PageRequest FIRST_PAGE = new PageRequest(1, 20);

    private List<Resource> catalog = new ArrayList<>();
    private boolean truncated;
    private boolean inventoryDown;
    private Sanction sanction;
    private boolean complianceDown;
    private InMemoryOccupancies occupancies = new InMemoryOccupancies();

    private final InventoryPort inventory = new InventoryPort() {
        @Override
        public Catalog searchCatalog(ResourceFilter filter) {
            if (inventoryDown) {
                throw new ExternalServiceUnavailableException(ExternalModule.MODULO_1, "caído", null);
            }
            return new Catalog(catalog, truncated);
        }

        @Override
        public java.util.Optional<Resource> details(String resourceId) {
            throw new UnsupportedOperationException("UC1 no la usa");
        }

        @Override
        public Map<String, OperationalStatus> operationalStatus(Collection<String> resourceIds) {
            throw new UnsupportedOperationException("UC1 no la usa");
        }
    };

    private final CompliancePort compliance = personCode -> {
        if (complianceDown) {
            throw new ExternalServiceUnavailableException(ExternalModule.MODULO_3, "caído", null);
        }
        return new edu.unimagdalena.reservasunimag.domain.model.compliance.ComplianceReport(
                sanction == null ? List.of() : List.of(sanction), 0, 0, NOW);
    };

    private SearchResourcesResult search(CurrentUser user, ResourceFilter filter, PageRequest page) {
        var availability = new CheckAvailabilityUseCase(inventory, occupancies, CLOCK);
        var useCase = new SearchResourcesUseCase(inventory, availability, new CheckSanctionsUseCase(compliance, new InMemorySanctionChecks(), CLOCK),
                new NextFreeSlotFinder(occupancies, CLOCK, Duration.ofMinutes(30)), CLOCK);
        return useCase.search(new SearchResourcesQuery(user, TEN_TO_TWELVE, filter, page));
    }

    private SearchResourcesResult search() {
        return search(STUDENT, NO_FILTER, FIRST_PAGE);
    }

    private static List<String> ids(SearchResourcesResult result) {
        return result.page().items().stream().map(item -> item.resource().id()).toList();
    }

    @Test
    void sortsByNameThenIdRegardlessOfCategoryOrStatus() {
        catalog.addAll(List.of(space("ESP-2", "Sala", 8), space("ESP-1", "Sala", 8),
                asset("ACT-9", "Microscopio"), space("ESP-0", "Laboratorio", 30, OperationalStatus.EN_MANTENIMIENTO)));

        assertThat(ids(search())).containsExactly("ESP-0", "ACT-9", "ESP-1", "ESP-2");
    }

    @Test
    void showsEveryMatchingResourceWithItsStatusAndOnlyAvailableOnesAreSelectable() {
        catalog.addAll(List.of(space("A", "A", 8), space("B", "B", 8), space("C", "C", 8,
                OperationalStatus.EN_MANTENIMIENTO)));
        occupancies = new InMemoryOccupancies(occupancy("B", OccupancyType.SPACE_RESERVATION, 11, 13));

        SearchResourcesResult result = search();

        assertThat(result.page().items()).extracting(ResourceWithStatus::status)
                .containsExactly(DisplayedStatus.DISPONIBLE, DisplayedStatus.RESERVADO,
                        DisplayedStatus.EN_MANTENIMIENTO);
        assertThat(result.page().items()).extracting(ResourceWithStatus::selectable)
                .containsExactly(true, false, false);
        assertThat(result.anySelectable()).isTrue();
        assertThat(result.message()).isNull();
        assertThat(result.nextTimeSlot()).isNull();
    }

    @Test
    void pagesFortySevenResourcesAsTwentyTwentyAndSevenWithoutRepeating() {
        IntStream.range(0, 47).forEach(i -> catalog.add(space("ESP-%02d".formatted(i), "Sala %02d".formatted(i), 8)));

        var first = search(STUDENT, NO_FILTER, new PageRequest(1, 20));
        var second = search(STUDENT, NO_FILTER, new PageRequest(2, 20));
        var third = search(STUDENT, NO_FILTER, new PageRequest(3, 20));

        assertThat(first.page().items()).hasSize(20);
        assertThat(second.page().items()).hasSize(20);
        assertThat(third.page().items()).hasSize(7);
        assertThat(first.page().total()).isEqualTo(47);
        assertThat(first.page().totalPages()).isEqualTo(3);
        List<String> all = new ArrayList<>(ids(first));
        all.addAll(ids(second));
        all.addAll(ids(third));
        assertThat(all).doesNotHaveDuplicates().hasSize(47);
    }

    @Test
    void twentyOrFewerResourcesFitInASinglePage() {
        IntStream.range(0, 20).forEach(i -> catalog.add(space("ESP-%02d".formatted(i), "Sala %02d".formatted(i), 8)));

        var result = search();

        assertThat(result.page().items()).hasSize(20);
        assertThat(result.page().totalPages()).isEqualTo(1);
    }

    @Test
    void appliesTheMinimumCapacityFilterEvenIfInventoryDidNot() {
        catalog.addAll(List.of(space("S", "Pequeña", 10), space("G", "Grande", 40)));

        var result = search(STUDENT, new ResourceFilter(ResourceCategory.ESPACIO, null, 20), FIRST_PAGE);

        assertThat(ids(result)).containsExactly("G");
        assertThat(result.page().total()).isEqualTo(1);
    }

    @Test
    void whenNothingIsSelectableTheWholeListStillComesWithAMessageAndTheNextFreeSlot() {
        catalog.addAll(List.of(space("L1", "Lab 1", 30), space("L2", "Lab 2", 30)));
        occupancies = new InMemoryOccupancies(
                occupancy("L1", OccupancyType.ACADEMIC_BLOCK, 10, 12),
                occupancy("L2", OccupancyType.ACADEMIC_BLOCK, 10, 14));

        var result = search();

        assertThat(result.anySelectable()).isFalse();
        assertThat(result.page().items()).hasSize(2);
        assertThat(result.message())
                .isEqualTo("Ninguno de los 2 recursos que cumplen los filtros está disponible entre las 10:00 y las 12:00.");
        assertThat(result.nextTimeSlot()).isEqualTo(
                new TimeSlot(LocalDate.of(2026, 9, 1), java.time.LocalTime.of(12, 0), java.time.LocalTime.of(14, 0)));
    }

    @Test
    void whenThereIsNoFreeSlotLeftTheMessageSaysSoAndThereIsNoSuggestion() {
        catalog.add(space("L1", "Lab 1", 30));
        occupancies = new InMemoryOccupancies(occupancy("L1", OccupancyType.ACADEMIC_BLOCK, 10, 22));

        var result = search();

        assertThat(result.nextTimeSlot()).isNull();
        assertThat(result.message()).isEqualTo(
                "El único recurso que cumple los filtros no está disponible entre las 10:00 y las 22:00 de hoy.");
    }

    @Test
    void anEmptyCatalogIsNotAnErrorAndNeverSuggestsASlot() {
        var result = search();

        assertThat(result.page().items()).isEmpty();
        assertThat(result.page().total()).isZero();
        assertThat(result.page().totalPages()).isEqualTo(1);
        assertThat(result.anySelectable()).isFalse();
        assertThat(result.message()).isEqualTo("Ningún recurso cumple los filtros.");
        assertThat(result.nextTimeSlot()).isNull();
    }

    @Test
    void passesTheTruncationOfTheCatalogAlong() {
        catalog.add(space("A", "A", 8));
        truncated = true;

        assertThat(search().catalogTruncated()).isTrue();
    }

    @Test
    void studentsAndMonitorsGetTheSanctionNoticeAndTheListStillShows() {
        catalog.add(space("A", "A", 8));
        sanction = new Sanction("Dos ausencias", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), SanctionScope.TODO);

        for (CurrentUser user : List.of(STUDENT, MONITOR)) {
            var result = search(user, NO_FILTER, FIRST_PAGE);

            assertThat(result.sanctionNotice().type()).isEqualTo(SanctionNotice.Type.ACTIVE_SANCTION);
            assertThat(result.sanctionNotice().until()).isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(result.page().items()).hasSize(1);
        }
    }

    @Test
    void theProgramDirectionGetsNoNoticeAndComplianceIsNotEvenAsked() {
        catalog.add(space("A", "A", 8));
        sanction = new Sanction("Dos ausencias", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), SanctionScope.TODO);
        complianceDown = true;

        assertThat(search(DIRECTOR, NO_FILTER, FIRST_PAGE).sanctionNotice()).isNull();
    }

    @Test
    void aPersonInGoodStandingGetsNoNotice() {
        catalog.add(space("A", "A", 8));

        assertThat(search().sanctionNotice()).isNull();
    }

    @Test
    void whenComplianceIsDownTheSearchStillWorksWithANotVerifiedNotice() {
        catalog.add(space("A", "A", 8));
        complianceDown = true;

        var result = search();

        assertThat(result.sanctionNotice().type()).isEqualTo(SanctionNotice.Type.NOT_VERIFIED);
        assertThat(result.page().items()).hasSize(1);
    }

    @Test
    void whenInventoryIsDownTheSearchFailsInsteadOfShowingAnythingStale() {
        inventoryDown = true;

        assertThatThrownBy(this::search).isInstanceOfSatisfying(ExternalServiceUnavailableException.class,
                exception -> assertThat(exception.module()).isEqualTo(ExternalModule.MODULO_1));
    }

    @Test
    void picksTheSanctionThatEndsLast() {
        catalog.add(space("A", "A", 8));
        var shortOne = new Sanction("Corta", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10), SanctionScope.TODO);
        var longOne = new Sanction("Larga", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 30), SanctionScope.TODO);
        var notice = new CheckSanctionsUseCase(personCode -> new edu.unimagdalena.reservasunimag.domain.model.compliance.ComplianceReport(
                List.of(shortOne, longOne), 0, 0, NOW), new InMemorySanctionChecks(), CLOCK).checkSanctions(STUDENT);

        assertThat(notice).get().extracting(SanctionNotice::reason).isEqualTo("Larga");
    }
}
