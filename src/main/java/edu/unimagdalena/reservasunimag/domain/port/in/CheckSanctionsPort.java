package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionNotice;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;

import java.util.Optional;

/** UC6, lectura: qué hay que avisarle a una persona sobre sus sanciones. */
public interface CheckSanctionsPort {

    /** Vacío cuando la persona está al día. Si el Módulo 3 no responde, el aviso es "no comprobado". */
    Optional<SanctionNotice> checkSanctions(CurrentUser user);
}
