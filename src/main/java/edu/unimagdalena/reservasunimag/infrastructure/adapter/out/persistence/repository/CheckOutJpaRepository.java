package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.CheckOutJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Los check-out y las dos listas de pendientes de UC12, que son consultas JPQL sobre las reservas y no tablas
 * propias. Las páginas llegan como {@link Pageable}, que Spring Data aplica como {@code LIMIT} y {@code OFFSET}.
 */
public interface CheckOutJpaRepository extends JpaRepository<CheckOutJpaEntity, UUID> {

    String SELECT_PENDING = """
            SELECT new edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.PendingCheckOutRow(
                       r.id, r.userId, r.resourceId, r.resourceCategory, r.origin, r.status,
                       r.startsAt, r.endsAt, r.createdAt, r.useStartedAt,
                       p.termBusinessDays, p.pickedUpAt, p.returnedAt,
                       u.code, u.name, u.role)
            FROM ReservationJpaEntity r
            LEFT JOIN LoanJpaEntity p ON p.reservationId = r.id
            LEFT JOIN UserJpaEntity u ON u.id = r.userId
            """;

    String COUNTS = "SELECT new edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository"
            + ".PendingCounts(";

    /** Un préstamo entregado, sin devolver y vencido (FR-009). */
    String PENDING_RETURN = """
            r.resourceCategory = ACTIVO AND r.status = CONFIRMADA
              AND p.pickedUpAt IS NOT NULL AND p.returnedAt IS NULL AND r.endsAt < :now
            """;

    /**
     * Un espacio cuya franja terminó, que se usó —ni cancelado ni con ausencia vigente— y que no tiene check-out
     * (FR-014). Un espacio sigue CONFIRMADA al terminar su franja; FINALIZADA solo entra si su ausencia se anuló. Los
     * filtros opcionales se apagan con un parámetro {@code null}, siempre como parámetro y nunca concatenados. Las
     * fechas llevan {@code cast}: un {@code null} de fecha llega sin tipo, y PostgreSQL no puede evaluar
     * {@code IS NULL} sobre un parámetro sin tipo.
     */
    String PENDING_REVIEW = """
            r.resourceCategory = ESPACIO AND r.status IN (CONFIRMADA, FINALIZADA) AND r.endsAt <= :now
              AND NOT EXISTS (SELECT 1 FROM CheckOutJpaEntity c WHERE c.reservationId = r.id)
              AND NOT EXISTS (SELECT 1 FROM AbsenceJpaEntity a WHERE a.reservationId = r.id AND a.voided = false)
              AND (cast(:from AS Instant) IS NULL OR r.startsAt >= :from)
              AND (cast(:to AS Instant) IS NULL OR r.startsAt < :to)
              AND (:origin IS NULL OR r.origin = :origin)
              AND (:resourceId IS NULL OR r.resourceId = :resourceId)
            """;

    /** El total y los que están a menos de 48 horas del umbral de pérdida: vencen antes de {@code nearDue}. */
    @Query(COUNTS + """
            count(r), count(CASE WHEN r.endsAt <= :nearDue THEN 1 END))
            FROM ReservationJpaEntity r JOIN LoanJpaEntity p ON p.reservationId = r.id
            WHERE\s""" + PENDING_RETURN)
    PendingCounts countPendingReturns(@Param("now") Instant now, @Param("nearDue") Instant nearDue);

    @Query(SELECT_PENDING + "WHERE " + PENDING_RETURN + "ORDER BY r.endsAt, r.id")
    List<PendingCheckOutRow> findPendingReturns(@Param("now") Instant now, Pageable page);

    /** Las estudiantiles y las académicas. */
    @Query(COUNTS + """
            count(CASE WHEN r.origin = ESTUDIANTIL THEN 1 END), count(CASE WHEN r.origin = ACADEMICO THEN 1 END))
            FROM ReservationJpaEntity r
            WHERE\s""" + PENDING_REVIEW)
    PendingCounts countPendingReviews(@Param("now") Instant now, @Param("from") Instant from,
                                      @Param("to") Instant to, @Param("origin") ReservationOrigin origin,
                                      @Param("resourceId") String resourceId);

    @Query(SELECT_PENDING + "WHERE " + PENDING_REVIEW + "ORDER BY r.endsAt, r.id")
    List<PendingCheckOutRow> findPendingReviews(@Param("now") Instant now, @Param("from") Instant from,
                                                @Param("to") Instant to, @Param("origin") ReservationOrigin origin,
                                                @Param("resourceId") String resourceId, Pageable page);
}
