package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckFilter;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckPage;

/** El registro de las consultas al Módulo 3 (UC6 FR-009). No guarda sanciones (FR-005). */
public interface SanctionCheckRepositoryPort {

    /**
     * Se guarda en su propia transacción, para que sobreviva al rollback de una reserva denegada, y nunca lanza: si
     * no se puede escribir, se deja constancia en el log y la consulta sigue. Auditar no puede impedir reservar.
     */
    void record(SanctionCheck check);

    /** Las más recientes primero. */
    SanctionCheckPage search(SanctionCheckFilter filter, PageRequest page);
}
