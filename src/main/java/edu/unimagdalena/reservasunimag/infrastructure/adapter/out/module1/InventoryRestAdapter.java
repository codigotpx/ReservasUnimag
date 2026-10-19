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
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.module1.dto.OperationalStatusRequestDto;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.module1.dto.OperationalStatusResponseDto;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.module1.dto.ResourceDto;
import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente del Módulo 1 según UC1 Contratos §2. Sin reintentos y sin autenticación. Cualquier falla —timeout,
 * 4xx, 5xx, cuerpo ilegible, un valor fuera del contrato— sale como {@link ExternalServiceUnavailableException},
 * porque sin catálogo no hay nada que mostrar (FR-008). No se registran datos personales: solo duración y resultado.
 */
@Component
@Profile("!local")
class InventoryRestAdapter implements InventoryPort {

    private static final Logger log = LoggerFactory.getLogger(InventoryRestAdapter.class);

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
        List<Resource> resources = new ArrayList<>();
        boolean truncated = false;
        try {
            for (int page = 1; ; page++) {
                CatalogPageDto body = fetchPage(filter, page);
                if (body == null || body.content() == null) {
                    throw new ContractViolation("la página " + page + " llegó sin contenido");
                }
                body.content().forEach(dto -> resources.add(toDomain(dto)));
                if (page >= body.totalPages()) {
                    break;
                }
                if (page >= maxPages) {
                    truncated = true;
                    break;
                }
            }
        } catch (RestClientException | ContractViolation failure) {
            log.error("Módulo 1: catálogo falló tras {} ms: {}", millisSince(startedAt), failure.getMessage());
            throw new ExternalServiceUnavailableException(ExternalModule.MODULO_1,
                    "No se pudo leer el catálogo del Módulo 1", failure);
        }
        log.info("Módulo 1: catálogo de {} recursos en {} ms{}", resources.size(), millisSince(startedAt),
                truncated ? " (truncado)" : "");
        return new Catalog(resources, truncated);
    }

    private CatalogPageDto fetchPage(ResourceFilter filter, int page) {
        return client.get()
                .uri(uri -> {
                    uri.path("/api/v1/resources");
                    if (filter.category() != null) {
                        uri.queryParam("category", filter.category().name());
                    }
                    if (filter.type() != null) {
                        uri.queryParam("type", filter.type());
                    }
                    if (filter.minCapacity() != null) {
                        uri.queryParam("minCapacity", filter.minCapacity());
                    }
                    return uri.queryParam("page", page).queryParam("size", pageSize).build();
                })
                .retrieve()
                .body(CatalogPageDto.class);
    }

    @Override
    public Map<String, OperationalStatus> operationalStatus(Collection<String> resourceIds) {
        if (resourceIds.isEmpty()) {
            return Map.of();
        }
        long startedAt = System.nanoTime();
        try {
            OperationalStatusResponseDto body = client.post()
                    .uri("/api/v1/resources/operational-status")
                    .body(new OperationalStatusRequestDto(List.copyOf(resourceIds)))
                    .retrieve()
                    .body(OperationalStatusResponseDto.class);
            if (body == null || body.statuses() == null) {
                throw new ContractViolation("la respuesta de estado operativo llegó sin contenido");
            }
            Map<String, OperationalStatus> statuses = new HashMap<>();
            body.statuses().forEach(entry ->
                    statuses.put(required(entry.resourceId(), "resourceId"), status(entry.operationalStatus())));
            if (body.notFound() != null && !body.notFound().isEmpty()) {
                log.warn("Módulo 1: {} recursos ya no existen en el inventario", body.notFound().size());
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
        String name = required(dto.name(), "name");
        String type = required(dto.type(), "type");
        String location = required(dto.location(), "location");
        OperationalStatus status = status(dto.operationalStatus());
        ResourceCategory category = parseCategory(dto.category());
        if (category == ResourceCategory.ACTIVO) {
            return Resource.asset(id, name, type, location, status, dto.condition(), dto.loanTermBusinessDays());
        }
        if (dto.maxCapacity() == null) {
            throw new ContractViolation("el espacio " + id + " llegó sin maxCapacity");
        }
        return Resource.space(id, name, type, location, status, dto.faculty(), dto.maxCapacity(), dto.equipment());
    }

    private static ResourceCategory parseCategory(String value) {
        try {
            return ResourceCategory.valueOf(required(value, "category"));
        } catch (IllegalArgumentException unknown) {
            throw new ContractViolation("category desconocida: " + value);
        }
    }

    /** Un valor que no sea de los tres del Módulo 1 —p. ej. {@code RESERVADO}— rompe el reparto de estados. */
    private static OperationalStatus status(String value) {
        try {
            return OperationalStatus.valueOf(required(value, "operationalStatus"));
        } catch (IllegalArgumentException unknown) {
            throw new ContractViolation("operationalStatus desconocido: " + value);
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
