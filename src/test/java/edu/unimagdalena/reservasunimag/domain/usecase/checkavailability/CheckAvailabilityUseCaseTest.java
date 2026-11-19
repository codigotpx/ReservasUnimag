package edu.unimagdalena.reservasunimag.domain.usecase.checkavailability;

import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException.ExternalModule;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Occupancy;
import edu.unimagdalena.reservasunimag.domain.model.reservation.OccupancyType;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.AvailabilityAnswer;
import edu.unimagdalena.reservasunimag.domain.model.resource.DisplayedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceWithStatus;
import edu.unimagdalena.reservasunimag.support.InMemoryInventory;
import edu.unimagdalena.reservasunimag.support.ResourceFixtures.InMemoryOccupancies;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.CLOCK;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.DAY;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.NOW;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.TEN_TO_TWELVE;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.asset;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.at;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.occupancy;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.slot;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.space;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC8: la versión por lote, la individual y los seis escenarios del spec. */
class CheckAvailabilityUseCaseTest {

    private static final Resource STUDY_ROOM = space("ESP-0107", "Sala de Estudio 3", 8);
    private static final Resource ROOM_201 = space("ESP-0412", "Salón 201", 30);
    private static final Resource AUDITORIUM = space("ESP-0201", "Auditorio Menor", 120);
    private static final Resource VIDEOBEAM = new Resource("ACT-001200", "Videobeam 12",
            edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory.ACTIVO, "VIDEOBEAM", "Almacén",
            OperationalStatus.EN_MANTENIMIENTO, null, null, null, "BUENO", 3);
    private static final Resource BOOK = asset("ACT-004512", "Libro de Cálculo I");
    private static final Resource NETWORKS_LAB = space("ESP-0413", "Laboratorio de Redes", 30);

    private final InMemoryInventory inventory = new InMemoryInventory(STUDY_ROOM, ROOM_201, AUDITORIUM, VIDEOBEAM,
            BOOK, NETWORKS_LAB);
    private final InMemoryOccupancies occupancies = new InMemoryOccupancies();
    private final CheckAvailabilityUseCase useCase = new CheckAvailabilityUseCase(inventory, occupancies, CLOCK);

    private static List<DisplayedStatus> statuses(List<ResourceWithStatus> result) {
        return result.stream().map(ResourceWithStatus::status).toList();
    }

    private static Instant on(int dayOfMonth, int hour) {
        return LocalDate.of(2026, 9, dayOfMonth).atTime(hour, 0).atZone(TimeSlot.ZONE).toInstant();
    }

    // --- Versión por lote (UC1) -------------------------------------------------------------------------------

    @Test
    void givesEachResourceItsOwnStatusInOneQuery() {
        occupancies.add(occupancy("LAB", OccupancyType.ACADEMIC_BLOCK, 10, 12));
        occupancies.add(occupancy("SALA", OccupancyType.SPACE_RESERVATION, 11, 13));
        occupancies.add(occupancy("OTRA", OccupancyType.SPACE_RESERVATION, 14, 16));

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
        List<ResourceWithStatus> result = useCase.checkAvailability(
                List.of(space("B", "B", 5), space("A", "A", 5)), TEN_TO_TWELVE);

        assertThat(result).extracting(item -> item.resource().id()).containsExactly("B", "A");
    }

    @Test
    void aPickedUpLoanThatIsOverdueKeepsTheAssetInUse() {
        occupancies.add(new Occupancy("ACT", OccupancyType.LOAN_PICKED_UP, at(8, 0).minusSeconds(3 * 86_400), null,
                at(8, 0).minusSeconds(86_400)));

        List<ResourceWithStatus> result = useCase.checkAvailability(List.of(asset("ACT", "Microscopio")),
                slot(14, 16));

        assertThat(statuses(result)).containsExactly(DisplayedStatus.EN_USO);
    }

