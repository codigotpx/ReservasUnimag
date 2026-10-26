package edu.unimagdalena.reservasunimag.domain.usecase.receivecheckout;

import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutKind;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutRejection;
import edu.unimagdalena.reservasunimag.domain.model.compliance.CheckOutReport;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.port.out.AbsenceRepositoryPort;

import java.util.Optional;

/**
 * Las dos tablas de rechazo de UC12: la de un activo (FR-008, FR-010) y la de un espacio (FR-011, FR-013). Un dictamen
 * en un activo se rechaza en vez de ignorarse, para que el Módulo 3 sepa que no debe mandarlo.
 */
public class CheckOutValidator {

    private final AbsenceRepositoryPort absences;

    public CheckOutValidator(AbsenceRepositoryPort absences) {
        this.absences = absences;
    }

    /** Vacío si el check-out se puede aplicar. {@code SLOT_NOT_FINISHED} se decide aquí pero se arma con su fecha. */
    public Optional<CheckOutRejection> rejectionFor(Reservation reservation, CheckOutReport report) {
        return CheckOutKind.of(reservation.category()) == CheckOutKind.ASSET_RETURN
                ? assetRejection(reservation, report)
                : spaceRejection(reservation, report);
    }

    private static Optional<CheckOutRejection> assetRejection(Reservation reservation, CheckOutReport report) {
        if (reservation.status().isCancellation()) {
            return Optional.of(CheckOutRejection.RESERVATION_CANCELLED);
        }
        if (report.verdict() != null || report.carriesDamage()) {
            return Optional.of(CheckOutRejection.VERDICT_REQUIRED);
        }
        if (!reservation.loan().pickedUp()) {
            return Optional.of(CheckOutRejection.ASSET_NOT_PICKED_UP);
        }
        return Optional.empty();
    }

    private Optional<CheckOutRejection> spaceRejection(Reservation reservation, CheckOutReport report) {
        boolean absent = absences.findByReservation(reservation.id()).filter(a -> !a.voided()).isPresent();
        if (reservation.status().isCancellation() || absent) {
            return Optional.of(CheckOutRejection.RESERVATION_NOT_USED);
        }
        if (report.verdict() == null) {
            return Optional.of(CheckOutRejection.VERDICT_REQUIRED);
        }
        // La revisión es posterior al uso: se mide con lo que pasó, no con cuándo nos llegó.
        if (report.occurredAt().isBefore(reservation.endsAt())) {
            return Optional.of(CheckOutRejection.SLOT_NOT_FINISHED);
        }
        return Optional.empty();
    }
}
