package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckFilter;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckOutcome;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckPage;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckPurpose;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionScope;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.port.out.SanctionCheckRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El registro de las consultas al Módulo 3 (UC6 FR-009). Cada fila va en su propia transacción, como las
 * denegaciones de UC2: tiene que sobrevivir al rollback de la reserva que se denegó con RES-003. Si no se puede
 * escribir, la consulta sigue: auditar no puede impedir reservar.
 */
@Component
class SanctionCheckPersistenceAdapter implements SanctionCheckRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(SanctionCheckPersistenceAdapter.class);
    private static final int MAX_TEXT = 500;

    private final JdbcClient jdbc;
    private final TransactionTemplate ownTransaction;

    SanctionCheckPersistenceAdapter(JdbcClient jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void record(SanctionCheck check) {
        var answered = check.answered();
        try {
            ownTransaction.executeWithoutResult(status -> jdbc.sql("""
                            INSERT INTO sanction_check (id, person_code, asked_by, resource_category, asked_at, outcome,
                                                        answered_sanction_reason, answered_ends_on, answered_scope,
                                                        end_date_missing, answered_absence_count,
                                                        answered_late_returns, duration_ms, failure_detail)
                            VALUES (:id, :personCode, :askedBy, :category, :askedAt, :outcome, :reason, :endsOn,
                                    :scope, :endDateMissing, :absenceCount, :lateReturns, :durationMs, :failure)""")
                    .param("id", check.id())
                    .param("personCode", check.personCode())
                    .param("askedBy", check.purpose().name())
                    .param("category", check.category() == null ? null : check.category().name())
                    .param("askedAt", check.askedAt().atOffset(ZoneOffset.UTC))
                    .param("outcome", check.outcome().name())
                    .param("reason", answered == null ? null : truncate(answered.sanctionReason()))
                    .param("endsOn", answered == null ? null : answered.endsOn())
                    .param("scope", answered == null || answered.scope() == null ? null : answered.scope().name())
                    .param("endDateMissing", answered != null && answered.endDateMissing())
                    .param("absenceCount", answered == null ? null : answered.absenceCount())
                    .param("lateReturns", answered == null ? null : answered.lateReturns())
                    .param("durationMs", (int) Math.min(Integer.MAX_VALUE, check.durationMs()))
                    .param("failure", truncate(check.failure()))
                    .update());
        } catch (DataAccessException | TransactionException failure) {
            log.error("No se pudo registrar la consulta de sanciones {} ({}): {}", check.id(), check.outcome(),
                    failure.getMessage());
            return;
        }
        // Sin el motivo de la sanción ni el código de la persona: son datos personales y quedan en la tabla (T021).
        log.info("Consulta de sanciones {}: {} para {} en {} ms", check.id(), check.outcome(), check.purpose(),
                check.durationMs());
    }

    @Override
    public SanctionCheckPage search(SanctionCheckFilter filter, PageRequest page) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (filter.personCode() != null) {
            conditions.add("person_code = :personCode");
            params.put("personCode", filter.personCode());
        }
        if (filter.outcome() != null) {
            conditions.add("outcome = :outcome");
            params.put("outcome", filter.outcome().name());
        }
        // Días de Bogotá, ambos incluidos: desde el inicio de from hasta antes del inicio del día siguiente a to.
        if (filter.from() != null) {
            conditions.add("asked_at >= :from");
            params.put("from", startOf(filter.from()));
        }
        if (filter.to() != null) {
            conditions.add("asked_at < :to");
            params.put("to", startOf(filter.to().plusDays(1)));
        }
        String where = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions) + " ";

        var summary = jdbc.sql("""
                        SELECT count(*) AS total,
                               count(*) FILTER (WHERE outcome = 'CLEAR') AS clear,
                               count(*) FILTER (WHERE outcome = 'SANCTIONED') AS sanctioned,
                               count(*) FILTER (WHERE outcome = 'NO_HISTORY') AS no_history,
                               count(*) FILTER (WHERE outcome = 'NOT_VERIFIED') AS not_verified
                        FROM sanction_check\s""" + where)
                .params(params)
                .query((row, n) -> new SanctionCheckPage.Summary(row.getLong("total"), row.getLong("clear"),
                        row.getLong("sanctioned"), row.getLong("no_history"), row.getLong("not_verified")))
                .single();

        params.put("limit", page.size());
        params.put("offset", page.offset());
        List<SanctionCheck> items = jdbc.sql("SELECT * FROM sanction_check " + where
                        + "ORDER BY asked_at DESC, id LIMIT :limit OFFSET :offset")
                .params(params)
                .query(SanctionCheckPersistenceAdapter::toCheck)
                .list();
        return new SanctionCheckPage(new Page<>(items, page.page(), page.size(), summary.total()), summary);
    }

    private static SanctionCheck toCheck(ResultSet row, int rowNumber) throws SQLException {
        SanctionCheckOutcome outcome = SanctionCheckOutcome.valueOf(row.getString("outcome"));
        int absences = row.getInt("answered_absence_count");
        boolean answeredReport = !row.wasNull();
        String scope = row.getString("answered_scope");
        SanctionCheck.Answered answered = answeredReport
                ? new SanctionCheck.Answered(row.getString("answered_sanction_reason"),
                        row.getObject("answered_ends_on", LocalDate.class),
                        scope == null ? null : SanctionScope.valueOf(scope), row.getBoolean("end_date_missing"),
                        absences, row.getInt("answered_late_returns"))
                : null;
        String category = row.getString("resource_category");
        return new SanctionCheck(
                row.getObject("id", UUID.class),
                row.getString("person_code"),
                SanctionCheckPurpose.valueOf(row.getString("asked_by")),
                category == null ? null : ResourceCategory.valueOf(category),
                row.getObject("asked_at", OffsetDateTime.class).toInstant(),
                outcome,
                answered,
                row.getInt("duration_ms"),
                row.getString("failure_detail"));
    }

    private static OffsetDateTime startOf(LocalDate day) {
        return day.atStartOfDay(TimeSlot.ZONE).toOffsetDateTime();
    }

    private static String truncate(String text) {
        return text == null || text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
    }
}
