package edu.unimagdalena.reservasunimag.domain.port.out;

import edu.unimagdalena.reservasunimag.domain.model.upload.RawScheduleRow;

import java.util.List;

/** Lee el archivo del horario. El formato es tecnología y no vive en el dominio (plan de UC3, Structure Decision). */
public interface ScheduleReader {

    /**
     * Las filas de datos tal como vienen, con su número real. Nunca falla por una fila mal escrita: eso lo decide el
     * dictamen.
     *
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.InvalidScheduleFileException si falta una columna obligatoria, el archivo no es legible o no trae ninguna fila
     */
    List<RawScheduleRow> read(byte[] content);
}