    @Test
    void inventoryReportingInUseCountsOnlyWhenTheSlotIncludesNow() {
        Resource inUse = space("USO", "En uso", 8, OperationalStatus.EN_USO);

        assertThat(statuses(useCase.checkAvailability(List.of(inUse), slot(9, 11))))
                .containsExactly(DisplayedStatus.EN_USO);
        assertThat(statuses(useCase.checkAvailability(List.of(inUse), slot(14, 16))))
                .containsExactly(DisplayedStatus.DISPONIBLE);
    }

    @Test
    void doesNotQueryAnythingWhenThereAreNoResources() {
        assertThat(useCase.checkAvailability(List.of(), TEN_TO_TWELVE)).isEmpty();
        assertThat(occupancies.queries).isZero();
    }

    @Test
    void neverReportsAvailableWhenTheOccupancyStoreFails() {
        var failing = new CheckAvailabilityUseCase(inventory, (ids, from, to) -> {
            throw new ExternalServiceUnavailableException(ExternalModule.MODULO_1, "caído", null);
        }, CLOCK);

        assertThatThrownBy(() -> failing.checkAvailability(List.of(space("A", "A", 5)), TEN_TO_TWELVE))
                .isInstanceOf(ExternalServiceUnavailableException.class);
    }

    @Test
    void theListAlsoSaysUntilWhenTheResourceIsCommitted() {
        occupancies.add(occupancy(ROOM_201.id(), OccupancyType.ACADEMIC_BLOCK, 8, 11));

        List<ResourceWithStatus> result = useCase.checkAvailability(List.of(ROOM_201, STUDY_ROOM), TEN_TO_TWELVE);

        assertThat(result.get(0).occupiedUntil()).isEqualTo(at(11, 0));
        assertThat(result.get(1).occupiedUntil()).isNull();
    }

    // --- Escenarios del spec, por la pregunta de un recurso -----------------------------------------------------

    @Test
    void scenario1_aFreeResourceIsAvailableWithoutReasonNorUntil() {
        AvailabilityAnswer answer = useCase.check(STUDY_ROOM.id(), TEN_TO_TWELVE);

        assertThat(answer.available()).isTrue();
        assertThat(answer.status()).isEqualTo(DisplayedStatus.DISPONIBLE);
        assertThat(answer.reason()).isNull();
        assertThat(answer.occupiedUntil()).isNull();
        assertThat(answer.answeredAt()).isEqualTo(NOW);
        assertThat(answer.slot()).isEqualTo(TEN_TO_TWELVE);
    }

    @Test
    void scenario2_aClassIsAnAcademicBlockUntilTheEndOfTheClass() {
        occupancies.add(occupancy(ROOM_201.id(), OccupancyType.ACADEMIC_BLOCK, 8, 10));

        AvailabilityAnswer answer = useCase.check(ROOM_201.id(), slot(8, 10));

        assertThat(answer.available()).isFalse();
        assertThat(answer.status()).isEqualTo(DisplayedStatus.BLOQUEO_ACADEMICO);
        assertThat(answer.reason()).isEqualTo(
                "El recurso está reservado para actividad docente en la franja consultada.");
        assertThat(answer.occupiedUntil()).isEqualTo(at(10, 0));
    }

