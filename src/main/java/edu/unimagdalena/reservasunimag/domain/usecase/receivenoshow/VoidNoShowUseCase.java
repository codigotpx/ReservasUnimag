package edu.unimagdalena.reservasunimag.domain.usecase.receivenoshow;

import edu.unimagdalena.reservasunimag.domain.model.compliance.Absence;
import edu.unimagdalena.reservasunimag.domain.port.in.VoidNoShowPort;
import edu.unimagdalena.reservasunimag.domain.port.out.AbsenceRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;

import java.util.Optional;
import java.util.UUID;

/**
 * UC9 FR-012. Marca la ausencia como anulada, con su fecha y su motivo, y suelta el cierre de la reserva. La reserva
 * no vuelve a {@code CONFIRMADA}: el recurso ya volvió a la oferta, puede que otra persona lo tenga, y resucitarla
 * chocaría con esa ocupación (edge case <em>Reporte equivocado</em>).
 */
public class VoidNoShowUseCase implements VoidNoShowPort {

    private final AbsenceRepositoryPort absences;
    private final TransactionPort transactions;
    private final Clock clock;

    public VoidNoShowUseCase(AbsenceRepositoryPort absences, TransactionPort transactions, Clock clock) {
        this.absences = absences;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public boolean voidReport(UUID reservationId, String reason) {
        return transactions.required(() -> {
            Optional<Absence> absence = absences.findByReservation(reservationId);
            if (absence.isEmpty() || absence.get().voided()) {
                return false;
            }
            absences.markVoided(reservationId, clock.now(), reason);
            return true;
        });
    }
}
