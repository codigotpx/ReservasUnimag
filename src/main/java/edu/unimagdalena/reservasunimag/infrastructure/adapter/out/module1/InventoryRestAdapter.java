package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.module1;

import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException.ExternalModule;
import edu.unimagdalena.reservasunimag.domain.model.resource.Catalog;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceFilter;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.module1.dto.CatalogPageDto;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.module1.dto.ResourceDto;
import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Cliente del Módulo 1 contra su API real ({@code /api/v1/recursos}, en español y con HTTP Basic), que no es la
 * propuesta de UC1 Contratos §2: ver contratos-integracion.md, "La API real del Módulo 1". Sin reintentos. Cualquier
 * falla —timeout, 4xx, 5xx, cuerpo ilegible, un valor fuera de lo conocido— sale como
 * {@link ExternalServiceUnavailableException}, porque sin catálogo no hay nada que mostrar (FR-008).
 *
 * <p>El Módulo 1 no filtra ni tiene estado por lote: los filtros se aplican aquí y el lote sale del catálogo
 * completo. {@code RESERVADO} y {@code BLOQUEO_ACADEMICO} son estados que calcula este módulo con sus reservas, así
 * que para nosotros el recurso está {@code DISPONIBLE} y la disponibilidad la decide el cruce con las reservas.
 */
@Component
@Profile("!local")
class InventoryRestAdapter implements InventoryPort {

    private static final Logger log = LoggerFactory.getLogger(InventoryRestAdapter.class);

    private static final Set<String> OUR_STATES = Set.of("RESERVADO", "BLOQUEO_ACADEMICO");

    private final RestClient client;
    private final int pageSize;
    private final int maxPages;

    InventoryRestAdapter(RestClient module1RestClient, ReservationProperties properties) {
        this.client = module1RestClient;
        this.pageSize = properties.module1().pageSize();
        this.maxPages = properties.module1().maxPages();
    }

    @Override
    public Catalog searchCatalog(ResourceFilter filter) {
        long startedAt = System.nanoTime();
        try {
            Catalog all = readCatalog();
            List<Resource> matching = all.resources().stream().filter(matches(filter)).toList();
            log.info("Módulo 1: catálogo de {} recursos ({} con el filtro) en {} ms{}", all.resources().size(),
                    matching.size(), millisSince(startedAt), all.truncated() ? " (truncado)" : "");
            return new Catalog(matching, all.truncated());
        } catch (RestClientException | ContractViolation failure) {
            log.error("Módulo 1: catálogo falló tras {} ms: {}", millisSince(startedAt), failure.getMessage());
            throw new ExternalServiceUnavailableException(ExternalModule.MODULO_1,
                    "No se pudo leer el catálogo del Módulo 1", failure);
        }
    }

    private Catalog readCatalog() {
        List<Resource> resources = new ArrayList<>();
        for (int page = 0; ; page++) {
            CatalogPageDto body = fetchPage(page);
            if (body == null || body.content() == null) {
                throw new ContractViolation("la página " + page + " llegó sin contenido");
            }
            body.content().forEach(dto -> resources.add(toDomain(dto)));
            int size = body.size() > 0 ? body.size() : pageSize;
            long totalPages = (body.totalElements() + size - 1) / size;
            if (page + 1 >= totalPages || body.content().isEmpty()) {
                return new Catalog(resources, false);
            }
            if (page + 1 >= maxPages) {
                return new Catalog(resources, true);
            }
        }
    }

    private CatalogPageDto fetchPage(int page) {
        return client.get()
                .uri(uri -> uri.path("/api/v1/recursos")
                        .queryParam("page", page)
                        .queryParam("size", pageSize)
                        .build())
                .retrieve()
                .body(CatalogPageDto.class);
    }

    private static Predicate<Resource> matches(ResourceFilter filter) {
        return resource -> (filter.category() == null || filter.category() == resource.category())
                && (filter.type() == null || filter.type().equalsIgnoreCase(resource.type()))
                && (filter.minCapacity() == null
                    || (resource.maxCapacity() != null && resource.maxCapacity() >= filter.minCapacity()));
    }

