package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.compliance.Sanction;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionNotice;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;

import java.util.Optional;

/**
 * UC6: la situación de una persona ante el Módulo 3. No tiene pantalla (FR-002): solo la llaman Consultar recursos
 * (para avisar) y Reservar recursos (para decidir), y cada llamada queda registrada (FR-009). No hay caché: la
 * comprobación que vale es la del momento de confirmar.
 */
public interface CheckSanctionsPort {

    /**
     * Para Consultar recursos: vacío cuando la persona está al día. Si el Módulo 3 no responde, el aviso es "no
     * comprobado".
     */
    Optional<SanctionNotice> checkSanctions(CurrentUser user);

    /**
     * Para Reservar recursos: la sanción vigente que bloquea {@code category} y termina más tarde. A diferencia del
     * aviso, aquí no hay "no comprobado": si el Módulo 3 no responde, lanza
     * {@link edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException} y la reserva no se
     * confirma (FR-006).
     */
    Optional<Sanction> activeSanction(CurrentUser user, ResourceCategory category);
}
