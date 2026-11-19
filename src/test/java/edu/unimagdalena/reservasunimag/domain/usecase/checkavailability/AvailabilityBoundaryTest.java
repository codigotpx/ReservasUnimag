package edu.unimagdalena.reservasunimag.domain.usecase.checkavailability;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Occupancy;
import edu.unimagdalena.reservasunimag.domain.model.reservation.OccupancyType;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.support.InMemoryInventory;
import edu.unimagdalena.reservasunimag.support.ResourceFixtures.InMemoryOccupancies;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalTime;
import java.util.List;

import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.CLOCK;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.DAY;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.asset;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.at;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.occupancy;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.space;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * El borde de los rangos de UC8, enunciado en un solo sitio: {@code [inicio, fin)}. Cada fila se pregunta por las
 * dos versiones, porque el criterio tiene que ser el mismo en las dos.
 */
class AvailabilityBoundaryTest {

    private static final Resource ROOM = space("ESP-0412", "Salón 201", 30);
    private static final Resource BOOK = asset("ACT-004512", "Libro de Cálculo I");

    private static boolean[] availableBothWays(Resource resource, Occupancy occupancy, TimeSlot slot) {
        var useCase = new CheckAvailabilityUseCase(new InMemoryInventory(resource),
                new InMemoryOccupancies(occupancy), CLOCK);
        return new boolean[] {
                useCase.check(resource.id(), slot).available(),
                useCase.checkAvailability(List.of(resource), slot).getFirst().selectable()};
    }

    @ParameterizedTest(name = "clase 08:00-10:00 contra {0}-{1}: disponible={2}")
    @CsvSource({
            "10:00, 12:00, true",   // se tocan: a las 10:00 la clase ya terminó
            "06:00, 08:00, true",   // se tocan por el otro lado
            "09:30, 10:30, false",  // media hora de cruce (escenario 6)
            "09:59, 10:30, false",  // un minuto basta (FR-003)
            "07:00, 08:01, false",
            "08:00, 10:00, false",
            "08:30, 09:30, false"}) // contenida
    void aClassFromEightToTen(LocalTime start, LocalTime end, boolean available) {
        boolean[] answers = availableBothWays(ROOM, occupancy(ROOM.id(), OccupancyType.ACADEMIC_BLOCK, 8, 10),
                new TimeSlot(DAY, start, end));

        assertThat(answers).containsExactly(available, available);
    }

    @ParameterizedTest(name = "préstamo abierto desde el día anterior contra {0}-{1}: disponible={2}")
    @CsvSource({"06:00, 07:00, false", "20:00, 22:00, false"})
    void anOpenLoanCrossesAnyLaterSlot(LocalTime start, LocalTime end, boolean available) {
        var loan = new Occupancy(BOOK.id(), OccupancyType.LOAN_PICKED_UP, at(8, 0).minusSeconds(86_400), null,
                at(8, 0).plusSeconds(5 * 86_400));

        boolean[] answers = availableBothWays(BOOK, loan, new TimeSlot(DAY.plusDays(2), start, end));

        assertThat(answers).containsExactly(available, available);
    }
}
