package edu.unimagdalena.reservasunimag.infrastructure.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static edu.unimagdalena.reservasunimag.support.ContractFixtures.properties;

class HttpClientsConfigTest {

    private WireMockServer server;

    @BeforeEach
    void start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        server.stubFor(get(urlPathEqualTo("/ping")).willReturn(aResponse().withStatus(200)));
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private ReservationProperties withModule1Credentials(String username, String password) {
        ReservationProperties base = properties(URI.create(server.baseUrl()), URI.create(server.baseUrl()),
                Duration.ofSeconds(2), 10, 10);
        var module1 = new ReservationProperties.Module1(base.module1().baseUrl(), base.module1().timeout(),
                base.module1().pageSize(), base.module1().maxPages(), true, username, password);
        return new ReservationProperties(base.pageSize(), base.nextSlotStep(), base.maxQuota(),
                base.maxSpaceDuration(), base.minCancellationNotice(), base.noShowGrace(), base.loanLossThreshold(),
                base.holidays(), base.resourceTypes(), module1, base.module3(), base.jwt(), base.kafka(),
                base.outbox(), base.upload());
    }

    @Test
    void module1SendsHttpBasicWhenThereAreCredentials() {
        RestClient client = new HttpClientsConfig()
                .module1RestClient(RestClient.builder(), withModule1Credentials("admin_unimag", "secreto*"));

        client.get().uri("/ping").retrieve().toBodilessEntity();

        // base64("admin_unimag:secreto*")
        server.verify(getRequestedFor(urlPathEqualTo("/ping"))
                .withHeader("Authorization", equalTo("Basic YWRtaW5fdW5pbWFnOnNlY3JldG8q")));
    }

    @Test
    void withoutCredentialsNeitherModuleSendsAuthorization() {
        ReservationProperties properties = withModule1Credentials("", "");
        new HttpClientsConfig().module1RestClient(RestClient.builder(), properties)
                .get().uri("/ping").retrieve().toBodilessEntity();
        new HttpClientsConfig().module3RestClient(RestClient.builder(), properties)
                .get().uri("/ping").retrieve().toBodilessEntity();

        server.verify(2, getRequestedFor(urlPathEqualTo("/ping")).withHeader("Authorization", absent()));
    }
}
