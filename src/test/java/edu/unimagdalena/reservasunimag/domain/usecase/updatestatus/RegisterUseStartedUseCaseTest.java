package edu.unimagdalena.reservasunimag.domain.usecase.updatestatus;

import edu.unimagdalena.reservasunimag.domain.model.error.DenialCode;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException.ExternalModule;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationDeniedException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.error.UseStartRejectedException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.AcademicStarts;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.UseStartResult;
import edu.unimagdalena.reservasunimag.domain.model.resource.Catalog;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceFilter;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;
import edu.unimagdalena.reservasunimag.support.CancellationFakes.InMemoryReservations;
import edu.unimagdalena.reservasunimag.support.InMemoryStatusChanges;
import edu.unimagdalena.reservasunimag.support.InMemoryStatusChanges.RecordingInventoryNotifier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.MONITOR;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.STUDENT;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.asset;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.at;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.space;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC7 FR-004: los dos disparadores del inicio de uso, la ventana, el mantenimiento y la idempotencia. */
class RegisterUseStartedUseCaseTest {

    private Instant now = at(10, 3);
    private final Map<String, Resource> catalog = new HashMap<>(Map.of(
            "ESP-0107", space("ESP-0107", "Sala de Estudio 3", 8),
            "ESP-0220", space("ESP-0220", "Laboratorio", 30, OperationalStatus.EN_MANTENIMIENTO),
            "ACT-000913", asset("ACT-000913", "Microscopio M-015")));
    private boolean inventoryDown;

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
            if (inventoryDown) {
                throw new ExternalServiceUnavailableException(ExternalModule.MODULO_1, "caído", null);
            }
            Map<String, OperationalStatus> statuses = new HashMap<>();
            resourceIds.forEach(id -> statuses.put(id, catalog.get(id).operationalStatus()));
            return statuses;
        }
    };

    private final InMemoryReservations reservations = new InMemoryReservations();
    private final InMemoryStatusChanges statusChanges = new InMemoryStatusChanges();
    private final RecordingInventoryNotifier notifier = new RecordingInventoryNotifier();
    private final TransactionPort transactions = new TransactionPort() {
        @Override
        public <T> T required(Supplier<T> work) {
            return work.get();
        }
    };
    private final RegisterUseStartedUseCase useCase = new RegisterUseStartedUseCase(reservations, statusChanges,
            new UpdateResourceStatusUseCase(statusChanges, notifier, () -> now, true), inventory, transactions,
            () -> now, Duration.ofMinutes(10));

    private Reservation spaceReservation(String resourceId, int startHour, int endHour) {
        return reservations.add(Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), resourceId,
                ResourceCategory.ESPACIO, at(startHour, 0), at(endHour, 0), at(7, 0), null));
    }

    private Reservation academic(String resourceId, int startHour, int endHour) {
        return reservations.add(new Reservation(UUID.randomUUID(), null, resourceId, ResourceCategory.ESPACIO,
                ReservationOrigin.ACADEMICO, ReservationStatus.CONFIRMADA, at(startHour, 0), at(endHour, 0),
                at(7, 0), null));
    }

    @Test
    void theHolderRegistersTheArrivalAndTheNoticeIsQueued() {
        Reservation reservation = spaceReservation("ESP-0107", 10, 12);

        UseStartResult result = useCase.startByHolder(STUDENT, reservation.id());

        assertThat(result.alreadyStarted()).isFalse();
        assertThat(result.reservation().useStartedAt()).isEqualTo(now);
        assertThat(reservations.stored.get(reservation.id()).useStarted()).isTrue();
        assertThat(result.change().outboxMessageId()).isNotNull();
        assertThat(notifier.sent).hasSize(1);
    }

    @Test
    void pickingUpAnAssetIsStartingToUseIt() {
        Reservation loan = reservations.add(Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), "ACT-000913",
                ResourceCategory.ACTIVO, at(10, 0), at(10, 0).plus(Duration.ofDays(3)), at(7, 0), Loan.reserved(3)));

        useCase.startByHolder(STUDENT, loan.id());

        Reservation started = reservations.stored.get(loan.id());
        assertThat(started.loan().pickedUpAt()).isEqualTo(now);
        assertThat(started.inHands()).isTrue();
        assertThat(notifier.sent.get(0).useEndsAt()).isNull();
    }

    @Test
    void registeringTwiceDoesNotMarkRecordOrNotifyAgain() {
        Reservation reservation = spaceReservation("ESP-0107", 10, 12);
        UseStartResult first = useCase.startByHolder(STUDENT, reservation.id());
        now = at(10, 20);

        UseStartResult second = useCase.startByHolder(STUDENT, reservation.id());

        assertThat(second.alreadyStarted()).isTrue();
        assertThat(second.reservation().useStartedAt()).isEqualTo(at(10, 3));
        assertThat(second.change().outboxMessageId()).isEqualTo(first.change().outboxMessageId());
        assertThat(statusChanges.stored).hasSize(1);
        assertThat(notifier.sent).hasSize(1);
    }

    @Test
    void theWindowOpensTenMinutesBeforeAndClosesWhenTheSlotEnds() {
        Reservation reservation = spaceReservation("ESP-0107", 10, 12);

        now = at(9, 49);
        assertThatThrownBy(() -> useCase.startByHolder(STUDENT, reservation.id()))
                .isInstanceOfSatisfying(UseStartRejectedException.class, rejected -> {
                    assertThat(rejected.code()).isEqualTo(DenialCode.USO_002);
                    assertThat(rejected.extensions()).containsEntry("windowOpensAt", at(9, 50));
                    assertThat(rejected.getMessage()).isEqualTo("Tu reserva empieza a las 10:00. Puedes registrar tu "
                            + "llegada desde las 09:50.");
                });
        now = at(12, 0);
        assertThatThrownBy(() -> useCase.startByHolder(STUDENT, reservation.id()))
                .isInstanceOfSatisfying(UseStartRejectedException.class,
                        rejected -> assertThat(rejected.code()).isEqualTo(DenialCode.USO_002));
        now = at(9, 50);
        assertThat(useCase.startByHolder(STUDENT, reservation.id()).alreadyStarted()).isFalse();
    }

    @Test
    void aCancelledReservationDoesNotStartItsUse() {
        Reservation reservation = spaceReservation("ESP-0107", 10, 12);
        reservations.stored.put(reservation.id(), reservation.cancel(CancellationOrigin.HOLDER));

        assertThatThrownBy(() -> useCase.startByHolder(STUDENT, reservation.id()))
                .isInstanceOfSatisfying(UseStartRejectedException.class,
                        rejected -> assertThat(rejected.code()).isEqualTo(DenialCode.USO_001));
        assertThat(notifier.sent).isEmpty();
    }

    @Test
    void someoneElsesReservationIsNotFound() {
        Reservation reservation = spaceReservation("ESP-0107", 10, 12);

        assertThatThrownBy(() -> useCase.startByHolder(MONITOR, reservation.id()))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void aResourceUnderMaintenanceIsRes004AndNothingIsMarked() {
        Reservation reservation = spaceReservation("ESP-0220", 10, 12);

        assertThatThrownBy(() -> useCase.startByHolder(STUDENT, reservation.id()))
                .isInstanceOfSatisfying(ReservationDeniedException.class,
                        denied -> assertThat(denied.code()).isEqualTo(DenialCode.RES_004));
        assertThat(reservations.stored.get(reservation.id()).useStarted()).isFalse();
        assertThat(notifier.sent).isEmpty();
    }

    @Test
    void withTheInventoryDownTheArrivalIsRegisteredAnyway() {
        Reservation reservation = spaceReservation("ESP-0107", 10, 12);
        inventoryDown = true;

        assertThat(useCase.startByHolder(STUDENT, reservation.id()).alreadyStarted()).isFalse();
        assertThat(notifier.sent).hasSize(1);
    }

    @Test
    void aClassThatReachesItsHourStartsByItselfOnce() {
        Reservation due = academic("ESP-0107", 10, 12);
        academic("ESP-0108", 11, 13);                                   // todavía no es su hora
        Reservation cancelled = academic("ESP-0201", 10, 12);
        reservations.stored.put(cancelled.id(), cancelled.cancel(CancellationOrigin.ACADEMIC_PRIORITY));
        catalog.put("ESP-0108", space("ESP-0108", "Sala 4", 8));
        catalog.put("ESP-0201", space("ESP-0201", "Auditorio", 200));

        AcademicStarts first = useCase.startDueAcademicBlocks();
        AcademicStarts second = useCase.startDueAcademicBlocks();

        assertThat(first.started()).isEqualTo(1);
        assertThat(second.started()).isZero();
        assertThat(notifier.sent).singleElement().satisfies(useStart -> {
            assertThat(useStart.reservation().id()).isEqualTo(due.id());
            assertThat(useStart.useEndsAt()).isEqualTo(at(12, 0));
        });
    }

    @Test
    void aClassInARoomUnderMaintenanceIsLeftOutWithoutStoppingTheOthers() {
        academic("ESP-0220", 10, 12);
        Reservation fine = academic("ESP-0107", 10, 12);

        AcademicStarts result = useCase.startDueAcademicBlocks();

        assertThat(result.started()).isEqualTo(1);
        assertThat(result.skippedForMaintenance()).isEqualTo(1);
        assertThat(notifier.sent).extracting(useStart -> useStart.reservation().id()).containsExactly(fine.id());
    }

    @Test
    void withTheInventoryDownTheClassesStartAnyway() {
        academic("ESP-0107", 10, 12);
        inventoryDown = true;

        assertThat(useCase.startDueAcademicBlocks().started()).isEqualTo(1);
    }
}
