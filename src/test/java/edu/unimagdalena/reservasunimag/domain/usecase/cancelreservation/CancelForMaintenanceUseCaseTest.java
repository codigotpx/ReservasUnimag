package edu.unimagdalena.reservasunimag.domain.usecase.cancelreservation;

import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException.ExternalModule;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.MaintenanceCancellation;
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
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.NOW;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.STUDENT;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.at;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.space;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC4 FR-010: el recurso queda fuera de servicio y sus reservas futuras se cancelan sin penalizar. */
class CancelForMaintenanceUseCaseTest {

    private static final String LAB = "ESP-0220";
    private static final String REASON = "El salón quedó fuera de servicio por una gotera.";

    private final Map<String, Resource> catalog = new HashMap<>(Map.of(LAB, space(LAB, "Laboratorio de Química", 30)));
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

    /** Cada persona tiene como código su propio id, para poder distinguirlas en los reportes. */
    private final UserRepositoryPort users = new UserRepositoryPort() {
        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findById(UUID id) {
            return Optional.of(new User(id, id.toString(), "Persona " + id, "p@unimag.edu.co", "hash",
                    Role.ESTUDIANTE, true, NOW));
        }
    };

    private final InMemoryReservations reservations = new InMemoryReservations();
    private final InMemoryCancellationReports reports = new InMemoryCancellationReports();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final RollbackTransactions transactions = new RollbackTransactions(reservations, reports, notifier);
    private final CancelForMaintenanceUseCase useCase = new CancelForMaintenanceUseCase(reservations, users,
            inventory, new ReservationCanceller(reservations, users, inventory,
            new ReportCancellationUseCase(reports, notifier)), transactions, CLOCK);

    private Reservation store(UUID holder, String resourceId, Instant start, Instant end, Loan loan) {
        return reservations.add(Reservation.confirmed(UUID.randomUUID(), holder, resourceId,
                loan == null ? ResourceCategory.ESPACIO : ResourceCategory.ACTIVO, start, end, NOW.minusSeconds(60),
                loan));
    }

    private Reservation future(int startHour) {
        return store(UUID.randomUUID(), LAB, at(startHour, 0), at(startHour + 1, 0), null);
    }

    @Test
    void thePreviewListsOnlyFutureConfirmedStudentReservationsAndTouchesNothing() {
        Reservation upcoming = future(10);
        Reservation later = future(15);
        store(STUDENT.id(), LAB, at(8, 0), at(9, 0), null);                       // ya pasó
        Reservation cancelled = future(12);
        reservations.markCancelled(cancelled.id(), new edu.unimagdalena.reservasunimag.domain.model.reservation
                .Cancellation(CancellationOrigin.HOLDER, "x", 30, NOW));
        store(STUDENT.id(), "ESP-0107", at(10, 0), at(11, 0), null);             // otro recurso
        reservations.add(new Reservation(UUID.randomUUID(), null, LAB, ResourceCategory.ESPACIO,
                ReservationOrigin.ACADEMICO, ReservationStatus.CONFIRMADA, at(17, 0), at(19, 0), NOW, null));

        MaintenanceCancellation preview = useCase.preview(LAB);

        assertThat(preview.applied()).isFalse();
        assertThat(preview.resourceName()).isEqualTo("Laboratorio de Química");
        assertThat(preview.affected()).extracting(affected -> affected.reservation().id())
                .containsExactly(upcoming.id(), later.id());
        assertThat(preview.affected().get(0).holder().code()).isEqualTo(upcoming.userId().toString());
        assertThat(preview.skipped()).isEmpty();
        assertThat(reservations.stored.get(upcoming.id()).status()).isEqualTo(ReservationStatus.CONFIRMADA);
        assertThat(notifier.cancelled).isEmpty();
        assertThat(transactions.opened).isZero();
    }

