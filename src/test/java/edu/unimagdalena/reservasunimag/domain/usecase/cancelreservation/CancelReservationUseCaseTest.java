package edu.unimagdalena.reservasunimag.domain.usecase.cancelreservation;

import edu.unimagdalena.reservasunimag.domain.model.error.CancellationDeniedException;
import edu.unimagdalena.reservasunimag.domain.model.error.CancellationDeniedException.Reason;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException.ExternalModule;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationAlreadyClosedException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationPolicy;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationResult;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Catalog;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceFilter;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.model.user.User;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.UserRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.usecase.reportcancellation.ReportCancellationUseCase;
import edu.unimagdalena.reservasunimag.support.CancellationFakes.InMemoryCancellationReports;
import edu.unimagdalena.reservasunimag.support.CancellationFakes.InMemoryReservations;
import edu.unimagdalena.reservasunimag.support.CancellationFakes.RecordingNotifier;
import edu.unimagdalena.reservasunimag.support.CancellationFakes.RollbackTransactions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.CLOCK;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.MONITOR;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.NOW;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.STUDENT;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.asset;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.at;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.space;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC4 con puertos falsos: las tres ramas, la doble cancelación y el reporte que dejan (UC11). Ahora son las 09:14. */
class CancelReservationUseCaseTest {

    private final Map<String, Resource> catalog = new HashMap<>(Map.of(
            "ESP-0107", space("ESP-0107", "Sala de Estudio 3", 8),
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
            throw new UnsupportedOperationException();
        }
    };

    private final UserRepositoryPort users = new UserRepositoryPort() {
        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findById(UUID id) {
            return Optional.of(new User(id, STUDENT.code(), "Estudiante de Prueba", "e@unimag.edu.co", "hash",
                    Role.ESTUDIANTE, true, NOW));
        }
    };

    private final InMemoryReservations reservations = new InMemoryReservations();
    private final InMemoryCancellationReports reports = new InMemoryCancellationReports();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final RollbackTransactions transactions = new RollbackTransactions(reservations, reports, notifier);
    private final ReservationCanceller canceller = new ReservationCanceller(reservations, users, inventory,
            new ReportCancellationUseCase(reports, notifier));
    private final CancelReservationUseCase useCase = new CancelReservationUseCase(reservations, canceller,
            new CancellationPolicy(Duration.ofMinutes(10)), transactions, CLOCK, 3);

    private Reservation storeSpace(UUID holder, int startHour, int endHour) {
        return reservations.add(Reservation.confirmed(UUID.randomUUID(), holder, "ESP-0107",
                ResourceCategory.ESPACIO, at(startHour, 0), at(endHour, 0), NOW.minusSeconds(3600), null));
    }

