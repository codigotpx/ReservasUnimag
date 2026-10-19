package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.module1;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException.ExternalModule;
import edu.unimagdalena.reservasunimag.domain.model.resource.Catalog;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static edu.unimagdalena.reservasunimag.support.ContractFixtures.properties;
import static edu.unimagdalena.reservasunimag.support.ContractFixtures.read;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryRestAdapterTest {

    private static final ResourceFilter NO_FILTER = new ResourceFilter(null, null, null);

    private WireMockServer module1;

    @BeforeEach
    void startModule1() {
        module1 = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        module1.start();
    }

    @AfterEach
    void stopModule1() {
        module1.stop();
    }

    private InventoryRestAdapter adapter(int maxPages, Duration timeout) {
        var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        RestClient client = RestClient.builder().baseUrl(module1.baseUrl()).requestFactory(factory).build();
        return new InventoryRestAdapter(client,
                properties(URI.create(module1.baseUrl()), URI.create("http://unused"), timeout, 2, maxPages));
    }

    private void stubPage(int page, String fixture) {
        module1.stubFor(get(urlPathEqualTo("/api/v1/resources"))
                .withQueryParam("page", equalTo(String.valueOf(page)))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(read(fixture))));
    }

    private void stubAllPages() {
        stubPage(1, "module1-catalog-page1.json");
        stubPage(2, "module1-catalog-page2.json");
        stubPage(3, "module1-catalog-page3.json");
    }

    @Test
    void mapsTheCatalogOfASpaceAndAnAsset() {
        stubPage(1, "module1-catalog-page1.json");
        module1.stubFor(get(urlPathEqualTo("/api/v1/resources")).withQueryParam("page", equalTo("2"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(read("module1-catalog-empty.json"))));
        // El adaptador para en totalPages; la primera página dice 3, así que sirve páginas vacías de relleno.
        stubPage(3, "module1-catalog-empty.json");

        Catalog catalog = adapter(10, Duration.ofSeconds(2)).searchCatalog(NO_FILTER);

        Resource space = catalog.resources().get(0);
        assertThat(space.id()).isEqualTo("ESP-0412");
        assertThat(space.category()).isEqualTo(ResourceCategory.ESPACIO);
        assertThat(space.operationalStatus()).isEqualTo(OperationalStatus.DISPONIBLE);
        assertThat(space.maxCapacity()).isEqualTo(30);
        assertThat(space.equipment()).containsExactly("PROYECTOR", "AIRE_ACONDICIONADO");
        assertThat(space.condition()).isNull();

        Resource asset = catalog.resources().get(1);
        assertThat(asset.id()).isEqualTo("ACT-000912");
        assertThat(asset.category()).isEqualTo(ResourceCategory.ACTIVO);
        assertThat(asset.operationalStatus()).isEqualTo(OperationalStatus.EN_MANTENIMIENTO);
        assertThat(asset.condition()).isEqualTo("BUENO");
        assertThat(asset.loanTermBusinessDays()).isEqualTo(3);
        assertThat(asset.maxCapacity()).isNull();
    }

    @Test
    void walksEveryPageOfTheCatalog() {
        stubAllPages();

        Catalog catalog = adapter(10, Duration.ofSeconds(2)).searchCatalog(NO_FILTER);

        assertThat(catalog.resources()).extracting(Resource::id)
                .containsExactly("ESP-0412", "ACT-000912", "ESP-0107", "ESP-0108", "ACT-001200");
        assertThat(catalog.truncated()).isFalse();
        assertThat(catalog.resources().get(4).loanTermBusinessDays()).isNull();
        module1.verify(3, getRequestedFor(urlPathEqualTo("/api/v1/resources")));
    }

    @Test
    void stopsAtMaxPagesAndMarksTheCatalogAsTruncated() {
        stubAllPages();

        Catalog catalog = adapter(2, Duration.ofSeconds(2)).searchCatalog(NO_FILTER);

        assertThat(catalog.resources()).hasSize(4);
        assertThat(catalog.truncated()).isTrue();
        module1.verify(2, getRequestedFor(urlPathEqualTo("/api/v1/resources")));
    }

    @Test
    void sendsTheFiltersAndThePageSizeToInventory() {
        stubPage(1, "module1-catalog-empty.json");

        adapter(10, Duration.ofSeconds(2))
                .searchCatalog(new ResourceFilter(ResourceCategory.ESPACIO, "LABORATORIO", 20));

        module1.verify(getRequestedFor(urlPathEqualTo("/api/v1/resources"))
                .withQueryParam("category", equalTo("ESPACIO"))
                .withQueryParam("type", equalTo("LABORATORIO"))
                .withQueryParam("minCapacity", equalTo("20"))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("size", equalTo("2")));
    }

    @Test
    void anEmptyCatalogIsAnEmptyListNotAnError() {
        stubPage(1, "module1-catalog-empty.json");

        Catalog catalog = adapter(10, Duration.ofSeconds(2)).searchCatalog(NO_FILTER);

        assertThat(catalog.resources()).isEmpty();
        assertThat(catalog.truncated()).isFalse();
    }

    @Test
    void anOperationalStatusThatIsNotInventorysIsAContractError() {
        module1.stubFor(get(urlPathEqualTo("/api/v1/resources")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(read("module1-catalog-page2.json").replace("\"EN_USO\"", "\"RESERVADO\""))));

        assertUnavailable(() -> adapter(10, Duration.ofSeconds(2)).searchCatalog(NO_FILTER));
    }

    @Test
    void aMissingRequiredFieldIsAContractError() {
        module1.stubFor(get(urlPathEqualTo("/api/v1/resources")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(read("module1-catalog-page2.json").replace("\"name\": \"Sala de Estudio 3\",", ""))));

        assertUnavailable(() -> adapter(10, Duration.ofSeconds(2)).searchCatalog(NO_FILTER));
    }

    @Test
    void anUnreadableBodyIsUnavailable() {
        module1.stubFor(get(urlPathEqualTo("/api/v1/resources")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("{no es json")));

        assertUnavailable(() -> adapter(10, Duration.ofSeconds(2)).searchCatalog(NO_FILTER));
    }

    @Test
    void every4xxAnd5xxStatusIsUnavailable() {
        for (int status : new int[]{400, 401, 403, 500, 503}) {
            module1.stubFor(get(urlPathEqualTo("/api/v1/resources")).willReturn(aResponse().withStatus(status)));

            assertUnavailable(() -> adapter(10, Duration.ofSeconds(2)).searchCatalog(NO_FILTER));
        }
    }

    @Test
    void aTimeoutIsUnavailable() {
        module1.stubFor(get(urlPathEqualTo("/api/v1/resources")).willReturn(aResponse()
                .withFixedDelay(1500).withHeader("Content-Type", "application/json")
                .withBody(read("module1-catalog-empty.json"))));

        assertUnavailable(() -> adapter(10, Duration.ofMillis(300)).searchCatalog(NO_FILTER));
    }

    @Test
    void aRefusedConnectionIsUnavailable() {
        InventoryRestAdapter adapter = adapter(10, Duration.ofSeconds(1));
        module1.stop();

        assertUnavailable(() -> adapter.searchCatalog(NO_FILTER));
    }

    @Test
    void readsTheOperationalStatusOfABatchAndLeavesOutWhatInventoryDoesNotKnow() {
        module1.stubFor(post(urlPathEqualTo("/api/v1/resources/operational-status"))
                .withRequestBody(equalToJson("{\"resourceIds\": [\"ESP-0412\", \"ESP-0107\", \"ACT-000912\", \"ESP-9999\"]}"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(read("module1-operational-status.json"))));

        Map<String, OperationalStatus> statuses = adapter(10, Duration.ofSeconds(2))
                .operationalStatus(List.of("ESP-0412", "ESP-0107", "ACT-000912", "ESP-9999"));

        assertThat(statuses).containsOnly(
                Map.entry("ESP-0412", OperationalStatus.DISPONIBLE),
                Map.entry("ESP-0107", OperationalStatus.EN_USO),
                Map.entry("ACT-000912", OperationalStatus.EN_MANTENIMIENTO));
    }

    @Test
    void doesNotCallInventoryForAnEmptyBatch() {
        assertThat(adapter(10, Duration.ofSeconds(2)).operationalStatus(List.of())).isEmpty();
        module1.verify(0, postRequestedFor(urlPathEqualTo("/api/v1/resources/operational-status")));
    }

    private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ExternalServiceUnavailableException.class,
                exception -> assertThat(exception.module()).isEqualTo(ExternalModule.MODULO_1));
    }
}
