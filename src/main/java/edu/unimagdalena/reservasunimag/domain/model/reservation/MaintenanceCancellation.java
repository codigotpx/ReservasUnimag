package edu.unimagdalena.reservasunimag.domain.model.reservation;

import java.time.Instant;
import java.util.List;

/**
 * Las reservas futuras de un recurso que queda fuera de servicio (UC4 FR-010). {@code appliedAt} es {@code null} en
 * la previsualización; cuando no lo es, cada reserva de {@code affected} quedó cancelada con su evento en la outbox.
 * {@code skipped} son los activos que ya están en manos de alguien: no se cancelan, alguien tiene que ir a pedirlos.
 * {@code resourceName} es {@code null} si el Módulo 1 no respondió.
 */
public record MaintenanceCancellation(String resourceId, String resourceName, List<Affected> affected,
                                      List<Skipped> skipped, Instant appliedAt) {

    public MaintenanceCancellation {
        affected = List.copyOf(affected);
        skipped = List.copyOf(skipped);
    }

    public boolean applied() {
        return appliedAt != null;
    }

    /** {@code holder} es {@code null} si la persona ya no está en la base. */
    public record Affected(Reservation reservation, ReservationHolder holder) {
    }

    public record Skipped(Reservation reservation, String reason) {
    }
}
