package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.TestcontainersConfiguration;
import edu.unimagdalena.reservasunimag.domain.model.resource.ScheduledUse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.Database.clean;
import static org.assertj.core.api.Assertions.assertThat;

/** El calendario de ocupación contra PostgreSQL: qué entra y qué se queda fuera. */
@SpringBootTest(properties = "reservations.outbox.enabled=false")
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class ScheduledUsePersistenceAdapterIT {

    private static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant WEEK_START = Instant.parse("2026-11-02T05:00:00Z");
    private static final Instant WEEK_END = Instant.parse("2026-11-07T05:00:00Z");

    @Autowired
    private ScheduledUsePersistenceAdapter adapter;
    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyTables() {
        clean(jdbc);
    }

    private UUID reservation(String resourceId, String origin, String status, String start, String end) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO reservation (id, user_id, resource_id, resource_category, origin, status, starts_at, ends_at)
                        VALUES (:id, :userId, :resourceId, 'ESPACIO', :origin, :status,
                                CAST(:start AS timestamptz), CAST(:end AS timestamptz))""")
                .param("id", id)
                .param("userId", "ACADEMICO".equals(origin) ? null : STUDENT, java.sql.Types.OTHER)
                .param("resourceId", resourceId)
                .param("origin", origin)
                .param("status", status)
                .param("start", start)
                .param("end", end)
                .update();
        return id;
    }

    private void extraordinary(UUID reservationId) {
        jdbc.sql("""
                        INSERT INTO academic_block (reservation_id, type, course, program, instructor, course_code, group_code)
                        VALUES (:id, 'EXTRAORDINARIO', 'Visita de pares académicos', 'Ingeniería de Sistemas', NULL,
                                'EXT-001', '1')""")
                .param("id", reservationId)
                .update();
    }

    @Test
    void theWeekBringsConfirmedUsesInOrderWithTheirKind() {
        reservation("ESP-0412", "ESTUDIANTIL", "CONFIRMADA", "2026-11-03T19:00:00Z", "2026-11-03T21:00:00Z");
        extraordinary(reservation("ESP-0412", "ACADEMICO", "CONFIRMADA", "2026-11-02T13:00:00Z",
                "2026-11-02T16:00:00Z"));

        var uses = adapter.findByResource("ESP-0412", WEEK_START, WEEK_END);

        assertThat(uses).extracting(ScheduledUse::kind)
                .containsExactly(ScheduledUse.Kind.EXTRAORDINARY, ScheduledUse.Kind.STUDENT_RESERVATION);
        assertThat(uses.get(0).course()).isEqualTo("Visita de pares académicos");
        assertThat(uses.get(0).program()).isEqualTo("Ingeniería de Sistemas");
        assertThat(uses.get(1).course()).isNull();
    }

    @Test
    void cancelledOtherResourcesAndOtherWeeksAreLeftOut() {
        reservation("ESP-0412", "ESTUDIANTIL", "CANCELADA", "2026-11-03T19:00:00Z", "2026-11-03T21:00:00Z");
        reservation("ESP-0413", "ESTUDIANTIL", "CONFIRMADA", "2026-11-03T19:00:00Z", "2026-11-03T21:00:00Z");
        reservation("ESP-0412", "ESTUDIANTIL", "CONFIRMADA", "2026-11-09T19:00:00Z", "2026-11-09T21:00:00Z");

        assertThat(adapter.findByResource("ESP-0412", WEEK_START, WEEK_END)).isEmpty();
    }
}
