package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.mapper;

import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheck;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.SanctionCheckJpaEntity;

public final class SanctionCheckMapper {

    /** El largo de las columnas de texto: un motivo o un error más largo se corta, no hace fallar el registro. */
    private static final int MAX_TEXT = 500;

    private SanctionCheckMapper() {
    }

    public static SanctionCheckJpaEntity toEntity(SanctionCheck check) {
        var answered = check.answered();
        return new SanctionCheckJpaEntity(check.id(), check.personCode(), check.purpose(), check.category(),
                check.askedAt(), check.outcome(),
                answered == null ? null : truncate(answered.sanctionReason()),
                answered == null ? null : answered.endsOn(),
                answered == null ? null : answered.scope(),
                answered != null && answered.endDateMissing(),
                answered == null ? null : answered.absenceCount(),
                answered == null ? null : answered.lateReturns(),
                (int) Math.min(Integer.MAX_VALUE, check.durationMs()),
                truncate(check.failure()));
    }

    /** Hubo reporte si quedaron los conteos; los datos de la sanción solo vienen con SANCTIONED. */
    public static SanctionCheck toDomain(SanctionCheckJpaEntity entity) {
        SanctionCheck.Answered answered = entity.getAnsweredAbsenceCount() == null ? null
                : new SanctionCheck.Answered(entity.getAnsweredSanctionReason(), entity.getAnsweredEndsOn(),
                        entity.getAnsweredScope(), entity.isEndDateMissing(), entity.getAnsweredAbsenceCount(),
                        entity.getAnsweredLateReturns() == null ? 0 : entity.getAnsweredLateReturns());
        return new SanctionCheck(entity.getId(), entity.getPersonCode(), entity.getAskedBy(),
                entity.getResourceCategory(), entity.getAskedAt(), entity.getOutcome(), answered,
                entity.getDurationMs(), entity.getFailureDetail());
    }

    private static String truncate(String text) {
        return text == null || text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
    }
}
