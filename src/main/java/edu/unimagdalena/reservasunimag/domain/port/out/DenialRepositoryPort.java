package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.error.DenialCode;

/** Registro de denegaciones (UC2 FR-005; alimenta la consulta de sanciones UC6). */
public interface DenialRepositoryPort {

    /** Guarda la denegación: solo el identificador del titular y el código salen; sin datos personales. */
    void save(String userId, DenialCode denialCode, String detail);
}
