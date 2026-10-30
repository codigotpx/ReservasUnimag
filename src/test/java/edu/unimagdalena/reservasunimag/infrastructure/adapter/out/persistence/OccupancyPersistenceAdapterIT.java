package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Occupancy;
import edu.unimagdalena.reservasunimag.domain.model.reservation.OccupancyType;
import edu.unimagdalena.reservasunimag.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** La consulta de ocupaciones contra PostgreSQL real, con rangos {@code tstzrange} (UC1 T021). */
@SpringBootTest
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class OccupancyPersistenceAdapterIT {

    private static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant TEN = Instant.parse("2026-09-01T15:00:00Z");
    private static final Instant TWELVE = Instant.parse("2026-09-01T17:00:00Z");

    @Autowired
    private OccupancyPersistenceAdapter adapter;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void clean() {
        edu.unimagdalena.reservasunimag.support.Database.clean(jdbc);
    }

    private UUID reservation(String resourceId, String category, String origin, String status, String start,
                             String end) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO reservation (id, user_id, resource_id, resource_category, origin, status, starts_at, ends_at)
                        VALUES (:id, :userId, :resourceId, :category, :origin, :status,
                                CAST(:start AS timestamptz), CAST(:end AS timestamptz))
                        """)
                .param("id", id)
                .param("userId", origin.equals("ACADEMICO") ? null : STUDENT, java.sql.Types.OTHER)
                .param("resourceId", resourceId)
                .param("category", category)
                .param("origin", origin)
                .param("status", status)
                .param("start", start)
                .param("end", end)
                .update();
        return id;
    }

    private void loan(UUID reservationId, String pickedUp, String returned) {
        jdbc.sql("""
                        INSERT INTO loan (reservation_id, term_business_days, picked_up_at, returned_at)
                        VALUES (:id, 3, CAST(:pickedUp AS timestamptz), CAST(:returned AS timestamptz))
                        """)
                .param("id", reservationId)
                .param("pickedUp", pickedUp)
                .param("returned", returned)
                .update();
    }

    @Test
    void aConfirmedSpaceReservationThatOverlapsOccupiesTheSpace() {
        reservation("ESP-1", "ESPACIO", "ESTUDIANTIL", "CONFIRMADA", "2026-09-01T10:30:00Z", "2026-09-01T16:00:00Z");

        List<Occupancy> found = adapter.findOccupancies(List.of("ESP-1"), TEN, TWELVE);

        assertThat(found).singleElement().satisfies(occupancy -> {
            assertThat(occupancy.type()).isEqualTo(OccupancyType.SPACE_RESERVATION);
            assertThat(occupancy.resourceId()).isEqualTo("ESP-1");
            assertThat(occupancy.startsAt()).isEqualTo(Instant.parse("2026-09-01T10:30:00Z"));
            assertThat(occupancy.endsAt()).isEqualTo(Instant.parse("2026-09-01T16:00:00Z"));
        });
    }

    @Test
    void touchingRangesDoNotOverlapButAMinuteDoes() {
        reservation("ESP-1", "ESPACIO", "ESTUDIANTIL", "CONFIRMADA", "2026-09-01T13:00:00Z", "2026-09-01T15:00:00Z");
        reservation("ESP-2", "ESPACIO", "ESTUDIANTIL", "CONFIRMADA", "2026-09-01T13:00:00Z", "2026-09-01T15:01:00Z");
        reservation("ESP-3", "ESPACIO", "ESTUDIANTIL", "CONFIRMADA", "2026-09-01T17:00:00Z", "2026-09-01T19:00:00Z");

        assertThat(adapter.findOccupancies(List.of("ESP-1", "ESP-2", "ESP-3"), TEN, TWELVE))
                .extracting(Occupancy::resourceId).containsExactly("ESP-2");
    }

    @Test
    void cancelledAndFinishedReservationsDoNotOccupy() {
        reservation("ESP-1", "ESPACIO", "ESTUDIANTIL", "CANCELADA", "2026-09-01T15:00:00Z", "2026-09-01T17:00:00Z");
        reservation("ESP-1", "ESPACIO", "ESTUDIANTIL", "CANCELADA_POR_PRIORIDAD_ACADEMICA",
                "2026-09-01T15:00:00Z", "2026-09-01T17:00:00Z");
        reservation("ESP-1", "ESPACIO", "ESTUDIANTIL", "FINALIZADA", "2026-09-01T15:00:00Z", "2026-09-01T17:00:00Z");

        assertThat(adapter.findOccupancies(List.of("ESP-1"), TEN, TWELVE)).isEmpty();
    }

    @Test
    void anAcademicBlockIsReportedAsABlock() {
        reservation("ESP-1", "ESPACIO", "ACADEMICO", "CONFIRMADA", "2026-09-01T15:00:00Z", "2026-09-01T17:00:00Z");

        assertThat(adapter.findOccupancies(List.of("ESP-1"), TEN, TWELVE))
                .singleElement().extracting(Occupancy::type).isEqualTo(OccupancyType.ACADEMIC_BLOCK);
    }

    @Test
    void aHeldLoanIsHeldAndAPickedUpOneIsInUse() {
        loan(reservation("ACT-1", "ACTIVO", "ESTUDIANTIL", "CONFIRMADA", "2026-09-01T15:00:00Z",
                "2026-09-04T17:00:00Z"), null, null);
        loan(reservation("ACT-2", "ACTIVO", "ESTUDIANTIL", "CONFIRMADA", "2026-09-01T15:00:00Z",
                "2026-09-04T17:00:00Z"), "2026-09-01T15:05:00Z", null);

        assertThat(adapter.findOccupancies(List.of("ACT-1", "ACT-2"), TEN, TWELVE))
                .extracting(Occupancy::resourceId, Occupancy::type)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("ACT-1", OccupancyType.LOAN_HELD),
                        org.assertj.core.groups.Tuple.tuple("ACT-2", OccupancyType.LOAN_PICKED_UP));
    }

    @Test
    void anOverdueLoanThatWasNeverReturnedStillOccupiesWithNoEnd() {
        loan(reservation("ACT-1", "ACTIVO", "ESTUDIANTIL", "CONFIRMADA", "2026-08-20T15:00:00Z",
                "2026-08-25T17:00:00Z"), "2026-08-20T15:05:00Z", null);

        assertThat(adapter.findOccupancies(List.of("ACT-1"), TEN, TWELVE)).singleElement().satisfies(occupancy -> {
            assertThat(occupancy.type()).isEqualTo(OccupancyType.LOAN_PICKED_UP);
            assertThat(occupancy.endsAt()).isNull();
            // Sigue sabiendo cuándo vencía, para decir que ya venció (UC8 FR-012).
            assertThat(occupancy.dueAt()).isEqualTo(Instant.parse("2026-08-25T17:00:00Z"));
        });
    }

    @Test
    void aReturnedLoanDoesNotOccupyOutsideItsRange() {
        loan(reservation("ACT-1", "ACTIVO", "ESTUDIANTIL", "CONFIRMADA", "2026-08-20T15:00:00Z",
                "2026-08-25T17:00:00Z"), "2026-08-20T15:05:00Z", "2026-08-24T10:00:00Z");

        assertThat(adapter.findOccupancies(List.of("ACT-1"), TEN, TWELVE)).isEmpty();
    }

    @Test
    void anOpenLoanThatStartsAfterTheQueriedRangeDoesNotOccupyIt() {
        loan(reservation("ACT-1", "ACTIVO", "ESTUDIANTIL", "CONFIRMADA", "2026-09-02T15:00:00Z",
                "2026-09-05T17:00:00Z"), "2026-09-02T15:05:00Z", null);

        assertThat(adapter.findOccupancies(List.of("ACT-1"), TEN, TWELVE)).isEmpty();
    }

    @Test
    void onlyTheRequestedResourcesComeBackAndAnEmptyListQueriesNothing() {
        reservation("ESP-1", "ESPACIO", "ESTUDIANTIL", "CONFIRMADA", "2026-09-01T15:00:00Z", "2026-09-01T17:00:00Z");
        reservation("ESP-2", "ESPACIO", "ESTUDIANTIL", "CONFIRMADA", "2026-09-01T15:00:00Z", "2026-09-01T17:00:00Z");

        assertThat(adapter.findOccupancies(List.of("ESP-2"), TEN, TWELVE))
                .extracting(Occupancy::resourceId).containsExactly("ESP-2");
        assertThat(adapter.findOccupancies(List.of(), TEN, TWELVE)).isEmpty();
    }
}
