package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.module3;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import edu.unimagdalena.reservasunimag.domain.model.compliance.ComplianceReport;
import edu.unimagdalena.reservasunimag.domain.model.compliance.Sanction;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionScope;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException.ExternalModule;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;

import static edu.unimagdalena.reservasunimag.support.ContractFixtures.read;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplianceRestAdapterTest {

    private static final String PATH = "/api/v1/compliance/people/2019114045";
    private static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);

    private WireMockServer module3;

    @BeforeEach
    void startModule3() {
        module3 = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        module3.start();
    }

    @AfterEach
    void stopModule3() {
        module3.stop();
    }

    private ComplianceRestAdapter adapter(Duration timeout) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        return new ComplianceRestAdapter(RestClient.builder().baseUrl(module3.baseUrl()).requestFactory(factory).build());
    }

    private ComplianceRestAdapter adapter() {
        return adapter(Duration.ofSeconds(2));
    }

    private void respond(String fixture) {
        module3.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody(read(fixture))));
    }

    @Test
    void mapsASanctionedReportWithDatesAndScope() {
        respond("module3-report-sanctioned.json");

        ComplianceReport report = adapter().report("2019114045");

        assertThat(report.noHistory()).isFalse();
        assertThat(report.absenceCount()).isEqualTo(2);
        assertThat(report.lateReturns()).isEqualTo(1);
        assertThat(report.activeSanctions()).containsExactly(
                new Sanction("Dos ausencias no justificadas en el semestre", SEP_1, LocalDate.of(2026, 9, 30),
                        SanctionScope.ESPACIOS),
                new Sanction("Devolución tardía", LocalDate.of(2026, 8, 20), LocalDate.of(2026, 9, 10),
                        SanctionScope.ACTIVOS));
        assertThat(report.farthestInForce(SEP_1, null)).get().extracting(Sanction::end)
                .isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(report.farthestInForce(SEP_1, ResourceCategory.ACTIVO)).get().extracting(Sanction::reason)
                .isEqualTo("Devolución tardía");
    }

    @Test
    void aSanctionWithoutEndsOnIsKeptAsInForceWithoutInventingTheDate() {
        respond("module3-report-sanctioned-no-end-date.json");

        Sanction sanction = adapter().report("2019114045").farthestInForce(LocalDate.of(2027, 6, 1), null)
                .orElseThrow();

        assertThat(sanction.end()).isNull();
        assertThat(sanction.endDateMissing()).isTrue();
    }

    @Test
    void anAssetsOnlySanctionDoesNotBlockSpaces() {
        respond("module3-report-scope-assets.json");

        ComplianceReport report = adapter().report("2019114045");

        assertThat(report.farthestInForce(SEP_1, ResourceCategory.ESPACIO)).isEmpty();
        assertThat(report.farthestInForce(SEP_1, ResourceCategory.ACTIVO)).isPresent();
    }

    @Test
    void anExpiredSanctionThatStillComesInTheListDoesNotCount() {
        respond("module3-report-expired-sanction.json");

        assertThat(adapter().report("2019114045").farthestInForce(LocalDate.of(2026, 9, 3), null)).isEmpty();
    }

    @Test
    void theDraftNamesStartAndEndAndAnUnknownScopeAreStillRead() {
        module3.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("""
                        {"person": {"code": "2019114045"}, "absenceCount": 0, "lateReturns": 0,
                         "activeSanctions": [{"reason": "x", "start": "2026-09-01", "end": "2026-09-15",
                                              "scope": "RESERVAS"}]}""")));

        assertThat(adapter().report("2019114045").activeSanctions()).containsExactly(
                new Sanction("x", SEP_1, LocalDate.of(2026, 9, 15), SanctionScope.TODO));
    }

    @Test
    void aReportOfAnotherPersonIsNotUsed() {
        module3.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(read("module3-report-sanctioned.json").replace("2019114045", "2018000001"))));

        assertUnavailable(() -> adapter().report("2019114045"));
    }

    @Test
    void aReportWithoutSanctionsIsClearAndNotNoHistory() {
        respond("module3-report-clear.json");

        ComplianceReport report = adapter().report("2019114045");

        assertThat(report.activeSanctions()).isEmpty();
        assertThat(report.noHistory()).isFalse();
    }

    @Test
    void notFoundMeansNoHistoryAndReadsAsNoSanctions() {
        module3.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(404)));

        ComplianceReport report = adapter().report("2019114045");

        assertThat(report.activeSanctions()).isEmpty();
        assertThat(report.noHistory()).isTrue();
    }

    @Test
    void aFailureOfAnyOtherKindIsUnavailable() {
        for (int status : new int[]{401, 403, 500, 503}) {
            module3.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(status)));

            assertUnavailable(() -> adapter().report("2019114045"));
        }
    }

    @Test
    void aSlowAnswerWithinTheBudgetOfSc002IsStillUsed() {
        // El timeout de producción es 1500 ms, por debajo de los 2 s de SC-002.
        module3.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withFixedDelay(900)
                .withHeader("Content-Type", "application/json").withBody(read("module3-report-clear.json"))));

        assertThat(adapter(Duration.ofMillis(1500)).report("2019114045").noHistory()).isFalse();
    }

    @Test
    void aTimeoutIsUnavailable() {
        module3.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withFixedDelay(1500)
                .withHeader("Content-Type", "application/json").withBody(read("module3-report-clear.json"))));

        assertUnavailable(() -> adapter(Duration.ofMillis(300)).report("2019114045"));
    }

    private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ExternalServiceUnavailableException.class,
                exception -> assertThat(exception.module()).isEqualTo(ExternalModule.MODULO_3));
    }
}
