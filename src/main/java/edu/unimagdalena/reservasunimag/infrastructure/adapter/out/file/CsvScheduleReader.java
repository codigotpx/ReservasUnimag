package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.file;

import edu.unimagdalena.reservasunimag.domain.model.error.InvalidScheduleFileException;
import edu.unimagdalena.reservasunimag.domain.model.upload.RawScheduleRow;
import edu.unimagdalena.reservasunimag.domain.port.out.ScheduleReader;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee el CSV del horario con Commons CSV (UC3 Contratos §5): cabecera y comillas dobles. Valida solo la cabecera; una
 * fila mal escrita no rompe la lectura, porque decidir qué le pasa es trabajo del dominio. Una columna extra se ignora
 * y las filas en blanco se saltan.
 * <p>
 * Acepta lo que guarda Excel en español sin que nadie tenga que tocar nada: el separador puede ser coma o punto y coma
 * (con la configuración regional de Colombia, Excel usa punto y coma), y un archivo que no es UTF-8 se lee como
 * Windows-1252, que es lo que produce "CSV (delimitado por comas)".
 */
@Component
class CsvScheduleReader implements ScheduleReader {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .build();

    @Override
    public List<RawScheduleRow> read(byte[] content) {
        String text = decode(withoutBom(content));
        CSVFormat format = FORMAT.builder().setDelimiter(delimiterOf(text)).build();
        try (var parser = CSVParser.parse(new StringReader(text), format)) {
            List<String> header = parser.getHeaderNames().stream().map(String::strip).toList();
            List<String> missing = RawScheduleRow.REQUIRED_COLUMNS.stream()
                    .filter(column -> !header.contains(column))
                    .toList();
            if (header.isEmpty() && missing.size() == RawScheduleRow.REQUIRED_COLUMNS.size()
                    && content.length == 0) {
                throw InvalidScheduleFileException.empty();
            }
            if (!missing.isEmpty()) {
                throw InvalidScheduleFileException.invalidHeader(missing);
            }
            List<RawScheduleRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, String> values = new HashMap<>();
                for (String column : header) {
                    values.put(column, record.isSet(column) ? record.get(column) : "");
                }
                // El número real de línea en el archivo, para que el reporte se pueda buscar en el CSV.
                rows.add(new RawScheduleRow((int) parser.getCurrentLineNumber(), values));
            }
            if (rows.isEmpty()) {
                throw InvalidScheduleFileException.empty();
            }
            return rows;
        } catch (IOException | IllegalArgumentException | IllegalStateException unreadable) {
            throw InvalidScheduleFileException.invalidHeader(List.of());
        } catch (UncheckedIOException unreadable) {
            throw InvalidScheduleFileException.invalidHeader(List.of());
        }
    }

    /** UTF-8 si lo es; si no, Windows-1252, que es como guarda Excel cuando no se elige "CSV UTF-8". */
    private static String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException notUtf8) {
            return new String(content, WINDOWS_1252);
        }
    }

    /** El separador sale de la cabecera, cuyos nombres no tienen ni comas ni punto y coma. */
    private static char delimiterOf(String text) {
        int lineEnd = text.indexOf('\n');
        String header = lineEnd < 0 ? text : text.substring(0, lineEnd);
        long semicolons = header.chars().filter(c -> c == ';').count();
        long commas = header.chars().filter(c -> c == ',').count();
        return semicolons > commas ? ';' : ',';
    }

    private static byte[] withoutBom(byte[] content) {
        if (content.length >= 3 && (content[0] & 0xFF) == 0xEF && (content[1] & 0xFF) == 0xBB
                && (content[2] & 0xFF) == 0xBF) {
            return java.util.Arrays.copyOfRange(content, 3, content.length);
        }
        return content;
    }
}
