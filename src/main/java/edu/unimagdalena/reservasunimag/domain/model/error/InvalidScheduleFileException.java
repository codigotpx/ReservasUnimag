package edu.unimagdalena.reservasunimag.domain.model.error;

import java.util.List;

/** El archivo o el periodo no permiten ni empezar la carga: un 400 con su {@code code} (UC3 Contratos §1). */
public class InvalidScheduleFileException extends RuntimeException {

    public enum Code {
        EMPTY_FILE,
        INVALID_HEADER,
        INVALID_TERM
    }

    private final Code code;
    private final List<String> missingColumns;

    private InvalidScheduleFileException(Code code, String detail, List<String> missingColumns) {
        super(detail);
        this.code = code;
        this.missingColumns = List.copyOf(missingColumns);
    }

    public Code code() {
        return code;
    }

    /** Solo en {@code INVALID_HEADER}. */
    public List<String> missingColumns() {
        return missingColumns;
    }

    public static InvalidScheduleFileException empty() {
        return new InvalidScheduleFileException(Code.EMPTY_FILE, "El archivo no trae ninguna clase. No se cargó nada.",
                List.of());
    }

    public static InvalidScheduleFileException invalidHeader(List<String> missingColumns) {
        String detail = missingColumns.isEmpty()
                ? "El archivo no es un CSV legible. No se cargó nada."
                : "Al archivo le faltan columnas obligatorias: " + String.join(", ", missingColumns) + ".";
        return new InvalidScheduleFileException(Code.INVALID_HEADER, detail, missingColumns);
    }

    public static InvalidScheduleFileException invalidTerm() {
        return new InvalidScheduleFileException(Code.INVALID_TERM,
                "La fecha de fin del periodo debe ser posterior a la de inicio.", List.of());
    }
}
