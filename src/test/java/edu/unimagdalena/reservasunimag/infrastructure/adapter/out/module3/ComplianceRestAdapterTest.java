package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.module3;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import edu.unimagdalena.reservasunimag.domain.model.compliance.ComplianceReport;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException.ExternalModule;
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

    @Test
    void mapsASanctionedReportAndPicksTheFarthestEnd() {
        module3.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody(read("module3-report-sanctioned.json"))));

        ComplianceReport report = adapter().report("2019114045");

        assertThat(report.activeSanctions()).hasSize(2);
        assertThat(report.absenceCount()).isEqualTo(2);
        assertThat(report.lateReturns()).isEqualTo(1);
        assertThat(report.farthestSanction()).get().satisfies(sanction -> {
            assertThat(sanction.reason()).isEqualTo("Dos ausencias no justificadas en el semestre");
            assertThat(sanction.end()).isEqualTo(LocalDate.of(2026, 9, 30));
        });
    }

    @Test
    void aReportWithoutSanctionsIsEmpty() {
        module3.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody(read("module3-report-clear.json"))));

        assertThat(adapter().report("2019114045").farthestSanction()).isEmpty();
    }

    @Test
    void notFoundMeansNoHistoryAndReadsAsNoSanctions() {
        module3.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(404)));

        ComplianceReport report = adapter().report("2019114045");

        assertThat(report.activeSanctions()).isEmpty();
        assertThat(report.farthestSanction()).isEmpty();
    }

    @Test
    void aFailureOfAnyOtherKindIsUnavailable() {
        for (int status : new int[]{401, 403, 500, 503}) {
            module3.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(status)));

            assertUnavailable(() -> adapter().report("2019114045"));
        }
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
