package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.compliance.ComplianceReport;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;

/** Módulo 3: cumplimiento y sanciones. */
public interface CompliancePort {

    /**
     * El reporte de una sola persona por su código institucional (UC6 FR-007). Una persona sin historial devuelve
     * {@link ComplianceReport#empty()}. Si el módulo no responde, lanza {@link ExternalServiceUnavailableException}.
     */
    ComplianceReport report(String personCode);
}
