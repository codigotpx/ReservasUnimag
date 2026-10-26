package edu.unimagdalena.reservasunimag.domain.usecase.reportcancellation;

import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationAlreadyClosedException;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Cancellation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReportFilter;
import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
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
import edu.unimagdalena.reservasunimag.support.CancellationFakes.InMemoryCancellationReports;
import edu.unimagdalena.reservasunimag.support.CancellationFakes.RecordingNotifier;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.DIRECTOR;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.MONITOR;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.NOW;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.STUDENT;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.at;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.space;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC11: las tres formas del reporte, la constancia que dejan y quién puede consultarla. */
class ReportCancellationUseCaseTest {

    private static final User HOLDER = new User(STUDENT.id(), STUDENT.code(), "Estudiante de Prueba",
            "e@unimag.edu.co", "hash", Role.ESTUDIANTE, true, NOW);

    private final InMemoryCancellationReports reports = new InMemoryCancellationReports();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final ReportCancellationUseCase useCase = new ReportCancellationUseCase(reports, notifier);
    private boolean inventoryDown;

    private final InventoryPort inventory = new InventoryPort() {
        @Override
        public Catalog searchCatalog(ResourceFilter filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Resource> details(String resourceId) {
            if (inventoryDown) {
                throw new edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException(
                        edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException
                                .ExternalModule.MODULO_1, "caído", null);
            }
            return Optional.of(space(resourceId, "Sala de Estudio 3", 8));
        }

        @Override
        public Map<String, OperationalStatus> operationalStatus(Collection<String> resourceIds) {
            throw new UnsupportedOperationException();
        }
    };
    private final QueryCancellationReportsUseCase query = new QueryCancellationReportsUseCase(reports, inventory);

    private static Reservation cancelled(CancellationOrigin origin) {
        return Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), "ESP-0107", ResourceCategory.ESPACIO,
                at(10, 0), at(12, 0), NOW.minusSeconds(3600), null).cancel(origin);
    }

    @Test
    void scenario1TheHolderCancellationCarriesItsNinetyMinutesAndIsAttributable() {
        Reservation reservation = cancelled(CancellationOrigin.HOLDER);

        useCase.reportCancelled(reservation,
                new Cancellation(CancellationOrigin.HOLDER, CancellationReport.HOLDER_REASON, 90, NOW),
                "Sala de Estudio 3", HOLDER);

        CancellationReport report = notifier.cancelled.get(0);
        assertThat(report.status()).isEqualTo(ReservationStatus.CANCELADA);
        assertThat(report.origin()).isEqualTo(CancellationOrigin.HOLDER);
        assertThat(report.attributableToPerson()).isTrue();
        assertThat(report.noticeMinutes()).isEqualTo(90);
        assertThat(report.delivery().status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(reports.stored.get(reservation.id())).isEqualTo(report);
    }

    @Test
    void scenarios2And3AutomaticCancellationsCarryNoNoticeAndAreNotAttributable() {
        for (CancellationOrigin origin : new CancellationOrigin[]{CancellationOrigin.ACADEMIC_PRIORITY,
                CancellationOrigin.RESOURCE_UNAVAILABLE}) {
            Reservation reservation = cancelled(origin);
            useCase.reportCancelled(reservation, new Cancellation(origin, "motivo", null, NOW), null, HOLDER);
        }

        assertThat(notifier.cancelled).hasSize(2).allSatisfy(report -> {
            assertThat(report.attributableToPerson()).isFalse();
            assertThat(report.noticeMinutes()).isNull();
            assertThat(report.holder().code()).isEqualTo(STUDENT.code());
        });
        assertThat(notifier.cancelled).extracting(CancellationReport::status).containsExactly(
                ReservationStatus.CANCELADA_POR_PRIORIDAD_ACADEMICA,
                ReservationStatus.CANCELADA_POR_RECURSO_NO_DISPONIBLE);
    }

    @Test
    void anAcademicBlockIsReportedWithoutHolder() {
        Reservation block = new Reservation(UUID.randomUUID(), null, "ESP-0107", ResourceCategory.ESPACIO,
                ReservationOrigin.ACADEMICO, ReservationStatus.CONFIRMADA, at(14, 0), at(16, 0), NOW, null)
                .cancel(CancellationOrigin.ACADEMIC_PRIORITY);

        useCase.reportCancelled(block, new Cancellation(CancellationOrigin.ACADEMIC_PRIORITY, "Clase retirada.",
                null, NOW), null, null);

        assertThat(notifier.cancelled.get(0).holder()).isNull();
    }

    @Test
    void theSameCancellationCannotBeReportedTwice() {
        Reservation reservation = cancelled(CancellationOrigin.HOLDER);
        var cancellation = new Cancellation(CancellationOrigin.HOLDER, CancellationReport.HOLDER_REASON, 90, NOW);
        useCase.reportCancelled(reservation, cancellation, null, HOLDER);

        assertThatThrownBy(() -> useCase.reportCancelled(reservation, cancellation, null, HOLDER))
                .isInstanceOf(ReservationAlreadyClosedException.class);
    }

    @Test
    void theNoticeOnlyGoesWithTheHolder() {
        assertThatThrownBy(() -> new Cancellation(CancellationOrigin.ACADEMIC_PRIORITY, "x", 30, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theHolderSeesTheirReportAndTheDirectorToo() {
        Reservation reservation = cancelled(CancellationOrigin.HOLDER);
        useCase.reportCancelled(reservation,
                new Cancellation(CancellationOrigin.HOLDER, CancellationReport.HOLDER_REASON, 90, NOW), null, HOLDER);

        assertThat(query.find(STUDENT, reservation.id()).resourceName()).isEqualTo("Sala de Estudio 3");
        assertThat(query.find(DIRECTOR, reservation.id()).reservationId()).isEqualTo(reservation.id());
        assertThatThrownBy(() -> query.find(MONITOR, reservation.id()))
                .isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> query.find(DIRECTOR, UUID.randomUUID()))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void theListNamesTheResourcesWhenModule1AnswersAndOmitsThemWhenNot() {
        Reservation reservation = cancelled(CancellationOrigin.HOLDER);
        useCase.reportCancelled(reservation,
                new Cancellation(CancellationOrigin.HOLDER, CancellationReport.HOLDER_REASON, 90, NOW), null, HOLDER);
        var filter = new CancellationReportFilter(null, null, null, null);

        assertThat(query.list(filter, new PageRequest(1, 20)).page().items().get(0).resourceName())
                .isEqualTo("Sala de Estudio 3");
        inventoryDown = true;
        var listed = query.list(filter, new PageRequest(1, 20));
        assertThat(listed.page().items().get(0).resourceName()).isNull();
        assertThat(listed.summary().pending()).isEqualTo(1);
    }
}