    @Test
    void applyingCancelsEachOneWithItsOwnNonAttributableEvent() {
        Reservation first = future(10);
        Reservation second = future(15);

        MaintenanceCancellation applied = useCase.apply(LAB, "  " + REASON + " ");

        assertThat(applied.applied()).isTrue();
        assertThat(applied.appliedAt()).isEqualTo(NOW);
        assertThat(applied.affected()).hasSize(2);
        for (Reservation reservation : new Reservation[]{first, second}) {
            assertThat(reservations.stored.get(reservation.id()).status())
                    .isEqualTo(ReservationStatus.CANCELADA_POR_RECURSO_NO_DISPONIBLE);
        }
        assertThat(notifier.cancelled).hasSize(2).allSatisfy(report -> {
            assertThat(report.origin()).isEqualTo(CancellationOrigin.RESOURCE_UNAVAILABLE);
            assertThat(report.attributableToPerson()).isFalse();
            assertThat(report.noticeMinutes()).isNull();
            assertThat(report.reason()).isEqualTo(REASON);
            assertThat(report.resourceName()).isEqualTo("Laboratorio de Química");
        });
        assertThat(transactions.opened).isEqualTo(1);
    }

    @Test
    void anAssetAlreadyInHandsIsSkippedAndReported() {
        String microscope = "ACT-000913";
        catalog.put(microscope, edu.unimagdalena.reservasunimag.support.ResourceFixtures.asset(microscope, "Microscopio"));
        Reservation inHands = store(STUDENT.id(), microscope, at(8, 0), at(8, 0).plus(Duration.ofDays(3)),
                new Loan(3, at(8, 5), null));
        Reservation reserved = store(UUID.randomUUID(), microscope, at(8, 0).plus(Duration.ofDays(4)),
                at(8, 0).plus(Duration.ofDays(7)), Loan.reserved(3));

        assertThat(useCase.preview(microscope).skipped()).extracting(skipped -> skipped.reservation().id())
                .containsExactly(inHands.id());

        MaintenanceCancellation applied = useCase.apply(microscope, REASON);

        assertThat(applied.skipped()).singleElement().satisfies(skipped -> {
            assertThat(skipped.reservation().id()).isEqualTo(inHands.id());
            assertThat(skipped.reason()).isEqualTo("ASSET_ALREADY_PICKED_UP");
        });
        assertThat(applied.affected()).extracting(affected -> affected.reservation().id())
                .containsExactly(reserved.id());
        assertThat(reservations.stored.get(inHands.id()).status()).isEqualTo(ReservationStatus.CONFIRMADA);
        assertThat(notifier.cancelled).hasSize(1);
    }

    @Test
    void aResourceWithoutReservationsThatModule1DoesNotKnowIsNotFound() {
        assertThatThrownBy(() -> useCase.preview("NO-EXISTE")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> useCase.apply("NO-EXISTE", REASON)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aKnownResourceWithoutReservationsIsAnEmptyResult() {
        MaintenanceCancellation applied = useCase.apply(LAB, REASON);

        assertThat(applied.affected()).isEmpty();
        assertThat(applied.skipped()).isEmpty();
        assertThat(applied.applied()).isTrue();
    }

    @Test
    void withTheInventoryDownItStillCancelsAndOnlyTheNameIsMissing() {
        future(10);
        inventoryDown = true;

        MaintenanceCancellation applied = useCase.apply(LAB, REASON);

        assertThat(applied.resourceName()).isNull();
        assertThat(applied.affected()).hasSize(1);
        assertThat(notifier.cancelled.get(0).resourceName()).isNull();
    }

    @Test
    void aMassCancellationProducesOneReportPerHolderNeverABatch() {
        // Edge case "Cancelación masiva" de UC11: cincuenta reservas, cincuenta reportes, cada uno con su titular.
        for (int i = 0; i < 50; i++) {
            store(UUID.randomUUID(), LAB, at(10, 0).plus(Duration.ofDays(i + 1)),
                    at(11, 0).plus(Duration.ofDays(i + 1)), null);
        }

        useCase.apply(LAB, REASON);

        assertThat(notifier.cancelled).hasSize(50);
        assertThat(reports.stored).hasSize(50);
        assertThat(notifier.cancelled).extracting(report -> report.holder().userId()).doesNotHaveDuplicates();
        assertThat(notifier.cancelled).extracting(CancellationReport::eventId).doesNotHaveDuplicates();
        assertThat(notifier.cancelled).extracting(CancellationReport::releasedStart).isSorted();
    }
}
