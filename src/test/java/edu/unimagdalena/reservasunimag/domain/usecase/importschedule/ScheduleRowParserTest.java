package edu.unimagdalena.reservasunimag.domain.usecase.importschedule;

import edu.unimagdalena.reservasunimag.domain.model.upload.RawScheduleRow;
import edu.unimagdalena.reservasunimag.domain.usecase.importschedule.ScheduleRowParser.ParsedRow;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** UC3 Contratos §5: qué hace válida una fila, y qué columna señala cuando no lo es. */
class ScheduleRowParserTest {

    private static RawScheduleRow row(Map<String, String> overrides) {
        Map<String, String> values = new HashMap<>(Map.of(
                "course_code", "IS-402", "course", "Redes de Computadores", "group", "1",
                "program", "Ingeniería de Sistemas", "instructor", "Nombre del instructor", "resource_id", "ESP-0412",
                "day_of_week", "LUNES", "start_time", "08:00", "end_time", "10:00"));
        values.putAll(overrides);
        return new RawScheduleRow(2, values);
    }

    @Test
    void aWellWrittenRowIsAWeeklyClass() {
        ParsedRow parsed = ScheduleRowParser.parse(row(Map.of()));

        assertThat(parsed.valid()).isTrue();
        assertThat(parsed.weekly().day()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(parsed.weekly().start()).isEqualTo(LocalTime.of(8, 0));
        assertThat(parsed.weekly().academic().label()).isEqualTo("IS-402 grupo 1");
    }

    @Test
    void theInstructorMayBeEmptyButNotTheRest() {
        assertThat(ScheduleRowParser.parse(row(Map.of("instructor", ""))).weekly().academic().instructor()).isNull();
        ParsedRow noGroup = ScheduleRowParser.parse(row(Map.of("group", " ")));
        assertThat(noGroup.valid()).isFalse();
        assertThat(noGroup.column()).isEqualTo("group");
    }

    @Test
    void aBadHourPointsAtItsColumn() {
        ParsedRow parsed = ScheduleRowParser.parse(row(Map.of("start_time", "9:3")));

        assertThat(parsed.problem()).isEqualTo("La columna start_time no tiene el formato HH:mm.");
        assertThat(parsed.column()).isEqualTo("start_time");
        assertThat(ScheduleRowParser.parse(row(Map.of("end_time", "25:00"))).column()).isEqualTo("end_time");
    }

    @Test
    void sundayIsRejectedAndSaturdayIsValid() {
        assertThat(ScheduleRowParser.parse(row(Map.of("day_of_week", "DOMINGO"))).problem())
                .isEqualTo("Los domingos no hay clases.");
        assertThat(ScheduleRowParser.parse(row(Map.of("day_of_week", "SABADO"))).valid()).isTrue();
        assertThat(ScheduleRowParser.parse(row(Map.of("day_of_week", "Miércoles"))).column())
                .isEqualTo("day_of_week");
    }

    @Test
    void theClassMustFitTheWindowAndEndAfterItStarts() {
        assertThat(ScheduleRowParser.parse(row(Map.of("start_time", "05:00"))).valid()).isFalse();
        assertThat(ScheduleRowParser.parse(row(Map.of("end_time", "22:30"))).valid()).isFalse();
        assertThat(ScheduleRowParser.parse(row(Map.of("start_time", "10:00", "end_time", "08:00"))).column())
                .isEqualTo("end_time");
        // Una clase de tres horas es normal: el tope de 2 horas es de las reservas estudiantiles.
        assertThat(ScheduleRowParser.parse(row(Map.of("start_time", "14:00", "end_time", "17:00"))).valid()).isTrue();
    }
}
