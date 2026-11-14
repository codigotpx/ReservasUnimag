package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.ReservationRecordJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * El historial de fichas (UC10 FR-006) en JPQL. La clase de una ficha académica se lee de {@code academic_block} en
 * vez de copiarse, y el próximo intento, de la <em>outbox</em> mientras el evento siga pendiente.
 */
public interface ReservationRecordJpaRepository extends JpaRepository<ReservationRecordJpaEntity, UUID> {

    String SELECT_ROW = """
            SELECT new edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.ReservationRecordRow(
                       r.reservationId, r.eventId, r.origin, r.holderUserId, r.resourceId, r.resourceCategory,
                       r.occupancyStartsAt, r.occupancyEndsAt, r.loanTermBusinessDays, r.sentAt,
                       r.deliveryStatus, r.attempts, r.firstAttemptAt, r.deliveredAt, r.lastError,
                       u.code, u.name, u.role,
                       a.courseCode, a.course, a.groupCode, a.program, a.instructor,
                       o.nextAttemptAt)
            FROM ReservationRecordJpaEntity r
            LEFT JOIN UserJpaEntity u ON u.id = r.holderUserId
            LEFT JOIN AcademicBlockJpaEntity a ON a.reservationId = r.reservationId
            LEFT JOIN OutboxMessageJpaEntity o ON o.id = r.eventId AND o.status = 'PENDING'
            """;

    /**
     * Los filtros opcionales se apagan con un parámetro {@code null}, siempre como parámetro y nunca concatenados.
     * Las fechas llevan {@code cast} porque PostgreSQL no evalúa {@code IS NULL} sobre un parámetro sin tipo. Son días
     * de Bogotá ya convertidos a instantes: {@code to} es el inicio del día siguiente.
     */
    String FILTERS = """
            (:origin IS NULL OR r.origin = :origin)
              AND (:delivery IS NULL OR r.deliveryStatus = :delivery)
              AND (cast(:from AS Instant) IS NULL OR r.sentAt >= :from)
              AND (cast(:to AS Instant) IS NULL OR r.sentAt < :to)
            """;

    @Query(SELECT_ROW + "WHERE r.reservationId = :id")
    Optional<ReservationRecordRow> findRow(@Param("id") UUID reservationId);

    @Query("""
            SELECT new edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.DeliveryCounts(
                       count(r),
                       count(CASE WHEN r.deliveryStatus = SENT THEN 1 END),
                       count(CASE WHEN r.deliveryStatus = PENDING THEN 1 END),
                       count(CASE WHEN r.deliveryStatus = FAILED THEN 1 END),
                       count(CASE WHEN r.sanctionable = true THEN 1 END))
            FROM ReservationRecordJpaEntity r
            WHERE\s""" + FILTERS)
    DeliveryCounts summarize(@Param("origin") ReservationOrigin origin, @Param("delivery") DeliveryStatus delivery,
                             @Param("from") Instant from, @Param("to") Instant to);

    @Query(SELECT_ROW + "WHERE " + FILTERS + "ORDER BY r.sentAt DESC, r.reservationId")
    List<ReservationRecordRow> search(@Param("origin") ReservationOrigin origin,
                                      @Param("delivery") DeliveryStatus delivery, @Param("from") Instant from,
                                      @Param("to") Instant to, Pageable page);
}
