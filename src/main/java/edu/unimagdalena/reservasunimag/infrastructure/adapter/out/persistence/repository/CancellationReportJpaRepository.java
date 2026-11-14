package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.DeliveryStatus;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.CancellationReportJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** El historial de cancelaciones (UC11 FR-010) en JPQL, con la misma forma que {@link ReservationRecordJpaRepository}. */
public interface CancellationReportJpaRepository extends JpaRepository<CancellationReportJpaEntity, UUID> {

    String SELECT_ROW = """
            SELECT new edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.CancellationReportRow(
                       c.reservationId, c.eventId, c.cancellationOrigin, c.reason, c.noticeMinutes, c.cancelledAt,
                       c.resourceId, c.resourceCategory, c.holderUserId, c.releasedStartsAt, c.releasedEndsAt,
                       c.deliveryStatus, c.attempts, c.firstAttemptAt, c.deliveredAt, c.lastError,
                       u.code, u.name, u.role,
                       o.nextAttemptAt)
            FROM CancellationReportJpaEntity c
            LEFT JOIN UserJpaEntity u ON u.id = c.holderUserId
            LEFT JOIN OutboxMessageJpaEntity o ON o.id = c.eventId AND o.status = 'PENDING'
            """;

    /** Igual que en {@link ReservationRecordJpaRepository#FILTERS}, sobre el instante de la cancelación. */
    String FILTERS = """
            (:origin IS NULL OR c.cancellationOrigin = :origin)
              AND (:delivery IS NULL OR c.deliveryStatus = :delivery)
              AND (cast(:from AS Instant) IS NULL OR c.cancelledAt >= :from)
              AND (cast(:to AS Instant) IS NULL OR c.cancelledAt < :to)
            """;

    @Query(SELECT_ROW + "WHERE c.reservationId = :id")
    Optional<CancellationReportRow> findRow(@Param("id") UUID reservationId);

    @Query("""
            SELECT new edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository.DeliveryCounts(
                       count(c),
                       count(CASE WHEN c.deliveryStatus = SENT THEN 1 END),
                       count(CASE WHEN c.deliveryStatus = PENDING THEN 1 END),
                       count(CASE WHEN c.deliveryStatus = FAILED THEN 1 END),
                       0L)
            FROM CancellationReportJpaEntity c
            WHERE\s""" + FILTERS)
    DeliveryCounts summarize(@Param("origin") CancellationOrigin origin, @Param("delivery") DeliveryStatus delivery,
                             @Param("from") Instant from, @Param("to") Instant to);

    @Query(SELECT_ROW + "WHERE " + FILTERS + "ORDER BY c.cancelledAt DESC, c.reservationId")
    List<CancellationReportRow> search(@Param("origin") CancellationOrigin origin,
                                       @Param("delivery") DeliveryStatus delivery, @Param("from") Instant from,
                                       @Param("to") Instant to, Pageable page);
}
