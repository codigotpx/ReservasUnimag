package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.TestcontainersConfiguration;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationReportFilter;
import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.CancelReservationPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReserveResourcesPort;
import edu.unimagdalena.reservasunimag.domain.port.out.CancellationReportRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.Database.clean;
import static org.assertj.core.api.Assertions.assertThat;

/** El historial de UC11 leído desde PostgreSQL: lo que se guardó, cómo va la entrega y el listado filtrado. */
@SpringBootTest(properties = "reservations.outbox.enabled=false")
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class CancellationReportIT {

    private static final CurrentUser STUDENT = new CurrentUser(UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "2021114001", Role.ESTUDIANTE);
    private static final LocalDate TOMORROW = LocalDate.now(TimeSlot.ZONE).plusDays(1);

    @Autowired
    private ReserveResourcesPort reserveResources;
    @Autowired
    private CancelReservationPort cancelReservation;
    @Autowired
    private CancellationReportRepositoryPort reports;
    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyTables() {
        clean(jdbc);
    }

    private Reservation reserve(String resourceId, int startHour) {
        return reserveResources.reserve(ReservationRequest.forSpace(STUDENT, resourceId, TOMORROW,
                LocalTime.of(startHour, 0), LocalTime.of(startHour + 1, 0))).reservation();
    }

    @Test
    void theReportIsReadBackWithItsHolderAndAPendingDelivery() {
        Reservation reservation = reserve("ESP-0107", 10);
        cancelReservation.cancelByHolder(STUDENT, reservation.id());

        CancellationReport report = reports.findByReservation(reservation.id()).orElseThrow();

        assertThat(report.origin()).isEqualTo(CancellationOrigin.HOLDER);
        assertThat(report.reason()).isEqualTo("Cancelada por el titular.");
        assertThat(report.noticeMinutes()).isPositive();
        assertThat(report.holder().code()).isEqualTo("2021114001");
        assertThat(report.holder().name()).isEqualTo("Estudiante de Prueba");
        assertThat(report.releasedStart()).isEqualTo(reservation.startsAt());
        assertThat(report.releasedEnd()).isEqualTo(reservation.endsAt());
        assertThat(report.resourceName()).isNull();
        assertThat(report.delivery().status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(report.delivery().attempts()).isZero();
        assertThat(report.delivery().nextAttemptAt()).isNotNull();
        assertThat(reports.findByReservation(UUID.randomUUID())).isEmpty();
    }

    @Test
    void theListFiltersByOriginAndDeliveryAndTheSummaryCountsTheWholeFilter() {
        Reservation holder = reserve("ESP-0107", 8);
        Reservation academic = reserve("ESP-0305", 9);
        Reservation sent = reserve("ESP-0412", 10);
        cancelReservation.cancelByHolder(STUDENT, holder.id());
        cancelReservation.cancelBySystem(academic.id(), CancellationOrigin.ACADEMIC_PRIORITY, "Clase IS-510.");
        cancelReservation.cancelByHolder(STUDENT, sent.id());
        jdbc.sql("UPDATE cancellation_report SET delivery_status = 'SENT' WHERE reservation_id = :id")
                .param("id", sent.id()).update();

        var all = reports.search(new CancellationReportFilter(null, null, null, null), new PageRequest(1, 2));
        assertThat(all.page().items()).hasSize(2);
        assertThat(all.page().total()).isEqualTo(3);
        assertThat(all.summary().total()).isEqualTo(3);
        assertThat(all.summary().sent()).isEqualTo(1);
        assertThat(all.summary().pending()).isEqualTo(2);

        var onlyAcademic = reports.search(new CancellationReportFilter(CancellationOrigin.ACADEMIC_PRIORITY, null,
                null, null), new PageRequest(1, 20));
        assertThat(onlyAcademic.page().items()).singleElement()
                .satisfies(report -> assertThat(report.reservationId()).isEqualTo(academic.id()));

        var pendingHolders = reports.search(new CancellationReportFilter(CancellationOrigin.HOLDER,
                DeliveryStatus.PENDING, null, null), new PageRequest(1, 20));
        assertThat(pendingHolders.page().items()).extracting(CancellationReport::reservationId)
                .containsExactly(holder.id());

        LocalDate today = LocalDate.now(TimeSlot.ZONE);
        assertThat(reports.search(new CancellationReportFilter(null, null, today, today), new PageRequest(1, 20))
                .summary().total()).isEqualTo(3);
        assertThat(reports.search(new CancellationReportFilter(null, null, today.plusDays(1), null),
                new PageRequest(1, 20)).summary().total()).isZero();
    }
}
