package edu.unimagdalena.reservasunimag.domain.model.upload;

import java.util.Objects;

/** El archivo que sube Dirección de Programa. {@code fileName} puede ser {@code null}. */
public record ScheduleFile(String fileName, byte[] content) {

    public ScheduleFile {
        Objects.requireNonNull(content, "content");
    }
}
