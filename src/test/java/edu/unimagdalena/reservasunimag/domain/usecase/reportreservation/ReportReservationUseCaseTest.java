package edu.unimagdalena.reservasunimag.domain.usecase.reportreservation;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecord;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordFilter;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Catalog;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceFilter;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.model.user.User;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import edu.unimagdalena.reservasunimag.support.CancellationFakes.RecordingNotifier;
import edu.unimagdalena.reservasunimag.support.RecordFakes.InMemoryReservationRecords;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.CLOCK;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.DIRECTOR;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.MONITOR;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.NOW;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.STUDENT;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.asset;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.at;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.space;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC10: las dos formas de la ficha, la constancia que dejan y quién puede consultarla. */
class ReportReservationUseCaseTest {

    private static final User HOLDER = new User(STUDENT.id(), STUDENT.code(), "Estudiante de Prueba",
            "e@unimag.edu.co", "hash", Role.ESTUDIANTE, true, NOW);
    private static final AcademicData NETWORKS = new AcademicData("IS-402", "Redes de Computadores", "1",
            "Ingeniería de Sistemas", "Nombre del docente");
    private static final Resource ROOM = space("ESP-0107", "Sala de Estudio 3", 8);

    private final InMemoryReservationRecords records = new InMemoryReservationRecords();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final ReportReservationUseCase useCase = new ReportReservationUseCase(records, notifier, CLOCK);
    private boolean inventoryDown;

