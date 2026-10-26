package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.compliance.Absence;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationAlreadyClosedException;
import edu.unimagdalena.reservasunimag.domain.port.out.AbsenceRepositoryPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Las ausencias de UC9 y su cierre en {@code reservation_closure}, que es lo que impide que una reserva quede a la vez
 * ausente y cancelada (UC11 FR-011). Participa en la transacción del caso de uso; no abre ninguna.
 */
@Component
class AbsencePersistenceAdapter implements AbsenceRepositoryPort {

    private static final String SELECT = """
            SELECT reservation_id, source_event_id, verified_at, reported_at, resource_released, voided, voided_at,
                   voided_reason
            FROM absence
            """;

    private final JdbcClient jdbc;

    AbsencePersistenceAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Absence absence) {
        try {
            jdbc.sql("INSERT INTO reservation_closure (reservation_id, closure_type) VALUES (:id, 'ABSENCE')")
                    .param("id", absence.reservationId())
                    .update();
        } catch (DuplicateKeyException closed) {
            throw new ReservationAlreadyClosedException(absence.reservationId(), closed);
        }
        jdbc.sql("""
                        INSERT INTO absence (reservation_id, source_event_id, verified_at, reported_at, resource_released)
                        VALUES (:id, :sourceEventId, :verifiedAt, :reportedAt, :released)""")
                .param("id", absence.reservationId())
                .param("sourceEventId", absence.sourceEventId())
                .param("verifiedAt", utc(absence.verifiedAt()))
                .param("reportedAt", utc(absence.reportedAt()))
                .param("released", absence.resourceReleased())
                .update();
    }

    @Override
    public Optional<Absence> findByReservation(UUID reservationId) {
        return jdbc.sql(SELECT + "WHERE reservation_id = :id")
                .param("id", reservationId)
                .query(AbsencePersistenceAdapter::toAbsence)
                .optional();
    }

    @Override
    public Map<UUID, Absence> findActiveByReservations(Collection<UUID> reservationIds) {
        if (reservationIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql(SELECT + "WHERE reservation_id IN (:ids) AND NOT voided")
                .param("ids", reservationIds)
                .query(AbsencePersistenceAdapter::toAbsence)
                .stream()
                .collect(Collectors.toMap(Absence::reservationId, Function.identity()));
    }

    @Override
    public void markVoided(UUID reservationId, Instant voidedAt, String reason) {
        jdbc.sql("UPDATE absence SET voided = true, voided_at = :at, voided_reason = :reason WHERE reservation_id = :id")
                .param("at", utc(voidedAt))
                .param("reason", reason == null || reason.length() <= 500 ? reason : reason.substring(0, 500))
                .param("id", reservationId)
                .update();
        jdbc.sql("DELETE FROM reservation_closure WHERE reservation_id = :id AND closure_type = 'ABSENCE'")
                .param("id", reservationId)
                .update();
    }

    private static Absence toAbsence(ResultSet row, int rowNumber) throws SQLException {
        return new Absence(
                row.getObject("reservation_id", UUID.class),
                row.getObject("source_event_id", UUID.class),
                instant(row, "verified_at"),
                instant(row, "reported_at"),
                row.getBoolean("resource_released"),
                row.getBoolean("voided"),
                instant(row, "voided_at"),
                row.getString("voided_reason"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
