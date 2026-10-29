package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.TestcontainersConfiguration;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.ExtraordinaryNeed;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationDeniedException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordFilter;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRecordReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.RegisterExtraordinaryNeedPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReserveResourcesPort;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRecordRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El historial de UC10 contra PostgreSQL: se escribe con la reserva o no se escribe, una sola vez, y se lee. */
@SpringBootTest(properties = {"reservations.outbox.enabled=false", "reservations.academic.use-start-enabled=false"})
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class ReservationRecordIT {

    private static final CurrentUser STUDENT = new CurrentUser(UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "2021114001", Role.ESTUDIANTE);
    /** Tiene una sanción vigente en el Módulo 3 falso del perfil local. */
    private static final CurrentUser SANCTIONED = new CurrentUser(
            UUID.fromString("00000000-0000-0000-0000-000000000002"), "2020114002", Role.MONITOR);
    private static final LocalDate TOMORROW = LocalDate.now(TimeSlot.ZONE).plusDays(1);

    @Autowired
    private ReserveResourcesPort reserveResources;
    @Autowired
    private RegisterExtraordinaryNeedPort registerExtraordinaryNeed;
    @Autowired
    private ReservationRecordRepositoryPort records;
    @Autowired
    private TransactionPort transactions;
    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyTables() {
        clean(jdbc);
    }

    private Reservation reserveSpace(String resourceId, int startHour) {
        return reserveResources.reserve(ReservationRequest.forSpace(STUDENT, resourceId, TOMORROW,
                LocalTime.of(startHour, 0), LocalTime.of(startHour + 1, 0))).reservation();
    }

    private Reservation academicBlock(String resourceId, int startHour) {
        return registerExtraordinaryNeed.register(new ExtraordinaryNeed(resourceId, TOMORROW,
                LocalTime.of(startHour, 0), LocalTime.of(startHour + 2, 0), new AcademicData("EXT-001",
                        "Visita de pares", "1", "Ingeniería de Sistemas", "Nombre del docente")), true).block();
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    @Test
    void theRecordIsWrittenWithTheReservationAndSharesItsEventIdWithTheOutbox() {
        Reservation reservation = reserveSpace("ESP-0107", 10);

        ReservationRecordReport record = records.findByReservation(reservation.id()).orElseThrow();

        assertThat(record.eventId()).isEqualTo(jdbc.sql(
                "SELECT id FROM outbox_message WHERE type = 'RESERVATION_RECORD' AND reservation_id = :id")
                .param("id", reservation.id()).query(UUID.class).single());
        assertThat(record.origin()).isEqualTo(ReservationOrigin.ESTUDIANTIL);
        assertThat(record.sanctionable()).isTrue();
        assertThat(record.holder().code()).isEqualTo("2021114001");
        assertThat(record.academic()).isNull();
        assertThat(record.startsAt()).isEqualTo(reservation.startsAt());
        assertThat(record.endsAt()).isEqualTo(reservation.endsAt());
        assertThat(record.loanTermBusinessDays()).isNull();
        assertThat(record.resourceName()).isNull();
        assertThat(record.delivery().status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(record.delivery().attempts()).isZero();
        assertThat(record.delivery().nextAttemptAt()).isNotNull();
    }

    @Test
    void anAssetRecordCarriesTheLoanTermAndItsDueDate() {
        Reservation loan = reserveResources.reserve(ReservationRequest.forAsset(STUDENT, "ACT-000913", TOMORROW,
                LocalTime.of(10, 0))).reservation();

        ReservationRecordReport record = records.findByReservation(loan.id()).orElseThrow();

        assertThat(record.category()).isEqualTo(ResourceCategory.ACTIVO);
        assertThat(record.loanTermBusinessDays()).isEqualTo(loan.loan().termBusinessDays());
        assertThat(record.endsAt()).isEqualTo(loan.endsAt());
    }

    @Test
    void anAcademicRecordHasNoHolderIsNotSanctionableAndReadsItsClassFromTheBlock() {
        Reservation block = academicBlock("ESP-0305", 14);

        ReservationRecordReport record = records.findByReservation(block.id()).orElseThrow();

        assertThat(record.origin()).isEqualTo(ReservationOrigin.ACADEMICO);
        assertThat(record.sanctionable()).isFalse();
        assertThat(record.holder()).isNull();
        assertThat(record.academic().courseCode()).isEqualTo("EXT-001");
        assertThat(record.academic().course()).isEqualTo("Visita de pares");
        assertThat(record.academic().program()).isEqualTo("Ingeniería de Sistemas");
        // La base tampoco deja guardar una académica sancionable o con titular (FR-008, SC-006).
        assertThat(jdbc.sql("SELECT sanctionable FROM reservation_record WHERE reservation_id = :id")
                .param("id", block.id()).query(Boolean.class).single()).isFalse();
    }

    @Test
    void aDeniedReservationLeavesNeitherRecordNorEvent() {
        // Sancionado (RES-003) y recurso ya ocupado: ninguna de las dos deja rastro (FR-007, SC-005).
        assertThatThrownBy(() -> reserveResources.reserve(ReservationRequest.forSpace(SANCTIONED, "ESP-0107",
                TOMORROW, LocalTime.of(10, 0), LocalTime.of(11, 0)))).isInstanceOf(ReservationDeniedException.class);
        reserveSpace("ESP-0107", 10);
        assertThatThrownBy(() -> reserveResources.reserve(ReservationRequest.forSpace(STUDENT, "ESP-0107",
                TOMORROW, LocalTime.of(10, 0), LocalTime.of(11, 0)))).isInstanceOf(ReservationDeniedException.class);

        assertThat(count("reservation")).isEqualTo(1);
        assertThat(count("reservation_record")).isEqualTo(1);
        assertThat(count("outbox_message WHERE type = 'RESERVATION_RECORD'")).isEqualTo(1);
    }

    @Test
    void aReservationHasASingleRecordAndAFailedSecondOneUndoesItsTransaction() {
        Reservation reservation = reserveSpace("ESP-0107", 10);
        ReservationRecordReport first = records.findByReservation(reservation.id()).orElseThrow();
        var copy = new ReservationRecordReport(first.reservationId(), UUID.randomUUID(), first.origin(),
                first.resourceId(), null, first.category(), first.holder(), null, first.startsAt(), first.endsAt(),
                null, first.sentAt(), first.delivery());

        assertThatThrownBy(() -> transactions.required(() -> {
            records.insert(copy);
            return null;
        })).isInstanceOf(IllegalStateException.class);

        assertThat(count("reservation_record")).isEqualTo(1);
        assertThat(records.findByReservation(reservation.id()).orElseThrow().eventId()).isEqualTo(first.eventId());
    }

    @Test
    void theListFiltersByOriginAndDeliveryAndCountsTheSanctionable() {
        Reservation pending = reserveSpace("ESP-0107", 8);
        Reservation sent = reserveSpace("ESP-0412", 9);
        Reservation block = academicBlock("ESP-0305", 14);
        jdbc.sql("UPDATE reservation_record SET delivery_status = 'SENT' WHERE reservation_id = :id")
                .param("id", sent.id()).update();

        var all = records.search(new ReservationRecordFilter(null, null, null, null), new PageRequest(1, 2));
        assertThat(all.page().items()).hasSize(2);
        assertThat(all.summary()).satisfies(summary -> {
            assertThat(summary.total()).isEqualTo(3);
            assertThat(summary.sent()).isEqualTo(1);
            assertThat(summary.pending()).isEqualTo(2);
            assertThat(summary.sanctionable()).isEqualTo(2);
        });

        var academic = records.search(new ReservationRecordFilter(ReservationOrigin.ACADEMICO, null, null, null),
                new PageRequest(1, 20));
        assertThat(academic.page().items()).extracting(ReservationRecordReport::reservationId)
                .containsExactly(block.id());
        assertThat(academic.summary().sanctionable()).isZero();

        var pendingStudents = records.search(new ReservationRecordFilter(ReservationOrigin.ESTUDIANTIL,
                DeliveryStatus.PENDING, null, null), new PageRequest(1, 20));
        assertThat(pendingStudents.page().items()).extracting(ReservationRecordReport::reservationId)
                .containsExactly(pending.id());

        LocalDate today = LocalDate.now(TimeSlot.ZONE);
        assertThat(records.search(new ReservationRecordFilter(null, null, today, today), new PageRequest(1, 20))
                .summary().total()).isEqualTo(3);
        assertThat(records.search(new ReservationRecordFilter(null, null, today.plusDays(1), null),
                new PageRequest(1, 20)).summary().total()).isZero();
    }
}
