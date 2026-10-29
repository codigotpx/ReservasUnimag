package edu.unimagdalena.reservasunimag.domain.usecase.reserveresources;

import edu.unimagdalena.reservasunimag.domain.model.compliance.ComplianceReport;
import edu.unimagdalena.reservasunimag.domain.model.compliance.Sanction;
import edu.unimagdalena.reservasunimag.domain.model.error.DenialCode;
import edu.unimagdalena.reservasunimag.domain.model.error.DurationExceededException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException.ExternalModule;
import edu.unimagdalena.reservasunimag.domain.model.error.IncompleteResourceDetailsException;
import edu.unimagdalena.reservasunimag.domain.model.error.InvalidTimeSlotException;
import edu.unimagdalena.reservasunimag.domain.model.error.NotAnAssetException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationDeniedException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationShapeException;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceTakenException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Cancellation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Denial;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.OccupancyType;
import edu.unimagdalena.reservasunimag.domain.model.reservation.QuotaUsage;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecord;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationResult;
import edu.unimagdalena.reservasunimag.domain.model.resource.Catalog;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceFilter;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.model.user.User;
import edu.unimagdalena.reservasunimag.domain.port.out.CompliancePort;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Module3NotifierPort;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.usecase.checkavailability.CheckAvailabilityUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.checksanctions.CheckSanctionsUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.reportreservation.ReportReservationUseCase;
import edu.unimagdalena.reservasunimag.support.ResourceFixtures.InMemoryOccupancies;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.CLOCK;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.DAY;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.NOW;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.STUDENT;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.asset;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.at;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.occupancy;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.space;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReserveResourcesUseCaseTest {

    private static final LocalTime TEN = LocalTime.of(10, 0);
    private static final LocalTime TWELVE = LocalTime.of(12, 0);

    // --- puertos falsos, con el estado que cada prueba necesita ---
    private final Map<String, Resource> catalog = new HashMap<>();
    private boolean inventoryDown;
    private final List<Sanction> sanctionsOfStudent = new ArrayList<>();
    private boolean complianceDown;
    private int complianceCalls;
    private final InMemoryOccupancies occupancies = new InMemoryOccupancies();
    private final List<Reservation> stored = new ArrayList<>();
    private boolean nextInsertLosesTheRace;
    private final List<Denial> denials = new ArrayList<>();
    private final List<ReservationRecord> reported = new ArrayList<>();
    private int transactions;

    private final InventoryPort inventory = new InventoryPort() {
        @Override
        public Catalog searchCatalog(ResourceFilter filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Resource> details(String resourceId) {
            if (inventoryDown) {
                throw new ExternalServiceUnavailableException(ExternalModule.MODULO_1, "caído", null);
            }
            return Optional.ofNullable(catalog.get(resourceId));
        }

        @Override
        public Map<String, OperationalStatus> operationalStatus(Collection<String> resourceIds) {
            throw new UnsupportedOperationException();
        }
    };

    private final CompliancePort compliance = personCode -> {
        complianceCalls++;
        if (complianceDown) {
            throw new ExternalServiceUnavailableException(ExternalModule.MODULO_3, "caído", null);
        }
        return new ComplianceReport(sanctionsOfStudent, 0, 0, NOW);
    };

    private final ReservationRepositoryPort reservations = new ReservationRepositoryPort() {
        @Override
        public void lockHolder(UUID userId) {
        }

        @Override
        public QuotaUsage quotaOf(UUID userId, Instant now) {
            List<Instant> ends = stored.stream()
                    .filter(r -> r.status().name().equals("CONFIRMADA") && userId.equals(r.userId()))
                    .filter(r -> r.category() == ResourceCategory.ESPACIO ? r.endsAt().isAfter(now)
                            : !r.loan().returned())
                    .map(Reservation::endsAt).sorted().toList();
            if (ends.isEmpty()) {
                return new QuotaUsage(0, null, false);
            }
            return ends.get(0).isAfter(now) ? new QuotaUsage(ends.size(), ends.get(0), false)
                    : new QuotaUsage(ends.size(), null, true);
        }

        @Override
        public void insert(Reservation reservation) {
            if (nextInsertLosesTheRace) {
                throw new ResourceTakenException(reservation.resourceId(), null);
            }
            stored.add(reservation);
        }

        @Override
        public List<Reservation> findByHolder(UUID userId, boolean onlyActive, Instant now) {
            return stored;
        }

        @Override
        public Optional<Reservation> findById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Reservation> lockById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Cancellation> findCancellation(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markCancelled(UUID id, Cancellation cancellation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Reservation> findUpcomingByResource(String resourceId, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markFinished(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markReturned(UUID id, Instant returnedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean markUseStarted(UUID id, Instant at) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Reservation> findAcademicBlocksToStart(Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Reservation> findStudentOccupying(Collection<String> resourceIds, Instant from, Instant to) {
            throw new UnsupportedOperationException();
        }
    };

    private final edu.unimagdalena.reservasunimag.support.RecordingStatusUpdates statusUpdates =
            new edu.unimagdalena.reservasunimag.support.RecordingStatusUpdates();

    private final edu.unimagdalena.reservasunimag.support.RecordFakes.InMemoryReservationRecords records =
            new edu.unimagdalena.reservasunimag.support.RecordFakes.InMemoryReservationRecords();

    private final Module3NotifierPort notifier = new Module3NotifierPort() {
        @Override
        public void reservationCreated(ReservationRecord record) {
            reported.add(record);
        }

        @Override
        public void reservationCancelled(CancellationReport report) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void noShowAcknowledged(edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowAck ack) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void checkOutAcknowledged(edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutAck ack) {
            throw new UnsupportedOperationException();
        }
    };

    private final ReserveResourcesUseCase useCase = new ReserveResourcesUseCase(
            inventory,
            new CheckSanctionsUseCase(compliance),
            new CheckAvailabilityUseCase(occupancies, CLOCK),
            reservations,
            denials::add,
            new FixedUsers(),
            new ReportReservationUseCase(records, notifier, CLOCK),
            statusUpdates,
            new DueDateCalculator(date -> date.getDayOfWeek() != DayOfWeek.SATURDAY
                    && date.getDayOfWeek() != DayOfWeek.SUNDAY),
            new CountingTransactions(),
            CLOCK, 3, Duration.ofHours(2));

    private final class FixedUsers implements edu.unimagdalena.reservasunimag.domain.port.out.UserRepositoryPort {
        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findById(UUID id) {
            return Optional.of(new User(STUDENT.id(), STUDENT.code(), "Estudiante de Prueba", "e@unimag.edu.co",
                    "hash", Role.ESTUDIANTE, true, NOW));
        }
    }

    private final class CountingTransactions implements edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort {
        @Override
        public <T> T required(Supplier<T> work) {
            transactions++;
            return work.get();
        }
    }

    private ReservationRequest spaceRequest(String id, int fromHour, int toHour) {
        return ReservationRequest.forSpace(STUDENT, id, DAY, LocalTime.of(fromHour, 0), LocalTime.of(toHour, 0));
    }

    private void store(String resourceId, ResourceCategory category, int startHour, int endHour, Loan loan) {
        stored.add(Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), resourceId, category, at(startHour, 0),
                at(endHour, 0), NOW.minusSeconds(3600), loan));
    }

    private ReservationDeniedException denied(Runnable action) {
        return (ReservationDeniedException) catchThrowable(action);
    }

    private static Throwable catchThrowable(Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            return t;
        }
        throw new AssertionError("se esperaba una excepción");
    }

    // ------------------------------------------------------------------------------------------------ éxito

    @Test
    void reservesASpaceAndLeavesTheReservationTheFichaAndTheQuota() {
        catalog.put("ESP-1", space("ESP-1", "Sala de Estudio 3", 8));
        store("OTRA", ResourceCategory.ESPACIO, 14, 15, null);
        store("OTRA-2", ResourceCategory.ESPACIO, 16, 17, null);

        ReservationResult result = useCase.reserve(spaceRequest("ESP-1", 10, 12));

        Reservation reservation = result.reservation();
        assertThat(reservation.status().name()).isEqualTo("CONFIRMADA");
        assertThat(reservation.origin().name()).isEqualTo("ESTUDIANTIL");
        assertThat(reservation.userId()).isEqualTo(STUDENT.id());
        assertThat(reservation.startsAt()).isEqualTo(at(10, 0));
        assertThat(reservation.endsAt()).isEqualTo(at(12, 0));
        assertThat(statusUpdates.reasons()).containsExactly(
                edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason.RESERVATION_CONFIRMED);
        assertThat(reservation.loan()).isNull();
        assertThat(result.resource().name()).isEqualTo("Sala de Estudio 3");
        assertThat(result.quota().used()).isEqualTo(3);
        assertThat(result.quota().max()).isEqualTo(3);
        assertThat(stored).contains(reservation);
        assertThat(reported).singleElement().satisfies(record -> {
            assertThat(record.reservation()).isEqualTo(reservation);
            assertThat(record.holder().code()).isEqualTo(STUDENT.code());
            assertThat(record.holder().name()).isEqualTo("Estudiante de Prueba");
        });
        assertThat(transactions).isEqualTo(1);
        assertThat(denials).isEmpty();
    }

    @Test
    void reservesAnAssetCalculatingTheDueDateFromTheTermOfInventory() {
        catalog.put("ACT-1", asset("ACT-1", "Libro de Cálculo I", 7));

        ReservationResult result = useCase.reserve(
                ReservationRequest.forAsset(STUDENT, "ACT-1", DAY, LocalTime.of(14, 30)));

        Reservation reservation = result.reservation();
        assertThat(reservation.startsAt()).isEqualTo(at(14, 30));
        assertThat(reservation.endsAt()).isEqualTo(Instant.parse("2026-09-11T03:00:00Z")); // jue 2026-09-10, 22:00
        assertThat(reservation.loan()).isEqualTo(Loan.reserved(7));
        assertThat(reported).singleElement().extracting(ReservationRecord::resource)
                .extracting(Resource::category).isEqualTo(ResourceCategory.ACTIVO);
    }

    @Test
    void anOnSiteAssetWithATermOfZeroDuesTheSameDay() {
        catalog.put("MIC-1", asset("MIC-1", "Microscopio", 0));

        Reservation reservation = useCase.reserve(
                ReservationRequest.forAsset(STUDENT, "MIC-1", DAY, LocalTime.of(10, 0))).reservation();

        assertThat(reservation.endsAt()).isEqualTo(Instant.parse("2026-09-02T03:00:00Z"));
    }

    // ------------------------------------------------------------------------------------- denegaciones

    @Test
    void anAcademicClassThatOverlapsPartiallyIsRes001WithUntilWhen() {
        catalog.put("LAB", space("LAB", "Laboratorio", 30));
        occupancies.add(occupancy("LAB", OccupancyType.ACADEMIC_BLOCK, 8, 10));

        ReservationDeniedException denial = denied(() ->
                useCase.reserve(ReservationRequest.forSpace(STUDENT, "LAB", DAY, LocalTime.of(9, 30), LocalTime.of(10, 30))));

        assertThat(denial.code()).isEqualTo(DenialCode.RES_001);
        assertThat(denial.extensions()).containsEntry("resourceId", "LAB").containsEntry("occupiedUntil", at(10, 0));
        assertThat(stored).isEmpty();
        assertThat(reported).isEmpty();
    }

    @Test
    void aFullQuotaIsRes002WithThreeOfThreeAndWhenTheNextOneFrees() {
        catalog.put("ESP-1", space("ESP-1", "Sala", 8));
        store("A", ResourceCategory.ESPACIO, 12, 13, null);
        store("B", ResourceCategory.ESPACIO, 14, 15, null);
        store("C", ResourceCategory.ESPACIO, 16, 17, null);

        ReservationDeniedException denial = denied(() -> useCase.reserve(spaceRequest("ESP-1", 10, 11)));

        assertThat(denial.code()).isEqualTo(DenialCode.RES_002);
        assertThat(denial.extensions()).containsEntry("active", 3).containsEntry("max", 3)
                .containsEntry("nextRelease", at(13, 0));
        assertThat(denial.getMessage()).isEqualTo(
                "Tienes 3 de 3 reservas activas. La próxima se libera el 2026-09-01 a las 13:00.");
    }

    @Test
    void anOverdueUnreturnedLoanKeepsTakingQuotaAndPromisesNoDate() {
        catalog.put("ESP-1", space("ESP-1", "Sala", 8));
        stored.add(Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), "ACT-OLD", ResourceCategory.ACTIVO,
                at(8, 0).minusSeconds(10 * 86_400), at(8, 0).minusSeconds(86_400), NOW, new Loan(3, NOW, null)));
        store("B", ResourceCategory.ESPACIO, 14, 15, null);
        store("C", ResourceCategory.ESPACIO, 16, 17, null);

        ReservationDeniedException denial = denied(() -> useCase.reserve(spaceRequest("ESP-1", 10, 11)));

        assertThat(denial.code()).isEqualTo(DenialCode.RES_002);
        assertThat(denial.extensions()).doesNotContainKey("nextRelease");
        assertThat(denial.getMessage()).contains("Una corresponde a un activo vencido");
    }

    @Test
    void aReturnedLoanAndAPastSpaceFreeTheirQuota() {
        catalog.put("ESP-1", space("ESP-1", "Sala", 8));
        stored.add(Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), "ACT", ResourceCategory.ACTIVO,
                at(8, 0).minusSeconds(86_400), at(8, 0), NOW, new Loan(3, NOW, NOW)));
        store("PASADA", ResourceCategory.ESPACIO, 6, 8, null);
        store("B", ResourceCategory.ESPACIO, 14, 15, null);

        assertThat(useCase.reserve(spaceRequest("ESP-1", 10, 11)).quota().used()).isEqualTo(2);
    }

    @Test
    void anActiveSanctionIsRes003WithReasonAndEndDate() {
        catalog.put("ESP-1", space("ESP-1", "Sala", 8));
        sanctionsOfStudent.add(new Sanction("Dos ausencias no justificadas en el semestre", DAY,
                LocalDate.of(2026, 9, 15), "RESERVAS"));

        ReservationDeniedException denial = denied(() -> useCase.reserve(spaceRequest("ESP-1", 10, 11)));

        assertThat(denial.code()).isEqualTo(DenialCode.RES_003);
        assertThat(denial.extensions()).containsEntry("sanctionReason", "Dos ausencias no justificadas en el semestre")
                .containsEntry("sanctionUntil", LocalDate.of(2026, 9, 15));
        assertThat(denial.getMessage()).isEqualTo("Tienes una sanción vigente hasta el 2026-09-15 y no puedes reservar.");
    }

    @Test
    void aSanctionWithoutAnEndDateDoesNotMentionOne() {
        catalog.put("ESP-1", space("ESP-1", "Sala", 8));
        sanctionsOfStudent.add(new Sanction("Pérdida de un activo", DAY, null, "RESERVAS"));

        ReservationDeniedException denial = denied(() -> useCase.reserve(spaceRequest("ESP-1", 10, 11)));

        assertThat(denial.extensions()).doesNotContainKey("sanctionUntil");
        assertThat(denial.getMessage()).isEqualTo("Tienes una sanción vigente y no puedes reservar.");
    }

    @Test
    void aSpaceAlreadyReservedByAnotherPersonIsRes004AndNeverSaysWho() {
        catalog.put("ESP-1", space("ESP-1", "Sala", 8));
        occupancies.add(occupancy("ESP-1", OccupancyType.SPACE_RESERVATION, 11, 13));

        ReservationDeniedException denial = denied(() -> useCase.reserve(spaceRequest("ESP-1", 10, 12)));

        assertThat(denial.code()).isEqualTo(DenialCode.RES_004);
        assertThat(denial.extensions()).containsOnlyKeys("resourceId");
        assertThat(denial.getMessage()).doesNotContainIgnoringCase("estudiante").doesNotContain(STUDENT.code());
    }

    @Test
    void aResourceInMaintenanceIsAProvisionalRes004WithTheDetectedStatus() {
        catalog.put("ACT-1", Resource.asset("ACT-1", "Microscopio", "MICROSCOPIO", "Lab",
                OperationalStatus.EN_MANTENIMIENTO, "BUENO", 3));

        ReservationDeniedException denial = denied(() ->
                useCase.reserve(ReservationRequest.forAsset(STUDENT, "ACT-1", DAY, TEN)));

        assertThat(denial.code()).isEqualTo(DenialCode.RES_004);
        assertThat(denial.extensions()).containsEntry("detectedStatus", "EN_MANTENIMIENTO")
                .containsEntry("resourceId", "ACT-1");
    }

    @Test
    void anAssetHeldInTheMiddleOfTheLoanPeriodBlocksTheWholePeriod() {
        catalog.put("ACT-1", asset("ACT-1", "Libro", 7));
        // Apartado para recogerlo el jueves: la recogida del martes ya se cruza con su periodo.
        occupancies.add(new edu.unimagdalena.reservasunimag.domain.model.reservation.Occupancy("ACT-1", OccupancyType.LOAN_HELD,
                Instant.parse("2026-09-03T19:00:00Z"), Instant.parse("2026-09-05T03:00:00Z")));

        ReservationDeniedException denial = denied(() ->
                useCase.reserve(ReservationRequest.forAsset(STUDENT, "ACT-1", DAY, LocalTime.of(14, 30))));

        assertThat(denial.code()).isEqualTo(DenialCode.RES_004);
    }

    @Test
    void losingTheRaceAtTheInsertIsRes004AndNothingIsReported() {
        catalog.put("ESP-1", space("ESP-1", "Sala", 8));
        nextInsertLosesTheRace = true;

        ReservationDeniedException denial = denied(() -> useCase.reserve(spaceRequest("ESP-1", 10, 12)));

        assertThat(denial.code()).isEqualTo(DenialCode.RES_004);
        assertThat(denial.extensions()).containsOnlyKeys("resourceId");
        assertThat(reported).isEmpty();
        assertThat(stored).isEmpty();
        assertThat(statusUpdates.calls).isEmpty();
    }

    @Test
    void withSeveralCausesAtOnceOnlyTheFirstInOrderOfRulesComesBack() {
        catalog.put("ESP-1", space("ESP-1", "Sala", 8));
        sanctionsOfStudent.add(new Sanction("x", DAY, LocalDate.of(2026, 9, 15), "RESERVAS"));
        store("A", ResourceCategory.ESPACIO, 12, 13, null);
        store("B", ResourceCategory.ESPACIO, 14, 15, null);
        store("C", ResourceCategory.ESPACIO, 16, 17, null);
        occupancies.add(occupancy("ESP-1", OccupancyType.ACADEMIC_BLOCK, 10, 12));

        assertThat(denied(() -> useCase.reserve(spaceRequest("ESP-1", 10, 12))).code()).isEqualTo(DenialCode.RES_003);

        sanctionsOfStudent.clear();
        assertThat(denied(() -> useCase.reserve(spaceRequest("ESP-1", 10, 12))).code()).isEqualTo(DenialCode.RES_002);

        stored.clear();
        assertThat(denied(() -> useCase.reserve(spaceRequest("ESP-1", 10, 12))).code()).isEqualTo(DenialCode.RES_001);
    }

    @Test
    void everyDenialIsRecordedWithItsCodeUserAndResource() {
        catalog.put("ESP-1", space("ESP-1", "Sala", 8));
        occupancies.add(occupancy("ESP-1", OccupancyType.SPACE_RESERVATION, 10, 12));
        denied(() -> useCase.reserve(spaceRequest("ESP-1", 10, 12)));

        nextInsertLosesTheRace = true;
        occupancies.add(occupancy("ESP-1", OccupancyType.SPACE_RESERVATION, 6, 7)); // no estorba
        denied(() -> useCase.reserve(spaceRequest("ESP-1", 14, 16)));

        assertThat(denials).hasSize(2).allSatisfy(denial -> {
            assertThat(denial.userId()).isEqualTo(STUDENT.id());
            assertThat(denial.resourceId()).isEqualTo("ESP-1");
            assertThat(denial.code()).isEqualTo(DenialCode.RES_004);
            assertThat(denial.occurredAt()).isEqualTo(NOW);
        });
    }

    // ------------------------------------------------------------------- lo que no es una denegación

    @Test
    void aSpaceSlotOverTwoHoursIsA400BeforeAskingForSanctionsQuotaOrAvailability() {
        catalog.put("ESP-1", space("ESP-1", "Sala", 8));

        assertThatThrownBy(() -> useCase.reserve(spaceRequest("ESP-1", 9, 12)))
                .isInstanceOfSatisfying(DurationExceededException.class, exception -> {
                    assertThat(exception.limitMinutes()).isEqualTo(120);
                    assertThat(exception.requestedMinutes()).isEqualTo(180);
                });
        assertThat(complianceCalls).isZero();
        assertThat(occupancies.queries).isZero();
        assertThat(denials).isEmpty();
    }

    @Test
    void aSlotOutsideTheWindowIsA400NotADenial() {
        catalog.put("ESP-1", space("ESP-1", "Sala", 8));

        assertThatThrownBy(() -> useCase.reserve(spaceRequest("ESP-1", 5, 7)))
                .isInstanceOf(InvalidTimeSlotException.class);
        assertThat(complianceCalls).isZero();
        assertThat(denials).isEmpty();
    }

    @Test
    void aPickupOutsideTheWindowIsA400() {
        catalog.put("ACT-1", asset("ACT-1", "Libro", 7));

        for (LocalTime pickup : List.of(LocalTime.of(5, 59), LocalTime.of(22, 0))) {
            assertThatThrownBy(() -> useCase.reserve(ReservationRequest.forAsset(STUDENT, "ACT-1", DAY, pickup)))
                    .isInstanceOf(InvalidTimeSlotException.class);
        }
    }

    @Test
    void theShapeMustMatchTheCategoryThatInventoryReports() {
        catalog.put("ESP-1", space("ESP-1", "Sala", 8));
        catalog.put("ACT-1", asset("ACT-1", "Libro", 7));

        assertThatThrownBy(() -> useCase.reserve(ReservationRequest.forAsset(STUDENT, "ESP-1", DAY, TEN)))
                .isInstanceOf(ReservationShapeException.class);
        assertThatThrownBy(() -> useCase.reserve(spaceRequest("ACT-1", 10, 12)))
                .isInstanceOf(ReservationShapeException.class);
        assertThat(complianceCalls).isZero();
    }

    @Test
    void anUnknownResourceIsNotFound() {
        assertThatThrownBy(() -> useCase.reserve(spaceRequest("NO-EXISTE", 10, 12)))
                .isInstanceOfSatisfying(ResourceNotFoundException.class,
                        exception -> assertThat(exception.resourceId()).isEqualTo("NO-EXISTE"));
    }

    @Test
    void anAssetWithoutLoanTermIsAnIncompleteContractNeverADefaultTerm() {
        catalog.put("ACT-1", asset("ACT-1", "Libro", null));

        assertThatThrownBy(() -> useCase.reserve(ReservationRequest.forAsset(STUDENT, "ACT-1", DAY, TEN)))
                .isInstanceOf(IncompleteResourceDetailsException.class);
        assertThat(stored).isEmpty();
    }

    @Test
    void whenInventoryIsDownNothingIsConfirmedBlindly() {
        inventoryDown = true;

        assertThatThrownBy(() -> useCase.reserve(spaceRequest("ESP-1", 10, 12)))
                .isInstanceOfSatisfying(ExternalServiceUnavailableException.class,
                        exception -> assertThat(exception.module()).isEqualTo(ExternalModule.MODULO_1));
        assertThat(stored).isEmpty();
    }

    @Test
    void whenComplianceIsDownTheReservationIsNotConfirmedAndIsNotADenial() {
        catalog.put("ESP-1", space("ESP-1", "Sala", 8));
        complianceDown = true;

        assertThatThrownBy(() -> useCase.reserve(spaceRequest("ESP-1", 10, 12)))
                .isInstanceOfSatisfying(ExternalServiceUnavailableException.class,
                        exception -> assertThat(exception.module()).isEqualTo(ExternalModule.MODULO_3));
        assertThat(stored).isEmpty();
        assertThat(denials).isEmpty();
    }

    // ------------------------------------------------------------------------ vencimiento previsto

    @Test
    void theExpectedDueDateIsInformedWithoutReservingAnything() {
        catalog.put("ACT-1", asset("ACT-1", "Libro de Cálculo I", 7));

        var expected = useCase.expectedDueDate("ACT-1", DAY, LocalTime.of(14, 30));

        assertThat(expected.resource().name()).isEqualTo("Libro de Cálculo I");
        assertThat(expected.period().dueAt()).isEqualTo(Instant.parse("2026-09-11T03:00:00Z"));
        assertThat(expected.period().skippedNonBusinessDays())
                .containsExactly(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 6));
        assertThat(stored).isEmpty();
        assertThat(reported).isEmpty();
    }

    @Test
    void theExpectedDueDateOfASpaceIsNotAnAssetAndOfAnUnknownResourceIsNotFound() {
        catalog.put("ESP-1", space("ESP-1", "Sala", 8));

        assertThatThrownBy(() -> useCase.expectedDueDate("ESP-1", DAY, TEN)).isInstanceOf(NotAnAssetException.class);
        assertThatThrownBy(() -> useCase.expectedDueDate("NO", DAY, TEN)).isInstanceOf(ResourceNotFoundException.class);
    }
}
