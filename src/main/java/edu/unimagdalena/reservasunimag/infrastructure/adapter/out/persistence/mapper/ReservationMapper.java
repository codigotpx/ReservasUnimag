package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.mapper;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.LoanJpaEntity;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.ReservationJpaEntity;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.ReservationRow;

public final class ReservationMapper {

    private ReservationMapper() {
    }

    public static ReservationJpaEntity toEntity(Reservation reservation) {
        return new ReservationJpaEntity(reservation.id(), reservation.userId(), reservation.resourceId(),
                reservation.category(), reservation.origin(), reservation.status(), reservation.startsAt(),
                reservation.endsAt(), reservation.createdAt());
    }

    /** {@code null} si la reserva no tiene préstamo. */
    public static LoanJpaEntity toLoanEntity(Reservation reservation) {
        Loan loan = reservation.loan();
        return loan == null ? null : new LoanJpaEntity(reservation.id(), loan.termBusinessDays(), loan.pickedUpAt(),
                loan.returnedAt());
    }

    public static Reservation toDomain(ReservationRow row) {
        // Un bloqueo académico sobre un activo no tiene fila de préstamo.
        Loan loan = row.termBusinessDays() != null
                ? new Loan(row.termBusinessDays(), row.pickedUpAt(), row.returnedAt())
                : null;
        return new Reservation(row.id(), row.userId(), row.resourceId(), row.resourceCategory(), row.origin(),
                row.status(), row.startsAt(), row.endsAt(), row.createdAt(), loan, row.useStartedAt());
    }
}
