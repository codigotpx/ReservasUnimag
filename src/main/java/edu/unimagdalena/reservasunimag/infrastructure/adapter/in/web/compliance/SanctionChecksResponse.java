package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.compliance;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckPage;
import edu.unimagdalena.reservasunimag.infrastructure.time.IsoTimes;

import java.util.List;

/**
 * El listado de UC6 Contratos §3. {@code answered} es lo que contestó el Módulo 3: falta cuando no contestó o no
 * conoce a la persona, y sus datos de sanción solo vienen con {@code SANCTIONED}. {@code summary.notVerified} es el
 * número que vigila SC-004.
 */
record SanctionChecksResponse(List<CheckBody> checks, PaginationBody pagination, SummaryBody summary) {

    static SanctionChecksResponse from(SanctionCheckPage result) {
        var page = result.page();
        var summary = result.summary();
        return new SanctionChecksResponse(
                page.items().stream().map(CheckBody::from).toList(),
                new PaginationBody(page.page(), page.pageSize(), page.totalPages(), page.total()),
                new SummaryBody(summary.total(), summary.clear(), summary.sanctioned(), summary.noHistory(),
                        summary.notVerified()));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CheckBody(String id, String personCode, String askedBy, String resourceCategory, String askedAt,
                     String outcome, AnsweredBody answered, String failureDetail, long durationMs) {

        static CheckBody from(SanctionCheck check) {
            var answered = check.answered();
            return new CheckBody(check.id().toString(), check.personCode(), check.purpose().name(),
                    check.category() == null ? null : check.category().name(), IsoTimes.offset(check.askedAt()),
                    check.outcome().name(),
                    answered == null ? null : new AnsweredBody(answered.sanctionReason(),
                            answered.endsOn() == null ? null : answered.endsOn().toString(),
                            answered.scope() == null ? null : answered.scope().name(),
                            answered.scope() == null ? null : answered.endDateMissing(),
                            answered.absenceCount(), answered.lateReturns()),
                    check.failure(), check.durationMs());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AnsweredBody(String sanctionReason, String endsOn, String scope, Boolean endDateMissing, int absenceCount,
                        int lateReturns) {
    }

    record PaginationBody(int page, int pageSize, int totalPages, long total) {
    }

    record SummaryBody(long total, long clear, long sanctioned, long noHistory, long notVerified) {
    }
}
