package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.repository;

import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence.entity.InboxMessageJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface InboxMessageJpaRepository extends JpaRepository<InboxMessageJpaEntity, UUID> {

    /**
     * Registra el evento y devuelve cuántas filas escribió: 0 si ya estaba. Es un {@code INSERT} de HQL con
     * {@code ON CONFLICT DO NOTHING} y no un {@code save()}: dos entregas simultáneas del mismo evento no chocan con la
     * clave primaria, la segunda simplemente no escribe nada, y ninguna de las dos aborta su transacción.
     */
    @Modifying
    @Query("""
            INSERT INTO InboxMessageJpaEntity (eventId, topic, type, reservationId, payload)
            VALUES (:eventId, :topic, :type, :reservationId, :payload)
            ON CONFLICT (eventId) DO NOTHING""")
    int insertIfAbsent(@Param("eventId") UUID eventId, @Param("topic") String topic, @Param("type") String type,
                       @Param("reservationId") UUID reservationId, @Param("payload") String payload);
}
