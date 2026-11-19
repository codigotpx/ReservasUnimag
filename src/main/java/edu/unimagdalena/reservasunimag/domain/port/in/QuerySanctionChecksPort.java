package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckFilter;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckPage;

/**
 * UC6 FR-009: qué se le preguntó al Módulo 3 y qué contestó. No responde si alguien está sancionado hoy (FR-002).
 */
public interface QuerySanctionChecksPort {

    SanctionCheckPage list(SanctionCheckFilter filter, PageRequest page);
}