    private Reservation storeAsset(Loan loan, Instant pickup) {
        return reservations.add(Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), "ACT-000913",
                ResourceCategory.ACTIVO, pickup, pickup.plus(Duration.ofDays(3)), NOW.minusSeconds(3600), loan));
    }

    private Reservation storeAcademicBlock() {
        return reservations.add(new Reservation(UUID.randomUUID(), null, "ESP-0107", ResourceCategory.ESPACIO,
                ReservationOrigin.ACADEMICO, ReservationStatus.CONFIRMADA, at(14, 0), at(16, 0),
                NOW.minusSeconds(3600), null));
    }

    // ---------------------------------------------------------------------------------------- titular

    @Test
    void scenario1TheHolderCancelsASpaceTheSlotIsReleasedAndTheQuotaDrops() {
        Reservation reservation = storeSpace(STUDENT.id(), 10, 12);
        storeSpace(STUDENT.id(), 14, 15);

        CancellationResult result = useCase.cancelByHolder(STUDENT, reservation.id());

        assertThat(result.alreadyCancelled()).isFalse();
        assertThat(result.reservation().status()).isEqualTo(ReservationStatus.CANCELADA);
        assertThat(reservations.stored.get(reservation.id()).status()).isEqualTo(ReservationStatus.CANCELADA);
        assertThat(result.cancellation().origin()).isEqualTo(CancellationOrigin.HOLDER);
        assertThat(result.cancellation().noticeMinutes()).isEqualTo(46);
        assertThat(result.cancellation().cancelledAt()).isEqualTo(NOW);
        assertThat(result.quota().used()).isEqualTo(1);
        assertThat(result.quota().max()).isEqualTo(3);
    }

    @Test
    void theHolderBranchQueuesExactlyOneAttributableEventWithItsNotice() {
        Reservation reservation = storeSpace(STUDENT.id(), 10, 12);

        useCase.cancelByHolder(STUDENT, reservation.id());

        assertThat(notifier.cancelled).hasSize(1);
        CancellationReport report = notifier.cancelled.get(0);
        assertThat(report.attributableToPerson()).isTrue();
        assertThat(report.status()).isEqualTo(ReservationStatus.CANCELADA);
        assertThat(report.noticeMinutes()).isEqualTo(46);
        assertThat(report.reason()).isEqualTo("Cancelada por el titular.");
        assertThat(report.resourceName()).isEqualTo("Sala de Estudio 3");
        assertThat(report.holder().code()).isEqualTo(STUDENT.code());
        assertThat(report.releasedStart()).isEqualTo(at(10, 0));
        assertThat(report.releasedEnd()).isEqualTo(at(12, 0));
        // La constancia y el evento llevan el mismo eventId.
        assertThat(reports.stored.get(reservation.id()).eventId()).isEqualTo(report.eventId());
    }

    @Test
    void scenario2AnAssetNotPickedUpReleasesTheWholeLoanPeriod() {
        Reservation loan = storeAsset(Loan.reserved(3), at(14, 30));

        CancellationResult result = useCase.cancelByHolder(STUDENT, loan.id());

        assertThat(result.reservation().status()).isEqualTo(ReservationStatus.CANCELADA);
        assertThat(result.cancellation().noticeMinutes()).isEqualTo(316);
        CancellationReport report = notifier.cancelled.get(0);
        assertThat(report.releasedStart()).isEqualTo(at(14, 30));
        assertThat(report.releasedEnd()).isEqualTo(at(14, 30).plus(Duration.ofDays(3)));
        assertThat(report.category()).isEqualTo(ResourceCategory.ACTIVO);
    }

    @Test
    void aMonitorCancelsTheirOwnReservationLikeAStudent() {
        Reservation reservation = storeSpace(MONITOR.id(), 10, 12);

        assertThat(useCase.cancelByHolder(MONITOR, reservation.id()).reservation().status())
                .isEqualTo(ReservationStatus.CANCELADA);
    }

    @Test
    void someoneElsesReservationIsNotFoundAndNothingChanges() {
        Reservation reservation = storeSpace(MONITOR.id(), 10, 12);

        assertThatThrownBy(() -> useCase.cancelByHolder(STUDENT, reservation.id()))
                .isInstanceOf(ReservationNotFoundException.class);
        assertThat(reservations.stored.get(reservation.id()).status()).isEqualTo(ReservationStatus.CONFIRMADA);
        assertThat(notifier.cancelled).isEmpty();
        assertThat(transactions.opened).isZero();
    }

    @Test
    void aReservationThatDoesNotExistIsNotFound() {
        assertThatThrownBy(() -> useCase.cancelByHolder(STUDENT, UUID.randomUUID()))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void scenario3TooLateIsCan001AndNothingIsReported() {
        Reservation reservation = storeSpace(STUDENT.id(), 9, 10);

        assertThatThrownBy(() -> useCase.cancelByHolder(STUDENT, reservation.id()))
                .isInstanceOfSatisfying(CancellationDeniedException.class,
                        denied -> assertThat(denied.code().code()).isEqualTo("CAN-001"));
        // UC11 FR-009: un intento rechazado no deja ni constancia ni evento.
        assertThat(reservations.stored.get(reservation.id()).status()).isEqualTo(ReservationStatus.CONFIRMADA);
        assertThat(reports.stored).isEmpty();
        assertThat(notifier.cancelled).isEmpty();
    }

    @Test
    void anAssetAlreadyInHandsIsCan001ForTheHolderToo() {
        Reservation loan = storeAsset(new Loan(3, at(8, 0), null), at(14, 30));

        assertThatThrownBy(() -> useCase.cancelByHolder(STUDENT, loan.id()))
                .isInstanceOfSatisfying(CancellationDeniedException.class,
                        denied -> assertThat(denied.reason()).isEqualTo(Reason.ASSET_ALREADY_PICKED_UP));
        assertThat(notifier.cancelled).isEmpty();
    }

    @Test
    void cancellingTwiceAnswersWithTheOriginalAndReleasesNothingTwice() {
        Reservation reservation = storeSpace(STUDENT.id(), 10, 12);
        CancellationResult first = useCase.cancelByHolder(STUDENT, reservation.id());

        CancellationResult second = useCase.cancelByHolder(STUDENT, reservation.id());

        assertThat(second.alreadyCancelled()).isTrue();
        assertThat(second.reservation().status()).isEqualTo(ReservationStatus.CANCELADA);
        assertThat(second.cancellation().cancelledAt()).isEqualTo(first.cancellation().cancelledAt());
        assertThat(second.cancellation().origin()).isEqualTo(CancellationOrigin.HOLDER);
        assertThat(second.quota()).isEqualTo(first.quota());
        assertThat(notifier.cancelled).hasSize(1);
        assertThat(reports.stored).hasSize(1);
    }

    @Test
    void withTheInventoryDownTheCancellationStillHappensAndTheEventGoesWithoutName() {
        Reservation reservation = storeSpace(STUDENT.id(), 10, 12);
        inventoryDown = true;

        useCase.cancelByHolder(STUDENT, reservation.id());

        assertThat(reservations.stored.get(reservation.id()).status()).isEqualTo(ReservationStatus.CANCELADA);
        assertThat(notifier.cancelled.get(0).resourceName()).isNull();
        assertThat(notifier.cancelled.get(0).resourceId()).isEqualTo("ESP-0107");
    }

    @Test
    void ifTheReservationWasAlreadyClosedAsAnAbsenceEverythingIsUndone() {
        Reservation reservation = storeSpace(STUDENT.id(), 10, 12);
        reports.stored.put(reservation.id(), null);

        assertThatThrownBy(() -> useCase.cancelByHolder(STUDENT, reservation.id()))
                .isInstanceOf(ReservationAlreadyClosedException.class);
        assertThat(reservations.stored.get(reservation.id()).status()).isEqualTo(ReservationStatus.CONFIRMADA);
        assertThat(notifier.cancelled).isEmpty();
    }

    // -------------------------------------------------------------------------- prioridad académica y recurso

    @Test
    void scenario4AcademicPriorityIgnoresHolderAndDeadlineAndIsNotAttributable() {
        // Empieza en 6 minutos: el titular ya no podría, la prioridad académica sí.
        Reservation reservation = reservations.add(Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), "ESP-0107",
                ResourceCategory.ESPACIO, at(9, 20), at(11, 0), NOW.minusSeconds(3600), null));

        CancellationResult result = useCase.cancelBySystem(reservation.id(), CancellationOrigin.ACADEMIC_PRIORITY,
                "La franja se asignó a actividad docente: IS-510 grupo 1.");

        assertThat(result.reservation().status()).isEqualTo(ReservationStatus.CANCELADA_POR_PRIORIDAD_ACADEMICA);
        assertThat(result.cancellation().noticeMinutes()).isNull();
        assertThat(result.quota().used()).isZero();
        CancellationReport report = notifier.cancelled.get(0);
        assertThat(report.attributableToPerson()).isFalse();
        assertThat(report.noticeMinutes()).isNull();
        assertThat(report.reason()).isEqualTo("La franja se asignó a actividad docente: IS-510 grupo 1.");
        assertThat(report.holder()).isNotNull();
    }

    @Test
    void resourceUnavailableLeavesItsOwnStatusAndIsNotAttributable() {
        Reservation reservation = storeSpace(STUDENT.id(), 10, 12);

        useCase.cancelBySystem(reservation.id(), CancellationOrigin.RESOURCE_UNAVAILABLE, "Gotera en el salón.");

        assertThat(reservations.stored.get(reservation.id()).status())
                .isEqualTo(ReservationStatus.CANCELADA_POR_RECURSO_NO_DISPONIBLE);
        assertThat(notifier.cancelled.get(0).attributableToPerson()).isFalse();
        assertThat(notifier.cancelled.get(0).noticeMinutes()).isNull();
    }

    @Test
    void theSystemDoesNotTakeAnAssetOutOfSomeonesHands() {
        Reservation loan = storeAsset(new Loan(3, at(8, 0), null), at(14, 30));

        assertThatThrownBy(() -> useCase.cancelBySystem(loan.id(), CancellationOrigin.ACADEMIC_PRIORITY, "Clase"))
                .isInstanceOfSatisfying(CancellationDeniedException.class,
                        denied -> assertThat(denied.reason()).isEqualTo(Reason.ASSET_ALREADY_PICKED_UP));
    }

    @Test
    void anAcademicBlockWithoutHolderIsReportedWithoutHolderAndWithoutQuota() {
        Reservation block = storeAcademicBlock();

        CancellationResult result = useCase.cancelBySystem(block.id(), CancellationOrigin.ACADEMIC_PRIORITY,
                "Clase retirada del horario.");

        assertThat(result.quota()).isNull();
        assertThat(notifier.cancelled.get(0).holder()).isNull();
    }

    @Test
    void theHolderBranchDoesNotEnterThroughTheSystemDoor() {
        Reservation reservation = storeSpace(STUDENT.id(), 10, 12);

        assertThatThrownBy(() -> useCase.cancelBySystem(reservation.id(), CancellationOrigin.HOLDER, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSystemCancellationOfAnAlreadyCancelledReservationReportsNothingNew() {
        Reservation reservation = storeSpace(STUDENT.id(), 10, 12);
        useCase.cancelByHolder(STUDENT, reservation.id());

        CancellationResult again = useCase.cancelBySystem(reservation.id(), CancellationOrigin.ACADEMIC_PRIORITY,
                "Clase");

        assertThat(again.alreadyCancelled()).isTrue();
        assertThat(again.reservation().status()).isEqualTo(ReservationStatus.CANCELADA);
        assertThat(notifier.cancelled).hasSize(1);
    }
}
