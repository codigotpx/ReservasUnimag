package edu.unimagdalena.reservasunimag.domain.model.upload;

import java.util.Set;
import java.util.UUID;

/**
 * Lo que la carga cancelaría al aplicarse, tal como se le mostró a la persona (FR-008): las reservas estudiantiles
 * desplazadas y los bloqueos que salen del horario. Al confirmar se vuelve a calcular y tiene que coincidir.
 */
public record UploadImpact(Set<UUID> reservationsToCancel, Set<UUID> blocksToWithdraw) {

    public static final UploadImpact NONE = new UploadImpact(Set.of(), Set.of());

    public UploadImpact {
        reservationsToCancel = Set.copyOf(reservationsToCancel);
        blocksToWithdraw = Set.copyOf(blocksToWithdraw);
    }
}
