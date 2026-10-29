package edu.unimagdalena.reservasunimag.domain.model.error;

import java.util.UUID;

public class ScheduleUploadNotFoundException extends RuntimeException {

    public ScheduleUploadNotFoundException(UUID uploadId) {
        super("La carga " + uploadId + " no existe.");
    }
}
