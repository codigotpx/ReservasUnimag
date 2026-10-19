package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.compliance.Sanction;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionNotice;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;

import java.util.Optional;

/** UC6, lectura: qué hay que avisarle a una persona sobre sus sanciones. */
public interface CheckSanctionsPort {

    /** Vacío cuando la persona está al día. Si el Módulo 3 no responde, el aviso es "no comprobado". */
    Optional<SanctionNotice> checkSanctions(CurrentUser user);

    /**
     * La sanción vigente que termina más tarde, para decidir si se puede reservar. A diferencia del aviso de la
     * consulta, aquí no hay "no comprobado": si el Módulo 3 no responde, lanza
     * {@link edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException} y la reserva no se confirma (UC6 FR-006).
     */
    Optional<Sanction> activeSanction(CurrentUser user);
}
