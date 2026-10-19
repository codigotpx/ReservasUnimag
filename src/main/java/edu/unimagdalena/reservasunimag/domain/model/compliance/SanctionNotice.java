package edu.unimagdalena.reservasunimag.domain.model.compliance;

import java.time.LocalDate;

/**
 * Aviso de sanciones de la consulta (FR-016). {@code reason} y {@code until} solo vienen con
 * {@link Type#ACTIVE_SANCTION}.
 */
public record SanctionNotice(Type type, String reason, LocalDate until, String message) {

    public enum Type {
        ACTIVE_SANCTION,
        /** El Módulo 3 no respondió: no es un error, se vuelve a comprobar al confirmar una reserva. */
        NOT_VERIFIED
    }

    public static SanctionNotice active(Sanction sanction) {
        String until = sanction.end() == null ? "" : " hasta el " + sanction.end();
        return new SanctionNotice(Type.ACTIVE_SANCTION, sanction.reason(), sanction.end(),
                "Tienes una sanción vigente" + until
                        + ". Puedes consultar los recursos, pero no podrás confirmar una reserva.");
    }

    public static SanctionNotice notVerified() {
        return new SanctionNotice(Type.NOT_VERIFIED, null, null,
                "No se pudo comprobar tu situación de sanciones en este momento. "
                        + "Se volverá a comprobar al confirmar una reserva.");
    }
}