    @Test
    void scenario3_aReservationByAnotherPersonSaysReservedAndNothingAboutWho() {
        occupancies.add(occupancy(AUDITORIUM.id(), OccupancyType.SPACE_RESERVATION, 14, 16));

        AvailabilityAnswer answer = useCase.check(AUDITORIUM.id(), slot(14, 16));

        assertThat(answer.status()).isEqualTo(DisplayedStatus.RESERVADO);
        assertThat(answer.reason()).isEqualTo("El recurso ya está reservado en la franja consultada.");
        assertThat(answer.occupiedUntil()).isEqualTo(at(16, 0));
        // La respuesta no tiene dónde llevar al titular, y el texto tampoco lo menciona (FR-005).
        assertThat(AvailabilityAnswer.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("resource", "slot", "availability", "answeredAt");
    }

    @Test
    void scenario4_maintenanceBlocksAnySlotAndPromisesNoDate() {
        AvailabilityAnswer answer = useCase.check(VIDEOBEAM.id(), slot(18, 20));

        assertThat(answer.status()).isEqualTo(DisplayedStatus.EN_MANTENIMIENTO);
        assertThat(answer.reason()).isEqualTo("El recurso está en mantenimiento.");
        assertThat(answer.occupiedUntil()).isNull();
    }

    @Test
    void scenario5_anAssetOnLoanSaysInUseUntilTheDueDate() {
        occupancies.add(new Occupancy(BOOK.id(), OccupancyType.LOAN_PICKED_UP, on(1, 8), null, on(10, 22)));

        AvailabilityAnswer answer = useCase.check(BOOK.id(),
                new TimeSlot(LocalDate.of(2026, 9, 3), LocalTime.of(10, 0), LocalTime.of(12, 0)));

        assertThat(answer.status()).isEqualTo(DisplayedStatus.EN_USO);
        assertThat(answer.reason()).isEqualTo("El recurso está en uso en la franja consultada.");
        assertThat(answer.occupiedUntil()).isEqualTo(on(10, 22));
    }

    @Test
    void scenario6_halfAnHourOfOverlapIsEnough() {
        occupancies.add(occupancy(NETWORKS_LAB.id(), OccupancyType.ACADEMIC_BLOCK, 8, 10));

        AvailabilityAnswer answer = useCase.check(NETWORKS_LAB.id(),
                new TimeSlot(DAY, LocalTime.of(9, 30), LocalTime.of(10, 30)));

        assertThat(answer.available()).isFalse();
        assertThat(answer.status()).isEqualTo(DisplayedStatus.BLOQUEO_ACADEMICO);
    }

    // --- FR-012: el "hasta cuándo" y su borde -------------------------------------------------------------------

    @Test
    void anOverdueLoanOmitsTheUntilAndExplainsItInTheReason() {
        occupancies.add(new Occupancy(BOOK.id(), OccupancyType.LOAN_PICKED_UP, on(1, 8).minusSeconds(10 * 86_400),
                null, on(1, 8).minusSeconds(86_400)));

        AvailabilityAnswer answer = useCase.check(BOOK.id(), TEN_TO_TWELVE);

        assertThat(answer.status()).isEqualTo(DisplayedStatus.EN_USO);
        assertThat(answer.occupiedUntil()).isNull();
        assertThat(answer.reason()).isEqualTo("El recurso está en uso y su plazo de préstamo ya venció.");
    }

    @Test
    void aHeldLoanThatWasNotPickedUpIsReservedUntilItsDueDate() {
        occupancies.add(new Occupancy(BOOK.id(), OccupancyType.LOAN_HELD, at(10, 0), on(4, 12)));

        AvailabilityAnswer answer = useCase.check(BOOK.id(), TEN_TO_TWELVE);

        assertThat(answer.status()).isEqualTo(DisplayedStatus.RESERVADO);
        assertThat(answer.occupiedUntil()).isEqualTo(on(4, 12));
    }

    @Test
    void theUntilIsTheLatestEndAmongTheOccupanciesThatDecide() {
        occupancies.add(occupancy(ROOM_201.id(), OccupancyType.SPACE_RESERVATION, 10, 11));
        occupancies.add(occupancy(ROOM_201.id(), OccupancyType.SPACE_RESERVATION, 11, 12));

        assertThat(useCase.check(ROOM_201.id(), TEN_TO_TWELVE).occupiedUntil()).isEqualTo(at(12, 0));
    }

    @Test
    void theUntilComesFromTheOccupancyThatDecidesNotFromAWeakerOne() {
        occupancies.add(occupancy(ROOM_201.id(), OccupancyType.SPACE_RESERVATION, 10, 13));
        occupancies.add(occupancy(ROOM_201.id(), OccupancyType.ACADEMIC_BLOCK, 11, 12));

        AvailabilityAnswer answer = useCase.check(ROOM_201.id(), TEN_TO_TWELVE);

        assertThat(answer.status()).isEqualTo(DisplayedStatus.BLOQUEO_ACADEMICO);
        assertThat(answer.occupiedUntil()).isEqualTo(at(12, 0));
    }

    @Test
    void inUseReportedOnlyByModule1PromisesNoDate() {
        var inUse = space("ESP-0108", "Sala de Estudio 4", 8, OperationalStatus.EN_USO);
        var withInUse = new CheckAvailabilityUseCase(new InMemoryInventory(inUse), occupancies, CLOCK);

        AvailabilityAnswer answer = withInUse.check(inUse.id(), slot(9, 11));

        assertThat(answer.status()).isEqualTo(DisplayedStatus.EN_USO);
        assertThat(answer.occupiedUntil()).isNull();
    }

    // --- FR-004 / SC-002: ninguna negativa sin motivo ----------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = DisplayedStatus.class, names = "DISPONIBLE", mode = EnumSource.Mode.EXCLUDE)
    void everyStatusThatBlocksCarriesAReason(DisplayedStatus status) {
        assertThat(status.selectable()).isFalse();
        assertThat(status.reason()).isNotBlank();
    }

    @Test
    void noNegativeAnswerLeavesWithoutReason() {
        occupancies.add(occupancy(ROOM_201.id(), OccupancyType.ACADEMIC_BLOCK, 10, 12));
        occupancies.add(occupancy(AUDITORIUM.id(), OccupancyType.SPACE_RESERVATION, 10, 12));
        occupancies.add(new Occupancy(BOOK.id(), OccupancyType.LOAN_PICKED_UP, at(8, 0), null, on(5, 12)));

        for (String id : List.of(ROOM_201.id(), AUDITORIUM.id(), BOOK.id(), VIDEOBEAM.id())) {
            AvailabilityAnswer answer = useCase.check(id, TEN_TO_TWELVE);
            assertThat(answer.available()).as(id).isFalse();
            assertThat(answer.reason()).as(id).isNotBlank();
        }
    }

    // --- FR-007: si el Módulo 1 calla, nunca "disponible" --------------------------------------------------------

    @Test
    void whenModule1IsDownTheSingleQuestionFailsInsteadOfSayingFree() {
        inventory.down = true;

        assertThatThrownBy(() -> useCase.check(STUDY_ROOM.id(), TEN_TO_TWELVE))
                .isInstanceOf(ExternalServiceUnavailableException.class);
        assertThat(occupancies.queries).isZero();
    }

    @Test
    void anUnknownResourceIsNotFoundAndNotOccupied() {
        assertThatThrownBy(() -> useCase.check("ESP-9999", TEN_TO_TWELVE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- FR-006: sin reutilizar respuestas ---------------------------------------------------------------------

    @Test
    void twoQuestionsInARowSeeAReservationMadeBetweenThem() {
        AvailabilityAnswer before = useCase.check(STUDY_ROOM.id(), TEN_TO_TWELVE);
        occupancies.add(occupancy(STUDY_ROOM.id(), OccupancyType.SPACE_RESERVATION, 10, 12));
        AvailabilityAnswer after = useCase.check(STUDY_ROOM.id(), TEN_TO_TWELVE);

        assertThat(before.available()).isTrue();
        assertThat(after.available()).isFalse();
        assertThat(inventory.detailCalls).isEqualTo(2);
    }

    // --- Una sola regla ----------------------------------------------------------------------------------------

    @Test
    void theListAndTheSingleQuestionAgreeResourceByResource() {
        occupancies.add(occupancy(ROOM_201.id(), OccupancyType.ACADEMIC_BLOCK, 9, 11));
        occupancies.add(occupancy(AUDITORIUM.id(), OccupancyType.SPACE_RESERVATION, 11, 12));
        occupancies.add(new Occupancy(BOOK.id(), OccupancyType.LOAN_PICKED_UP, at(8, 0), null, on(5, 12)));
        List<Resource> all = List.of(STUDY_ROOM, ROOM_201, AUDITORIUM, VIDEOBEAM, BOOK, NETWORKS_LAB);

        List<ResourceWithStatus> list = useCase.checkAvailability(all, TEN_TO_TWELVE);

        for (ResourceWithStatus item : list) {
            AvailabilityAnswer single = useCase.check(item.resource().id(), TEN_TO_TWELVE);
            assertThat(single.availability()).as(item.resource().id()).isEqualTo(item.availability());
        }
    }
}
