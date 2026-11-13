package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.ReservationJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reservas en JPQL. Las lecturas devuelven {@link ReservationRow}; los cambios de estado son {@code UPDATE} que solo
 * tocan la fila si sigue en el estado esperado, y devuelven cuántas filas cambiaron.
 */
public interface ReservationJpaRepository extends JpaRepository<ReservationJpaEntity, UUID> {

    String SELECT_ROW = """
            SELECT new edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.ReservationRow(
                       r.id, r.userId, r.resourceId, r.resourceCategory, r.origin, r.status,
                       r.startsAt, r.endsAt, r.createdAt, r.useStartedAt,
                       p.termBusinessDays, p.pickedUpAt, p.returnedAt)
            FROM ReservationJpaEntity r
            LEFT JOIN LoanJpaEntity p ON p.reservationId = r.id
            """;

    /**
     * Vigente es toda reserva confirmada de un espacio cuya franja no terminó, y todo préstamo mientras no tenga
     * devolución, aunque ya esté vencido (UC2 FR-008, FR-015).
     */
    String ACTIVE = """
            r.userId = :userId
              AND r.status = CONFIRMADA
              AND ( (r.resourceCategory = ESPACIO AND r.endsAt > :now)
                 OR (r.resourceCategory = ACTIVO AND p.returnedAt IS NULL) )
            """;

    @Query("""
            SELECT r.endsAt
            FROM ReservationJpaEntity r
            LEFT JOIN LoanJpaEntity p ON p.reservationId = r.id
            WHERE\s""" + ACTIVE + " ORDER BY r.endsAt")
    List<Instant> activeEndsOf(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query(SELECT_ROW + "WHERE " + ACTIVE + " ORDER BY r.startsAt, r.id")
    List<ReservationRow> findActiveByHolder(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query(SELECT_ROW + "WHERE r.userId = :userId ORDER BY r.startsAt, r.id")
    List<ReservationRow> findAllByHolder(@Param("userId") UUID userId);

    @Query(SELECT_ROW + "WHERE r.id = :id")
    Optional<ReservationRow> findRowById(@Param("id") UUID id);

    @Query("""
            SELECT new edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.CancellationRow(
                       r.status, r.cancellationOrigin, r.cancelledAt)
            FROM ReservationJpaEntity r
            WHERE r.id = :id""")
    Optional<CancellationRow> findCancellationRow(@Param("id") UUID id);

    /** Solo bloquea la reserva: el préstamo, del lado que puede ser nulo, no se puede bloquear en un LEFT JOIN. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r.id FROM ReservationJpaEntity r WHERE r.id = :id")
    Optional<UUID> lockById(@Param("id") UUID id);

    @Query(SELECT_ROW + """
            WHERE r.origin = ACADEMICO AND r.status = CONFIRMADA AND r.useStartedAt IS NULL
              AND r.startsAt <= :now AND r.endsAt > :now
            ORDER BY r.startsAt, r.id""")
    List<ReservationRow> findAcademicBlocksToStart(@Param("now") Instant now);

    /**
     * Una ocupación {@code [startsAt, endsAt)} se cruza con {@code [from, to)} si empieza antes de {@code to} y termina
     * después de {@code from}: es lo mismo que {@code occupancy && tstzrange(from, to, '[)')}, escrito sin el rango.
     */
    @Query(SELECT_ROW + """
            WHERE r.resourceId IN :resourceIds AND r.origin = ESTUDIANTIL AND r.status = CONFIRMADA
              AND ( (r.startsAt < :to AND r.endsAt > :from)
                 OR (p.pickedUpAt IS NOT NULL AND p.returnedAt IS NULL AND r.startsAt < :to) )
            ORDER BY r.startsAt, r.id""")
    List<ReservationRow> findStudentOccupying(@Param("resourceIds") Collection<String> resourceIds,
                                              @Param("from") Instant from, @Param("to") Instant to);

    @Query(SELECT_ROW + """
            WHERE r.resourceId = :resourceId
              AND r.origin = ESTUDIANTIL
              AND r.status = CONFIRMADA
              AND ( r.startsAt > :now
                 OR (p.pickedUpAt IS NOT NULL AND p.returnedAt IS NULL) )
            ORDER BY r.startsAt, r.id""")
    List<ReservationRow> findUpcomingByResource(@Param("resourceId") String resourceId, @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE ReservationJpaEntity r
            SET r.status = :status, r.cancellationOrigin = :origin, r.cancelledAt = :at, r.version = r.version + 1
            WHERE r.id = :id AND r.status = CONFIRMADA""")
    int markCancelled(@Param("id") UUID id, @Param("status") ReservationStatus status,
                      @Param("origin") CancellationOrigin origin, @Param("at") Instant at);

    @Modifying
    @Query("""
            UPDATE ReservationJpaEntity r SET r.status = FINALIZADA, r.version = r.version + 1
            WHERE r.id = :id AND r.status = CONFIRMADA""")
    int markFinished(@Param("id") UUID id);

    @Modifying
    @Query("""
            UPDATE ReservationJpaEntity r SET r.useStartedAt = :at, r.version = r.version + 1
            WHERE r.id = :id AND r.useStartedAt IS NULL""")
    int markUseStarted(@Param("id") UUID id, @Param("at") Instant at);

    /**
     * Un bloqueo cancelado suelta su huella, para que la misma clase se pueda volver a cargar (UC3 FR-007). Es SQL
     * nativo mientras {@code academic_block} no tenga entidad.
     */
    @Modifying
    @Query(value = "UPDATE academic_block SET session_key = NULL WHERE reservation_id = :id", nativeQuery = true)
    void releaseAcademicBlockKey(@Param("id") UUID id);
}
