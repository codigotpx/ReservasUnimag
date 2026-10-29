package edu.unimagdalena.reservasunimag.support;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

/** Limpieza y datos de apoyo para las pruebas que usan PostgreSQL de verdad. */
public final class Database {

    private Database() {
    }

    /** Borra todo lo que escriben las reservas; deja los usuarios sembrados del perfil local. */
    public static void clean(JdbcClient jdbc) {
        jdbc.sql("DELETE FROM status_change").update();
        jdbc.sql("DELETE FROM check_out").update();
        jdbc.sql("DELETE FROM absence").update();
        jdbc.sql("DELETE FROM inbox_message").update();
        jdbc.sql("DELETE FROM cancellation_report").update();
        jdbc.sql("DELETE FROM reservation_record").update();
        jdbc.sql("DELETE FROM reservation_closure").update();
        jdbc.sql("DELETE FROM outbox_message").update();
        jdbc.sql("DELETE FROM denial").update();
        jdbc.sql("DELETE FROM academic_block").update();
        jdbc.sql("DELETE FROM schedule_upload_row").update();
        jdbc.sql("DELETE FROM semester_schedule").update();
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

    /**
     * Una reserva de espacio confirmada que empezó hace {@code minutesAgo} minutos y dura una hora, como la que el
     * Módulo 3 reportaría por no asistencia; devuelve su id.
     */
    public static UUID startedReservation(JdbcClient jdbc, UUID userId, String resourceId, int minutesAgo) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO reservation (id, user_id, resource_id, resource_category, origin, status, starts_at, ends_at)
                        VALUES (:id, :user, :resource, 'ESPACIO', 'ESTUDIANTIL', 'CONFIRMADA',
                                now() - make_interval(mins => :ago), now() - make_interval(mins => :ago) + interval '1 hour')""")
                .param("id", id)
                .param("user", userId)
                .param("resource", resourceId)
                .param("ago", minutesAgo)
                .update();
        return id;
    }

    /**
     * Un préstamo de activo que empezó hace {@code startedDaysAgo} días y vence {@code dueInHours} horas después de
     * ahora (negativo si ya venció), con el activo recogido o no; devuelve el id de la reserva.
     */
    public static UUID loan(JdbcClient jdbc, UUID userId, String resourceId, int startedDaysAgo, int dueInHours,
                            boolean pickedUp) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO reservation (id, user_id, resource_id, resource_category, origin, status, starts_at, ends_at)
                        VALUES (:id, :user, :resource, 'ACTIVO', 'ESTUDIANTIL', 'CONFIRMADA',
                                now() - make_interval(days => :ago), now() + make_interval(hours => :due))""")
                .param("id", id)
                .param("user", userId)
                .param("resource", resourceId)
                .param("ago", startedDaysAgo)
                .param("due", dueInHours)
                .update();
        jdbc.sql("""
                        INSERT INTO loan (reservation_id, term_business_days, picked_up_at)
                        VALUES (:id, 3, CASE WHEN :pickedUp THEN now() - make_interval(days => :ago) END)""")
                .param("id", id)
                .param("pickedUp", pickedUp)
                .param("ago", startedDaysAgo)
                .update();
        return id;
    }

    /** Un bloqueo académico confirmado que empezó hace {@code minutesAgo} minutos y dura dos horas; devuelve su id. */
    public static UUID academicBlock(JdbcClient jdbc, String resourceId, int minutesAgo) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO reservation (id, resource_id, resource_category, origin, status, starts_at, ends_at)
                        VALUES (:id, :resource, 'ESPACIO', 'ACADEMICO', 'CONFIRMADA',
                                now() - make_interval(mins => :ago), now() - make_interval(mins => :ago) + interval '2 hours')""")
                .param("id", id)
                .param("resource", resourceId)
                .param("ago", minutesAgo)
                .update();
        return id;
    }
}
