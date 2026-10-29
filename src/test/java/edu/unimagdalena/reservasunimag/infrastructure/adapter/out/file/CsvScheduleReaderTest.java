package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.file;

import edu.unimagdalena.reservasunimag.domain.model.error.InvalidScheduleFileException;
import edu.unimagdalena.reservasunimag.domain.model.error.InvalidScheduleFileException.Code;
import edu.unimagdalena.reservasunimag.domain.model.upload.RawScheduleRow;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static edu.unimagdalena.reservasunimag.support.ContractFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El formato de UC3 Contratos §5 contra los mismos CSV que usan las demás pruebas. */
class CsvScheduleReaderTest {

    private final CsvScheduleReader reader = new CsvScheduleReader();

    private static byte[] bytes(String fixture) {
        return read(fixture).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void aValidFileGivesItsRowsWithTheirRealNumberAndQuotedCommas() {
        List<RawScheduleRow> rows = reader.read(bytes("schedule-valid.csv"));

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(RawScheduleRow::row).containsExactly(2, 3, 4);
        assertThat(rows.get(0).value(RawScheduleRow.RESOURCE_ID)).isEqualTo("ESP-0412");
        assertThat(rows.get(2).value(RawScheduleRow.COURSE)).isEqualTo("Dibujo Técnico, taller");
        assertThat(rows.get(2).value(RawScheduleRow.INSTRUCTOR)).isEmpty();
    }

    @Test
    void aBadlyWrittenRowIsReadAnywayBecauseTheVerdictDecides() {
        List<RawScheduleRow> rows = reader.read(bytes("schedule-with-errors.csv"));

        assertThat(rows).hasSize(6);
        assertThat(rows.get(1).value(RawScheduleRow.START_TIME)).isEqualTo("9:3");
        assertThat(rows.get(5).value(RawScheduleRow.DAY_OF_WEEK)).isEqualTo("DOMINGO");
    }

    @Test
    void aMissingRequiredColumnStopsTheUploadAndAnExtraOneIsIgnored() {
        assertThatThrownBy(() -> reader.read(bytes("schedule-invalid-header.csv")))
                .isInstanceOfSatisfying(InvalidScheduleFileException.class, invalid -> {
                    assertThat(invalid.code()).isEqualTo(Code.INVALID_HEADER);
                    assertThat(invalid.missingColumns()).containsExactly("resource_id");
                });
        String withExtra = read("schedule-valid.csv").replaceFirst("end_time", "end_time,notes")
                .replaceFirst("08:00,10:00", "08:00,10:00,algo");
        assertThat(reader.read(withExtra.getBytes(StandardCharsets.UTF_8))).hasSize(3);
    }

    @Test
    void aFileWithOnlyTheHeaderOrNothingAtAllIsEmpty() {
        assertThatThrownBy(() -> reader.read(bytes("schedule-empty.csv")))
                .isInstanceOfSatisfying(InvalidScheduleFileException.class,
                        invalid -> assertThat(invalid.code()).isEqualTo(Code.EMPTY_FILE));
        assertThatThrownBy(() -> reader.read(new byte[0]))
                .isInstanceOfSatisfying(InvalidScheduleFileException.class,
                        invalid -> assertThat(invalid.code()).isEqualTo(Code.EMPTY_FILE));
    }

    @Test
    void aByteOrderMarkFromExcelDoesNotBreakTheHeader() {
        byte[] content = bytes("schedule-valid.csv");
        byte[] withBom = new byte[content.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(content, 0, withBom, 3, content.length);

        assertThat(reader.read(withBom)).hasSize(3);
    }
}
