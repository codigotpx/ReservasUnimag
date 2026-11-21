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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contra la API real del Módulo 1: {@code /api/v1/recursos}, en español, con páginas desde 0. */
class InventoryRestAdapterTest {

    private static final ResourceFilter NO_FILTER = new ResourceFilter(null, null, null);
    private static final String CATALOG = "/api/v1/recursos";

    private static final String LAB = "c3e864b1-3199-4f34-8617-f2b217d9be5c";
    private static final String MICROSCOPE = "22d6bbf2-c0ac-4505-bb9b-38cddd0a43cb";
    private static final String STUDY_ROOM = "5a0e7c12-8d43-4b9f-a1e6-3f2c9d7b0e41";
    private static final String AUDITORIUM = "9b4f1e26-0c57-4d83-b2a9-6e1d8c3f7a05";
    private static final String LAPTOP = "e2d7a9c4-1b36-4f08-9c5e-7a4b0d2e6f18";
    private static final String BOOK = "7f3a2c91-4e6b-4d05-8a17-0c9e5b2d3f64";

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
        module1.stubFor(get(urlPathEqualTo(CATALOG))
                .withQueryParam("page", equalTo(String.valueOf(page)))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(read(fixture))));
    }

    private void stubAllPages() {
        stubPage(0, "module1-catalog-page1.json");
        stubPage(1, "module1-catalog-page2.json");
        stubPage(2, "module1-catalog-page3.json");
    }

    @Test
    void mapsTheCatalogOfASpaceAndAnAsset() {
        stubAllPages();

        Catalog catalog = adapter(10, Duration.ofSeconds(2)).searchCatalog(NO_FILTER);

        Resource space = catalog.resources().get(0);
        assertThat(space.id()).isEqualTo(LAB);
        assertThat(space.name()).isEqualTo("Laboratorio de Redes");
        assertThat(space.category()).isEqualTo(ResourceCategory.ESPACIO);
        assertThat(space.operationalStatus()).isEqualTo(OperationalStatus.DISPONIBLE);
        assertThat(space.location()).isEqualTo("Bloque 7, piso 2");
        assertThat(space.faculty()).isEqualTo("Ingeniería");
        assertThat(space.maxCapacity()).isEqualTo(30);
        assertThat(space.equipment()).isEmpty();
        assertThat(space.condition()).isNull();

        Resource asset = catalog.resources().get(1);
        assertThat(asset.id()).isEqualTo(MICROSCOPE);
        assertThat(asset.category()).isEqualTo(ResourceCategory.ACTIVO);
        assertThat(asset.type()).isEqualTo("MICROSCOPIO");
        assertThat(asset.operationalStatus()).isEqualTo(OperationalStatus.EN_MANTENIMIENTO);
        assertThat(asset.condition()).isEqualTo("OPTIMO");
        assertThat(asset.loanTermBusinessDays()).isEqualTo(3);
        assertThat(asset.maxCapacity()).isNull();
    }

    @Test
    void walksEveryPageOfTheCatalogFromPageZero() {
        stubAllPages();

        Catalog catalog = adapter(10, Duration.ofSeconds(2)).searchCatalog(NO_FILTER);

        assertThat(catalog.resources()).extracting(Resource::id)
                .containsExactly(LAB, MICROSCOPE, STUDY_ROOM, AUDITORIUM, LAPTOP);
        assertThat(catalog.truncated()).isFalse();
        assertThat(catalog.resources().get(4).loanTermBusinessDays()).isNull();
        module1.verify(3, getRequestedFor(urlPathEqualTo(CATALOG)));
        module1.verify(getRequestedFor(urlPathEqualTo(CATALOG))
                .withQueryParam("page", equalTo("0")).withQueryParam("size", equalTo("2")));
    }

    @Test
    void keepsEveryStateInventoryReportsAndItsCode() {
        stubAllPages();

        Catalog catalog = adapter(10, Duration.ofSeconds(2)).searchCatalog(NO_FILTER);

        // También RESERVADO y BLOQUEO_ACADEMICO: si el inventario los marca, el recurso no se ofrece
        assertThat(catalog.resources().get(3).operationalStatus()).isEqualTo(OperationalStatus.RESERVADO);
        assertThat(catalog.resources().get(4).operationalStatus()).isEqualTo(OperationalStatus.BLOQUEO_ACADEMICO);
        assertThat(catalog.resources().get(2).operationalStatus()).isEqualTo(OperationalStatus.EN_USO);
        assertThat(catalog.resources()).extracting(Resource::code)
                .containsExactly("ESP-002", "ACT-001", "ESP-003", "ESP-004", "ACT-002");
    }

    @Test
    void stopsAtMaxPagesAndMarksTheCatalogAsTruncated() {
        stubAllPages();

        Catalog catalog = adapter(2, Duration.ofSeconds(2)).searchCatalog(NO_FILTER);

        assertThat(catalog.resources()).hasSize(4);
        assertThat(catalog.truncated()).isTrue();
        module1.verify(2, getRequestedFor(urlPathEqualTo(CATALOG)));
    }

    @Test
    void filtersHereBecauseInventoryDoesNotFilter() {
        stubAllPages();
        InventoryRestAdapter adapter = adapter(10, Duration.ofSeconds(2));

        assertThat(adapter.searchCatalog(new ResourceFilter(ResourceCategory.ESPACIO, null, 20)).resources())
                .extracting(Resource::id).containsExactly(LAB, AUDITORIUM);
        assertThat(adapter.searchCatalog(new ResourceFilter(ResourceCategory.ACTIVO, null, null)).resources())
                .extracting(Resource::id).containsExactly(MICROSCOPE, LAPTOP);
        assertThat(adapter.searchCatalog(new ResourceFilter(null, "portatil", null)).resources())
                .extracting(Resource::id).containsExactly(LAPTOP);
    }

    @Test
    void anEmptyCatalogIsAnEmptyListNotAnError() {
        stubPage(0, "module1-catalog-empty.json");

        Catalog catalog = adapter(10, Duration.ofSeconds(2)).searchCatalog(NO_FILTER);

        assertThat(catalog.resources()).isEmpty();
        assertThat(catalog.truncated()).isFalse();
    }

    @Test
    void anUnknownStateIsAContractError() {
        module1.stubFor(get(urlPathEqualTo(CATALOG)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(read("module1-catalog-page2.json").replace("\"EN_USO\"", "\"DADO_DE_BAJA\""))));

        assertUnavailable(() -> adapter(10, Duration.ofSeconds(2)).searchCatalog(NO_FILTER));
    }

    @Test
    void aMissingRequiredFieldIsAContractError() {
        module1.stubFor(get(urlPathEqualTo(CATALOG)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(read("module1-catalog-page2.json").replace("\"nombre\": \"Sala de Estudio 3\",", ""))));

        assertUnavailable(() -> adapter(10, Duration.ofSeconds(2)).searchCatalog(NO_FILTER));
    }

    @Test
    void anUnreadableBodyIsUnavailable() {
        module1.stubFor(get(urlPathEqualTo(CATALOG)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("{no es json")));

        assertUnavailable(() -> adapter(10, Duration.ofSeconds(2)).searchCatalog(NO_FILTER));
    }

    @Test
    void every4xxAnd5xxStatusIsUnavailable() {
        for (int status : new int[]{400, 401, 403, 404, 500, 503}) {
            module1.stubFor(get(urlPathEqualTo(CATALOG)).willReturn(aResponse().withStatus(status)));

            assertUnavailable(() -> adapter(10, Duration.ofSeconds(2)).searchCatalog(NO_FILTER));
        }
    }

    @Test
    void aTimeoutIsUnavailable() {
        module1.stubFor(get(urlPathEqualTo(CATALOG)).willReturn(aResponse()
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

    // --- Estado por lote: el Módulo 1 no lo tiene, sale del catálogo ------------------------------------------

    @Test
    void readsTheOperationalStatusOfABatchFromTheCatalogAndLeavesOutWhatInventoryDoesNotKnow() {
        stubAllPages();

        Map<String, OperationalStatus> statuses = adapter(10, Duration.ofSeconds(2))
                .operationalStatus(List.of(LAB, STUDY_ROOM, MICROSCOPE, "00000000-0000-0000-0000-000000000000"));

        assertThat(statuses).containsOnly(
                Map.entry(LAB, OperationalStatus.DISPONIBLE),
                Map.entry(STUDY_ROOM, OperationalStatus.EN_USO),
                Map.entry(MICROSCOPE, OperationalStatus.EN_MANTENIMIENTO));
    }

    @Test
    void aTruncatedCatalogCannotAnswerABatch() {
        stubAllPages();

        assertUnavailable(() -> adapter(2, Duration.ofSeconds(2)).operationalStatus(List.of(LAPTOP)));
    }

    @Test
    void doesNotCallInventoryForAnEmptyBatch() {
        assertThat(adapter(10, Duration.ofSeconds(2)).operationalStatus(List.of())).isEmpty();
        module1.verify(0, getRequestedFor(urlPathEqualTo(CATALOG)));
    }

    // --- La ficha de un recurso ------------------------------------------------------------------------------

    private void stubDetails(String resourceId, String fixture) {
        module1.stubFor(get(urlPathEqualTo(CATALOG + "/" + resourceId))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(read(fixture))));
    }

    @Test
    void readsTheDetailsOfAnAssetWithItsLoanTerm() {
        stubDetails(BOOK, "module1-asset-details.json");

        Resource asset = adapter(10, Duration.ofSeconds(2)).details(BOOK).orElseThrow();

        assertThat(asset.id()).isEqualTo(BOOK);
        assertThat(asset.name()).isEqualTo("Libro de Cálculo I");
        assertThat(asset.category()).isEqualTo(ResourceCategory.ACTIVO);
        assertThat(asset.type()).isEqualTo("LIBRO");
        assertThat(asset.location()).isEqualTo("Biblioteca, estantería C-4");
        assertThat(asset.operationalStatus()).isEqualTo(OperationalStatus.DISPONIBLE);
        assertThat(asset.condition()).isEqualTo("OPTIMO");
        assertThat(asset.loanTermBusinessDays()).isEqualTo(7);
        assertThat(asset.maxCapacity()).isNull();
        module1.verify(1, getRequestedFor(urlPathEqualTo(CATALOG + "/" + BOOK)));
    }

    @Test
    void readsTheDetailsOfASpaceWithItsEquipment() {
        stubDetails(STUDY_ROOM, "module1-space-details.json");

        Resource space = adapter(10, Duration.ofSeconds(2)).details(STUDY_ROOM).orElseThrow();

        assertThat(space.category()).isEqualTo(ResourceCategory.ESPACIO);
        assertThat(space.operationalStatus()).isEqualTo(OperationalStatus.EN_MANTENIMIENTO);
        assertThat(space.faculty()).isEqualTo("Biblioteca Central");
        assertThat(space.maxCapacity()).isEqualTo(8);
        assertThat(space.equipment()).containsExactly("AIRE_ACONDICIONADO", "PROYECTOR");
        assertThat(space.loanTermBusinessDays()).isNull();
    }

    @Test
    void aMissingLoanTermIsKeptMissingAndNeverDefaulted() {
        // Ficha incompleta (P-16): la decide UC2 con INCOMPLETE_RESOURCE_DETAILS, no el adaptador con un valor nuestro.
        stubDetails(BOOK, "module1-asset-details-no-term.json");

        assertThat(adapter(10, Duration.ofSeconds(2)).details(BOOK).orElseThrow().loanTermBusinessDays()).isNull();
    }

    @Test
    void aLoanTermOfZeroIsOnSiteUseAndNotMissing() {
        module1.stubFor(get(urlPathEqualTo(CATALOG + "/" + BOOK)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(read("module1-asset-details.json").replace("\"plazoDevolucion\": 7", "\"plazoDevolucion\": 0"))));

        assertThat(adapter(10, Duration.ofSeconds(2)).details(BOOK).orElseThrow().loanTermBusinessDays()).isZero();
    }

    @Test
    void anUnknownOrMalformedIdIsEmptyAndNotAnError() {
        module1.stubFor(get(urlPathEqualTo(CATALOG + "/" + LAB)).willReturn(aResponse().withStatus(404)));
        // El Módulo 1 contesta 400 a un id que no es UUID
        module1.stubFor(get(urlPathEqualTo(CATALOG + "/ESP-9999")).willReturn(aResponse().withStatus(400)));

        assertThat(adapter(10, Duration.ofSeconds(2)).details(LAB)).isEmpty();
        assertThat(adapter(10, Duration.ofSeconds(2)).details("ESP-9999")).isEmpty();
    }

    @Test
    void anyOtherStatusOfTheDetailsIsUnavailable() {
        for (int status : new int[]{401, 403, 500, 503}) {
            module1.stubFor(get(urlPathEqualTo(CATALOG + "/" + STUDY_ROOM)).willReturn(aResponse().withStatus(status)));

            assertUnavailable(() -> adapter(10, Duration.ofSeconds(2)).details(STUDY_ROOM));
        }
    }

    @Test
    void brokenDetailsAreAContractErrorAndUnavailable() {
        String details = read("module1-space-details.json");
        for (String body : new String[]{
                "",
                "{no es json",
                details.replace("\"EN_MANTENIMIENTO\"", "\"DADO_DE_BAJA\""),
                details.replace("\"nombre\": \"Sala de Estudio 3\",", ""),
                details.replace("\"ESPACIO\"", "\"OTRA\""),
                details.replace("\"aforoMaximo\": 8,", "")}) {
            module1.stubFor(get(urlPathEqualTo(CATALOG + "/" + STUDY_ROOM)).willReturn(aResponse()
                    .withHeader("Content-Type", "application/json").withBody(body)));

            assertUnavailable(() -> adapter(10, Duration.ofSeconds(2)).details(STUDY_ROOM));
        }
    }

    @Test
    void aSlowOrAbsentModule1IsUnavailableForTheDetails() {
        module1.stubFor(get(urlPathEqualTo(CATALOG + "/" + STUDY_ROOM)).willReturn(aResponse()
                .withFixedDelay(1500).withHeader("Content-Type", "application/json")
                .withBody(read("module1-space-details.json"))));
        assertUnavailable(() -> adapter(10, Duration.ofMillis(300)).details(STUDY_ROOM));

        InventoryRestAdapter adapter = adapter(10, Duration.ofSeconds(1));
        module1.stop();
        assertUnavailable(() -> adapter.details(STUDY_ROOM));
    }

    private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ExternalServiceUnavailableException.class,
                exception -> assertThat(exception.module()).isEqualTo(ExternalModule.MODULO_1));
    }
}
