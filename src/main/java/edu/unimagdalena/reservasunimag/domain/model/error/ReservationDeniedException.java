package edu.unimagdalena.reservasunimag.domain.model.error;

import edu.unimagdalena.reservasunimag.domain.model.compliance.Sanction;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import static edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot.ZONE;

/**
 * Una regla de negocio dijo que no. Lleva un único código —el primero que falla en el orden de FR-002— y los datos
 * que su mensaje necesita. Nunca dice quién tiene el recurso (UC8 FR-005). Los valores de {@code extensions} son
 * cadenas, números, {@link Instant} o {@link LocalDate}; el adaptador web los escribe en el formato del contrato.
 */
public class ReservationDeniedException extends RuntimeException {

    private final DenialCode code;
    private final Map<String, Object> extensions;

    private ReservationDeniedException(DenialCode code, String detail, Map<String, Object> extensions) {
        super(detail);
        this.code = code;
        this.extensions = Map.copyOf(extensions);
    }

    public DenialCode code() {
        return code;
    }

    /** Campos propios de este código, además de {@code code}. */
    public Map<String, Object> extensions() {
        return extensions;
    }

    /** RES-001. {@code occupiedUntil} puede ser {@code null}. */
    public static ReservationDeniedException academicConflict(String resourceId, Instant occupiedUntil) {
        var extensions = new LinkedHashMap<String, Object>();
        extensions.put("resourceId", resourceId);
        if (occupiedUntil != null) {
            extensions.put("occupiedUntil", occupiedUntil);
        }
        return new ReservationDeniedException(DenialCode.RES_001,
                "El recurso está reservado para actividad docente en la franja solicitada.", extensions);
    }

    /** RES-002. {@code nextRelease} es {@code null} si la próxima en liberarse es un préstamo vencido sin devolver. */
    public static ReservationDeniedException quotaExhausted(int active, int max, Instant nextRelease) {
        var extensions = new LinkedHashMap<String, Object>();
        extensions.put("active", active);
        extensions.put("max", max);
        String detail;
        if (nextRelease == null) {
            detail = "Tienes " + active + " de " + max + " reservas vigentes. Una corresponde a un activo vencido: "
                    + "el cupo se libera cuando se registre su devolución.";
        } else {
            extensions.put("nextRelease", nextRelease);
            var local = nextRelease.atZone(ZONE);
            detail = "Tienes " + active + " de " + max + " reservas activas. La próxima se libera el "
                    + local.toLocalDate() + " a las " + local.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                    + ".";
        }
        return new ReservationDeniedException(DenialCode.RES_002, detail, extensions);
    }

    /** RES-003. Una sanción sin fecha de fin no la menciona. */
    public static ReservationDeniedException activeSanction(Sanction sanction) {
        var extensions = new LinkedHashMap<String, Object>();
        if (sanction.reason() != null) {
            extensions.put("sanctionReason", sanction.reason());
        }
        String detail;
        if (sanction.end() == null) {
            detail = "Tienes una sanción vigente y no puedes reservar.";
        } else {
            extensions.put("sanctionUntil", sanction.end());
            detail = "Tienes una sanción vigente hasta el " + sanction.end() + " y no puedes reservar.";
        }
        return new ReservationDeniedException(DenialCode.RES_003, detail, extensions);
    }

    /** RES-004: el recurso acaba de ser tomado por otra solicitud. */
    public static ReservationDeniedException resourceTaken(String resourceId) {
        return new ReservationDeniedException(DenialCode.RES_004,
                "El recurso acaba de ser tomado por otra solicitud. Vuelve a consultar la disponibilidad.",
                Map.of("resourceId", resourceId));
    }

    /** RES-004 provisional: el diccionario no tiene código propio para el mantenimiento. */
    public static ReservationDeniedException underMaintenance(String resourceId) {
        return new ReservationDeniedException(DenialCode.RES_004,
                "El recurso está en mantenimiento y no se puede reservar.",
                Map.of("detectedStatus", "EN_MANTENIMIENTO", "resourceId", resourceId));
    }
}
