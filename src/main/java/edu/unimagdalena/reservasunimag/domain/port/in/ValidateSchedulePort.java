package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleFile;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleUpload;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;

import java.time.LocalDate;

/**
 * UC3, paso 1: leer el horario, expandir cada clase a sus sesiones y reportar fila por fila qué pasaría (FR-003,
 * FR-008). Guarda la carga y su reporte, pero no crea bloqueos ni cancela reservas.
 */
public interface ValidateSchedulePort {

    /**
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.InvalidScheduleFileException       archivo vacío, cabecera inválida o periodo mal puesto
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException si el Módulo 1 no responde
     */
    ScheduleUpload validate(CurrentUser user, ScheduleFile file, String academicTerm, LocalDate termStart,
                            LocalDate termEnd);
}