    private final InventoryPort inventory = new InventoryPort() {
        @Override
        public Catalog searchCatalog(ResourceFilter filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Resource> details(String resourceId) {
            if (inventoryDown) {
                throw new ExternalServiceUnavailableException(
                        ExternalServiceUnavailableException.ExternalModule.MODULO_1, "caído", null);
            }
            return Optional.of(space(resourceId, "Sala de Estudio 3", 8));
        }

        @Override
        public Map<String, OperationalStatus> operationalStatus(Collection<String> resourceIds) {
            throw new UnsupportedOperationException();
        }
    };
    private final QueryReservationRecordsUseCase query = new QueryReservationRecordsUseCase(records, inventory);

    private static Reservation spaceReservation() {
        return Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), "ESP-0107", ResourceCategory.ESPACIO,
                at(10, 0), at(12, 0), NOW, null);
    }

    private static Reservation academicBlock() {
        return new Reservation(UUID.randomUUID(), null, "ESP-0412", ResourceCategory.ESPACIO,
                ReservationOrigin.ACADEMICO, ReservationStatus.CONFIRMADA, at(14, 0), at(16, 0), NOW, null);
    }

    @Test
    void aSpaceRecordCarriesTheSlotAndItsHolderAndIsSanctionable() {
        Reservation reservation = spaceReservation();

        useCase.reportCreated(reservation, ROOM, HOLDER);

        ReservationRecord sent = notifier.created.get(0);
        ReservationRecordReport stored = records.stored.get(reservation.id());
        assertThat(sent.sanctionable()).isTrue();
        assertThat(sent.holder().code()).isEqualTo(STUDENT.code());
        assertThat(sent.occurredAt()).isEqualTo(NOW);
        // La constancia y el evento son el mismo hecho (FR-005).
        assertThat(stored.eventId()).isEqualTo(sent.eventId());
        assertThat(stored.startsAt()).isEqualTo(at(10, 0));
        assertThat(stored.endsAt()).isEqualTo(at(12, 0));
        assertThat(stored.loanTermBusinessDays()).isNull();
        assertThat(stored.sentAt()).isEqualTo(NOW);
        assertThat(stored.delivery().status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(stored.delivery().attempts()).isZero();
    }

    @Test
    void anAssetRecordCarriesTheLoanPeriodAndItsTerm() {
        Reservation loan = Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), "ACT-000912",
                ResourceCategory.ACTIVO, at(14, 30), at(14, 30).plus(Duration.ofDays(9)), NOW, new Loan(7, null, null));

        useCase.reportCreated(loan, asset("ACT-000912", "Microscopio M-014", 7), HOLDER);

        ReservationRecordReport stored = records.stored.get(loan.id());
        assertThat(stored.category()).isEqualTo(ResourceCategory.ACTIVO);
        assertThat(stored.loanTermBusinessDays()).isEqualTo(7);
        assertThat(stored.endsAt()).isEqualTo(loan.endsAt());
    }

    @Test
    void anAcademicRecordHasItsClassNoHolderAndIsNeverSanctionable() {
        Reservation block = academicBlock();

        useCase.reportAcademicCreated(block, ROOM, NETWORKS);

        ReservationRecord sent = notifier.created.get(0);
        assertThat(sent.sanctionable()).isFalse();
        assertThat(sent.holder()).isNull();
        assertThat(sent.academic()).isEqualTo(NETWORKS);
        ReservationRecordReport stored = records.stored.get(block.id());
        assertThat(stored.origin()).isEqualTo(ReservationOrigin.ACADEMICO);
        assertThat(stored.sanctionable()).isFalse();
        assertThat(stored.holder()).isNull();
    }

    @Test
    void aRecordCannotMixAHolderWithAClass() {
        var holder = ReservationHolder.of(HOLDER);
        assertThatThrownBy(() -> ReservationRecord.student(UUID.randomUUID(), academicBlock(), ROOM, holder, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReservationRecord.academic(UUID.randomUUID(), spaceReservation(), ROOM, NETWORKS,
                NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReservationRecord.student(UUID.randomUUID(), spaceReservation(), ROOM, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theSameReservationCannotBeReportedTwice() {
        Reservation reservation = spaceReservation();
        useCase.reportCreated(reservation, ROOM, HOLDER);

        assertThatThrownBy(() -> useCase.reportCreated(reservation, ROOM, HOLDER))
                .isInstanceOf(IllegalStateException.class);
        assertThat(notifier.created).hasSize(1);
    }

    @Test
    void theHolderSeesTheirRecordAndTheDirectorToo() {
        Reservation reservation = spaceReservation();
        Reservation block = academicBlock();
        useCase.reportCreated(reservation, ROOM, HOLDER);
        useCase.reportAcademicCreated(block, ROOM, NETWORKS);

        assertThat(query.find(STUDENT, reservation.id()).resourceName()).isEqualTo("Sala de Estudio 3");
        assertThat(query.find(DIRECTOR, reservation.id()).reservationId()).isEqualTo(reservation.id());
        assertThat(query.find(DIRECTOR, block.id()).academic()).isEqualTo(NETWORKS);
        assertThatThrownBy(() -> query.find(MONITOR, reservation.id()))
                .isInstanceOf(ReservationNotFoundException.class);
        // Una ficha académica no tiene titular: solo la ve Dirección de Programa.
        assertThatThrownBy(() -> query.find(STUDENT, block.id())).isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> query.find(DIRECTOR, UUID.randomUUID()))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void theListNamesTheResourcesWhenModule1AnswersAndCountsNoSanctionableAmongTheAcademic() {
        useCase.reportCreated(spaceReservation(), ROOM, HOLDER);
        useCase.reportAcademicCreated(academicBlock(), ROOM, NETWORKS);
        useCase.reportAcademicCreated(academicBlock(), ROOM, NETWORKS);

        var all = query.list(new ReservationRecordFilter(null, null, null, null), new PageRequest(1, 20));
        assertThat(all.page().items()).extracting(ReservationRecordReport::resourceName)
                .containsOnly("Sala de Estudio 3");
        assertThat(all.summary().sanctionable()).isEqualTo(1);
        assertThat(query.list(new ReservationRecordFilter(ReservationOrigin.ACADEMICO, null, null, null),
                new PageRequest(1, 20)).summary()).satisfies(summary -> {
            assertThat(summary.total()).isEqualTo(2);
            assertThat(summary.sanctionable()).isZero();
        });

        inventoryDown = true;
        var listed = query.list(new ReservationRecordFilter(null, null, null, null), new PageRequest(1, 20));
        assertThat(listed.page().items()).extracting(ReservationRecordReport::resourceName).containsOnlyNulls();
        assertThat(listed.summary().pending()).isEqualTo(3);
    }
}
