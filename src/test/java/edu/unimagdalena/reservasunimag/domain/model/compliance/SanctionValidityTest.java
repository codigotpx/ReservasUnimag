package edu.unimagdalena.reservasunimag.domain.model.compliance;

import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** El borde de la vigencia de UC6: siempre el mismo, contra el día de hoy y con el último día completo. */
class SanctionValidityTest {

    private static final LocalDate SEP_15 = LocalDate.of(2026, 9, 15);

    private static Sanction until(LocalDate end) {
        return new Sanction("Dos ausencias", LocalDate.of(2026, 9, 1), end, SanctionScope.TODO);
    }

    @Test
    void theLastDayCountsWholeAndTheNextOneIsFree() {
        assertThat(until(SEP_15).inForceOn(SEP_15)).isTrue();
        assertThat(until(SEP_15).inForceOn(SEP_15.plusDays(1))).isFalse();
    }

    @Test
    void aSanctionThatEndedDaysAgoNoLongerCountsWithoutLiftingItByHand() {
        // Escenario 3: terminó el 30 de agosto y se consulta el 3 de septiembre.
        var ended = new Sanction("x", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 30), SanctionScope.TODO);

        assertThat(ended.inForceOn(LocalDate.of(2026, 9, 3))).isFalse();
        assertThat(new ComplianceReport(List.of(ended), 2, 0, null)
                .farthestInForce(LocalDate.of(2026, 9, 3), null)).isEmpty();
    }

    @Test
    void withoutAnEndDateItIsInForceAndSaysTheDataWasMissing() {
        Sanction open = until(null);

        assertThat(open.inForceOn(LocalDate.of(2030, 1, 1))).isTrue();
        assertThat(open.endDateMissing()).isTrue();
    }

    @Test
    void withoutAStartDateItAlreadyStartedBecauseItCameAsActive() {
        assertThat(new Sanction("x", null, SEP_15, SanctionScope.TODO).inForceOn(LocalDate.of(2026, 1, 1))).isTrue();
    }

    @Test
    void itIsComparedAgainstTodayAndNotAgainstTheDateOfTheReservation() {
        // Sancionado hasta el 15: hoy (día 10) no puede reservar, ni siquiera para el 20. La fecha pedida no entra.
        var report = new ComplianceReport(List.of(until(SEP_15)), 0, 0, null);

        assertThat(report.farthestInForce(LocalDate.of(2026, 9, 10), ResourceCategory.ESPACIO)).isPresent();
    }

    @Test
    void theScopeDecidesWhatItBlocks() {
        var spaces = new Sanction("x", null, SEP_15, SanctionScope.ESPACIOS);
        var assets = new Sanction("x", null, SEP_15, SanctionScope.ACTIVOS);
        var all = new Sanction("x", null, SEP_15, SanctionScope.TODO);

        assertThat(spaces.appliesTo(ResourceCategory.ESPACIO)).isTrue();
        assertThat(spaces.appliesTo(ResourceCategory.ACTIVO)).isFalse();
        assertThat(assets.appliesTo(ResourceCategory.ACTIVO)).isTrue();
        assertThat(assets.appliesTo(ResourceCategory.ESPACIO)).isFalse();
        assertThat(all.appliesTo(ResourceCategory.ESPACIO)).isTrue();
        assertThat(all.appliesTo(ResourceCategory.ACTIVO)).isTrue();
    }

    @Test
    void aMissingOrUnknownScopeBlocksEverything() {
        assertThat(SanctionScope.parse(null)).isEqualTo(SanctionScope.TODO);
        assertThat(SanctionScope.parse("RESERVAS")).isEqualTo(SanctionScope.TODO);
        assertThat(SanctionScope.parse(" espacios ")).isEqualTo(SanctionScope.ESPACIOS);
        assertThat(new Sanction("x", null, null, null).scope()).isEqualTo(SanctionScope.TODO);
    }

    @Test
    void ofSeveralInForceTheOneWithoutEndRulesAndThenTheFarthest() {
        var shortOne = until(SEP_15);
        var longOne = until(LocalDate.of(2026, 10, 30));
        var open = until(null);
        LocalDate today = LocalDate.of(2026, 9, 3);

        assertThat(new ComplianceReport(List.of(shortOne, longOne), 0, 0, null).farthestInForce(today, null))
                .contains(longOne);
        assertThat(new ComplianceReport(List.of(shortOne, open, longOne), 0, 0, null).farthestInForce(today, null))
                .contains(open);
    }
}
