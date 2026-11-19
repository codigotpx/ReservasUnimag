package edu.unimagdalena.reservasunimag.domain.model.compliance;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;

import java.time.Duration;
import java.time.Instant;

/**
 * Los préstamos vencidos sin check-out, de los más atrasados a los menos (FR-009, SC-005). {@code lossThreshold} es el
 * plazo tras el vencimiento a partir del cual se declararía perdido; {@code nearLossThreshold} cuenta, sobre el
 * conjunto completo, los que están a menos de 48 horas de él.
 */
public record PendingReturns(Page<PendingCheckOut> page, long nearLossThreshold, Duration lossThreshold,
                             Instant now) {

    public static final Duration NEAR_THRESHOLD = Duration.ofHours(48);

    public Instant lossThresholdAt(PendingCheckOut pending) {
        return pending.reservation().endsAt().plus(lossThreshold);
    }

    public long overdueDays(PendingCheckOut pending) {
        return Duration.between(pending.reservation().endsAt(), now).toDays();
    }
}
