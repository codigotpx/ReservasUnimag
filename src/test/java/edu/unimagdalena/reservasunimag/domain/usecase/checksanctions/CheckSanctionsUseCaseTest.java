package edu.unimagdalena.reservasunimag.domain.usecase.checksanctions;

import edu.unimagdalena.reservasunimag.domain.model.compliance.ComplianceReport;
import edu.unimagdalena.reservasunimag.domain.model.compliance.Sanction;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckOutcome;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckPurpose;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionNotice;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionScope;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException.ExternalModule;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.CompliancePort;
import edu.unimagdalena.reservasunimag.support.InMemorySanctionChecks;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.NOW;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.STUDENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC6: los cuatro escenarios, la caída del Módulo 3 y el registro de cada consulta (FR-009). Hoy es 2026-09-01. */
class CheckSanctionsUseCaseTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);

    private final InMemorySanctionChecks checks = new InMemorySanctionChecks();
    private final List<String> askedFor = new ArrayList<>();
    private ComplianceReport report = ComplianceReport.empty();
    private boolean down;
    /** Cada lectura del reloj avanza 150 ms, para que la duración registrada no sea cero. */
    private Instant now = NOW;
    private final Clock clock = () -> {
        Instant current = now;
        now = now.plusMillis(150);
        return current;
    };

    private final CompliancePort compliance = personCode -> {
        askedFor.add(personCode);
        if (down) {
            throw new ExternalServiceUnavailableException(ExternalModule.MODULO_3,
                    "No se pudo leer el reporte de cumplimiento del Módulo 3", new ConnectException("Connection refused"));
        }
        return report;
    };

    private final CheckSanctionsUseCase useCase = new CheckSanctionsUseCase(compliance, checks, clock);

    private static Sanction sanction(String reason, LocalDate end, SanctionScope scope) {
        return new Sanction(reason, TODAY.minusDays(5), end, scope);
    }

    private void reportWith(Sanction... sanctions) {
        report = new ComplianceReport(List.of(sanctions), 2, 1, NOW);
    }

    @Test
    void scenario1APersonUpToDateIsClearAndTheCountsAreRecorded() {
        report = new ComplianceReport(List.of(), 1, 0, NOW);

        assertThat(useCase.activeSanction(STUDENT, ResourceCategory.ESPACIO)).isEmpty();

        SanctionCheck check = checks.last();
        assertThat(check.outcome()).isEqualTo(SanctionCheckOutcome.CLEAR);
        assertThat(check.answered().sanctionReason()).isNull();
        assertThat(check.answered().absenceCount()).isEqualTo(1);
    }

    @Test
    void scenario2ASanctionInForceComesWithReasonAndEndAndIsRecordedAsAnswered() {
        reportWith(sanction("Dos ausencias no justificadas en el semestre", LocalDate.of(2026, 9, 15),
                SanctionScope.ESPACIOS));

        assertThat(useCase.activeSanction(STUDENT, ResourceCategory.ESPACIO)).get()
                .extracting(Sanction::end).isEqualTo(LocalDate.of(2026, 9, 15));

        SanctionCheck check = checks.last();
        assertThat(check.outcome()).isEqualTo(SanctionCheckOutcome.SANCTIONED);
        assertThat(check.personCode()).isEqualTo(STUDENT.code());
        assertThat(check.purpose()).isEqualTo(SanctionCheckPurpose.RESERVE_RESOURCES);
        assertThat(check.category()).isEqualTo(ResourceCategory.ESPACIO);
        assertThat(check.askedAt()).isEqualTo(NOW);
        assertThat(check.durationMs()).isEqualTo(150);
        assertThat(check.answered()).isEqualTo(new SanctionCheck.Answered(
                "Dos ausencias no justificadas en el semestre", LocalDate.of(2026, 9, 15), SanctionScope.ESPACIOS,
                false, 2, 1));
    }

    @Test
    void scenario3AnExpiredSanctionIsClear() {
        reportWith(new Sanction("Vencida", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 30), SanctionScope.TODO));

        assertThat(useCase.activeSanction(STUDENT, ResourceCategory.ESPACIO)).isEmpty();
        assertThat(checks.last().outcome()).isEqualTo(SanctionCheckOutcome.CLEAR);
    }

    @Test
    void scenario4APersonWithoutHistoryIsNoHistoryAndNotAFailure() {
        assertThat(useCase.activeSanction(STUDENT, ResourceCategory.ACTIVO)).isEmpty();
        assertThat(useCase.checkSanctions(STUDENT)).isEmpty();

        assertThat(checks.checks).extracting(SanctionCheck::outcome)
                .containsExactly(SanctionCheckOutcome.NO_HISTORY, SanctionCheckOutcome.NO_HISTORY);
        assertThat(checks.last().answered()).isNull();
    }

    @Test
    void ofSeveralTheFarthestDecidesAndOneWithoutEndRulesOverAll() {
        reportWith(sanction("Corta", TODAY.plusDays(3), SanctionScope.TODO),
                sanction("Larga", TODAY.plusDays(30), SanctionScope.TODO));
        assertThat(useCase.activeSanction(STUDENT, ResourceCategory.ESPACIO)).get()
                .extracting(Sanction::reason).isEqualTo("Larga");

        reportWith(sanction("Larga", TODAY.plusDays(30), SanctionScope.TODO),
                sanction("Sin fin", null, SanctionScope.TODO));
        assertThat(useCase.activeSanction(STUDENT, ResourceCategory.ESPACIO)).get()
                .extracting(Sanction::reason).isEqualTo("Sin fin");
        assertThat(checks.last().answered().endDateMissing()).isTrue();
        assertThat(checks.last().answered().endsOn()).isNull();
    }

    @Test
    void aSanctionOfAnotherScopeDoesNotDecideButTheCategoryIsRecorded() {
        reportWith(sanction("No asistencia", TODAY.plusDays(7), SanctionScope.ESPACIOS));

        assertThat(useCase.activeSanction(STUDENT, ResourceCategory.ACTIVO)).isEmpty();

        assertThat(checks.last().outcome()).isEqualTo(SanctionCheckOutcome.CLEAR);
        assertThat(checks.last().category()).isEqualTo(ResourceCategory.ACTIVO);
    }

    @Test
    void theSearchNoticeCountsAnyScopeAndSaysWhichOne() {
        reportWith(sanction("No asistencia", LocalDate.of(2026, 9, 8), SanctionScope.ESPACIOS));

        SanctionNotice notice = useCase.checkSanctions(STUDENT).orElseThrow();

        assertThat(notice.type()).isEqualTo(SanctionNotice.Type.ACTIVE_SANCTION);
        assertThat(notice.message()).isEqualTo("Tienes una sanción vigente sobre los espacios hasta el 2026-09-08. "
                + "Puedes consultar los recursos, pero no podrás reservar espacios.");
        assertThat(checks.last().purpose()).isEqualTo(SanctionCheckPurpose.SEARCH_RESOURCES);
        assertThat(checks.last().category()).isNull();
    }

    @Test
    void whenModule3IsDownSearchWarnsReserveFailsAndBothAreRecordedAsNotVerified() {
        down = true;

        assertThat(useCase.checkSanctions(STUDENT)).get().extracting(SanctionNotice::type)
                .isEqualTo(SanctionNotice.Type.NOT_VERIFIED);
        assertThatThrownBy(() -> useCase.activeSanction(STUDENT, ResourceCategory.ESPACIO))
                .isInstanceOf(ExternalServiceUnavailableException.class);

        assertThat(checks.checks).allSatisfy(check -> {
            assertThat(check.outcome()).isEqualTo(SanctionCheckOutcome.NOT_VERIFIED);
            assertThat(check.answered()).isNull();
            assertThat(check.failure()).isEqualTo("Connection refused");
        });
        assertThat(checks.checks).extracting(SanctionCheck::purpose).containsExactly(
                SanctionCheckPurpose.SEARCH_RESOURCES, SanctionCheckPurpose.RESERVE_RESOURCES);
    }

    @Test
    void onlyThePersonOfTheSessionIsAskedFor() {
        useCase.checkSanctions(STUDENT);
        useCase.activeSanction(STUDENT, ResourceCategory.ESPACIO);

        assertThat(askedFor).containsExactly(STUDENT.code(), STUDENT.code());
    }

    @Test
    void thereIsNoCacheASanctionThatAppearsBetweenTwoChecksIsSeen() {
        assertThat(useCase.activeSanction(STUDENT, ResourceCategory.ESPACIO)).isEmpty();

        reportWith(sanction("Recién sancionado", TODAY.plusDays(7), SanctionScope.TODO));

        assertThat(useCase.activeSanction(STUDENT, ResourceCategory.ESPACIO)).isPresent();
        assertThat(askedFor).hasSize(2);
    }
}