    @Override
    public Optional<Resource> details(String resourceId) {
        long startedAt = System.nanoTime();
        try {
            ResourceDto body = client.get()
                    .uri("/api/v1/recursos/{id}", resourceId)
                    .retrieve()
                    .body(ResourceDto.class);
            if (body == null) {
                throw new ContractViolation("la ficha de " + resourceId + " llegó sin contenido");
            }
            log.info("Módulo 1: ficha de un recurso en {} ms", millisSince(startedAt));
            return Optional.of(toDomain(body));
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest unknown) {
            // El Módulo 1 contesta 400 a un id que no es UUID: para nosotros es igual que no existir
            log.info("Módulo 1: el recurso no existe ({} ms)", millisSince(startedAt));
            return Optional.empty();
        } catch (RestClientException | ContractViolation failure) {
            log.error("Módulo 1: ficha falló tras {} ms: {}", millisSince(startedAt), failure.getMessage());
            throw new ExternalServiceUnavailableException(ExternalModule.MODULO_1,
                    "No se pudo leer la ficha del recurso en el Módulo 1", failure);
        }
    }

    @Override
    public Map<String, OperationalStatus> operationalStatus(Collection<String> resourceIds) {
        if (resourceIds.isEmpty()) {
            return Map.of();
        }
        long startedAt = System.nanoTime();
        try {
            Catalog all = readCatalog();
            if (all.truncated()) {
                throw new ContractViolation("el catálogo no cabe en " + maxPages + " páginas");
            }
            Set<String> wanted = Set.copyOf(resourceIds);
            Map<String, OperationalStatus> statuses = new HashMap<>();
            all.resources().stream().filter(resource -> wanted.contains(resource.id()))
                    .forEach(resource -> statuses.put(resource.id(), resource.operationalStatus()));
            if (statuses.size() < wanted.size()) {
                log.warn("Módulo 1: {} recursos ya no existen en el inventario", wanted.size() - statuses.size());
            }
            log.info("Módulo 1: estado operativo de {} recursos en {} ms", statuses.size(), millisSince(startedAt));
            return statuses;
        } catch (RestClientException | ContractViolation failure) {
            log.error("Módulo 1: estado operativo falló tras {} ms: {}", millisSince(startedAt), failure.getMessage());
            throw new ExternalServiceUnavailableException(ExternalModule.MODULO_1,
                    "No se pudo leer el estado operativo del Módulo 1", failure);
        }
    }

    private static Resource toDomain(ResourceDto dto) {
        String id = required(dto.id(), "id");
        String name = required(dto.nombre(), "nombre");
        String location = required(dto.ubicacion(), "ubicacion");
        OperationalStatus status = status(dto.estado());
        ResourceCategory category = parseCategory(dto.tipo());
        if (category == ResourceCategory.ACTIVO) {
            return Resource.asset(id, name, dto.tipoActivo(), location, status, dto.estadoFisico(),
                    dto.plazoDevolucion());
        }
        if (dto.aforoMaximo() == null) {
            throw new ContractViolation("el espacio " + id + " llegó sin aforoMaximo");
        }
        return Resource.space(id, name, null, location, status, dto.facultad(), dto.aforoMaximo(),
                equipment(dto.equipamiento()));
    }

    /** Texto tal cual, o el {@code nombre} si el Módulo 1 manda objetos. */
    private static List<String> equipment(List<JsonNode> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(item -> item.isString() ? item.asString()
                        : item.hasNonNull("nombre") ? item.get("nombre").asString() : item.toString())
                .toList();
    }

    private static ResourceCategory parseCategory(String value) {
        try {
            return ResourceCategory.valueOf(required(value, "tipo"));
        } catch (IllegalArgumentException unknown) {
            throw new ContractViolation("tipo desconocido: " + value);
        }
    }

    private static OperationalStatus status(String value) {
        String estado = required(value, "estado");
        if (OUR_STATES.contains(estado)) {
            return OperationalStatus.DISPONIBLE;
        }
        try {
            return OperationalStatus.valueOf(estado);
        } catch (IllegalArgumentException unknown) {
            throw new ContractViolation("estado desconocido: " + value);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ContractViolation("falta el campo " + field);
        }
        return value;
    }

    private static long millisSince(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static final class ContractViolation extends RuntimeException {
        ContractViolation(String message) {
            super(message);
        }
    }
}
