package edu.unimagdalena.reservasunimag.support;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

/** Limpieza y datos de apoyo para las pruebas que usan PostgreSQL de verdad. */
public final class Database {

    private Database() {
    }

    /** Borra todo lo que escriben las reservas; deja los usuarios sembrados del perfil local. */
    public static void clean(JdbcClient jdbc) {
        jdbc.sql("DELETE FROM outbox_message").update();
        jdbc.sql("DELETE FROM denial").update();
        jdbc.sql("DELETE FROM academic_block").update();
        jdbc.sql("DELETE FROM loan").update();
        jdbc.sql("DELETE FROM reservation").update();
        jdbc.sql("DELETE FROM app_user WHERE code LIKE 'IT-%'").update();
    }

    /** Un estudiante extra para pruebas de concurrencia; su código no tiene sanciones en el Módulo 3 falso. */
    public static UUID student(JdbcClient jdbc, String code) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO app_user (id, code, name, email, password_hash, role)
                        VALUES (:id, :code, :name, :email, 'x', 'ESTUDIANTE')""")
                .param("id", id)
                .param("code", code)
                .param("name", "Estudiante " + code)
                .param("email", code.toLowerCase() + "@unimagdalena.edu.co")
                .update();
        return id;
    }

    /** Una reserva de espacio con su evento pendiente en la outbox, como la deja UC2; devuelve el id de la reserva. */
    public static UUID reservationWithPendingEvent(JdbcClient jdbc, int hourOffset) {
        UUID reservationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO reservation (id, user_id, resource_id, resource_category, origin, status, starts_at, ends_at)
                        VALUES (:id, '00000000-0000-0000-0000-000000000001', :resource, 'ESPACIO', 'ESTUDIANTIL',
                                'CONFIRMADA', now() + make_interval(hours => :h), now() + make_interval(hours => :h + 1))""")
                .param("id", reservationId)
                .param("resource", "ESP-IT-" + hourOffset)
                .param("h", hourOffset)
                .update();
        jdbc.sql("""
                        INSERT INTO outbox_message (id, type, reservation_id, payload)
                        VALUES (:id, 'RESERVATION_RECORD', :reservationId, CAST(:payload AS jsonb))""")
                .param("id", eventId)
                .param("reservationId", reservationId)
                .param("payload", "{\"eventId\": \"" + eventId + "\", \"type\": \"ReservationRecordCreated\", \"version\": 1}")
                .update();
        return reservationId;
    }
}
