package edu.unimagdalena.reservasunimag.domain.model.compliance;

/** Qué contestó el Módulo 3, en los cuatro casos del spec de UC6. Una sanción vencida es {@link #CLEAR}. */
public enum SanctionCheckOutcome {
    CLEAR,
    SANCTIONED,
    NO_HISTORY,
    NOT_VERIFIED
}
