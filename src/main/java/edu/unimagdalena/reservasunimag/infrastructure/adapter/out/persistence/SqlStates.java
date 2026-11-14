package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import java.sql.SQLException;

/**
 * Los códigos de PostgreSQL que los adaptadores traducen a errores del dominio. Se buscan en la cadena de causas,
 * porque JPA envuelve la {@link SQLException} en sus propias excepciones.
 */
final class SqlStates {

    /** Violación de una clave única o primaria. */
    static final String UNIQUE_VIOLATION = "23505";
    /** Violación de una restricción de exclusión. */
    static final String EXCLUSION_VIOLATION = "23P01";
    /** Un <em>deadlock</em>: PostgreSQL abortó esta transacción para dejar seguir a otra. */
    static final String DEADLOCK = "40P01";

    private SqlStates() {
    }

    static boolean has(Throwable failure, String sqlState) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sqlState.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
