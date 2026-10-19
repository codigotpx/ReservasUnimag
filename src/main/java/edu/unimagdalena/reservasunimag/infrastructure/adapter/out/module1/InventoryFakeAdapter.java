package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.module1;

import edu.unimagdalena.reservasunimag.domain.model.resource.Catalog;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceFilter;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus.DISPONIBLE;
import static edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus.EN_MANTENIMIENTO;
import static edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus.EN_USO;

/** Catálogo de ejemplo del perfil local, mientras el Módulo 1 no publique su API (P-16). */
@Component
@Profile("local")
class InventoryFakeAdapter implements InventoryPort {

    private static final List<Resource> CATALOG = List.of(
            Resource.space("ESP-0412", "Laboratorio de Redes", "LABORATORIO", "Bloque 7, piso 2", DISPONIBLE,
                    "Ingeniería", 30, List.of("PROYECTOR", "AIRE_ACONDICIONADO")),
            Resource.space("ESP-0413", "Laboratorio de Física", "LABORATORIO", "Bloque 5, piso 1", DISPONIBLE,
                    "Ciencias Básicas", 25, List.of("PROYECTOR")),
            Resource.space("ESP-0107", "Sala de Estudio 3", "SALA_DE_ESTUDIO", "Biblioteca, piso 1", DISPONIBLE,
                    "Biblioteca Central", 8, List.of("AIRE_ACONDICIONADO")),
            Resource.space("ESP-0108", "Sala de Estudio 4", "SALA_DE_ESTUDIO", "Biblioteca, piso 1", EN_USO,
                    "Biblioteca Central", 8, List.of("AIRE_ACONDICIONADO")),
            Resource.space("ESP-0201", "Auditorio Principal", "AUDITORIO", "Edificio Central", DISPONIBLE,
                    "Rectoría", 200, List.of("PROYECTOR", "SONIDO")),
            Resource.space("ESP-0305", "Salón 305", "SALON", "Bloque 3, piso 3", DISPONIBLE,
                    "Ingeniería", 40, List.of("PROYECTOR")),
            Resource.asset("ACT-000912", "Microscopio M-014", "MICROSCOPIO", "Laboratorio de Biología",
                    EN_MANTENIMIENTO, "BUENO", 3),
            Resource.asset("ACT-000913", "Microscopio M-015", "MICROSCOPIO", "Laboratorio de Biología",
                    DISPONIBLE, "BUENO", 3),
            Resource.asset("ACT-001200", "Videobeam V-02", "VIDEOBEAM", "Almacén de Audiovisuales",
                    DISPONIBLE, "REGULAR", 1),
            Resource.asset("ACT-001450", "Portátil P-110", "PORTATIL", "Almacén de Sistemas",
                    DISPONIBLE, "BUENO", 5));

    @Override
    public Catalog searchCatalog(ResourceFilter filter) {
        List<Resource> matching = CATALOG.stream()
                .filter(resource -> filter.category() == null || resource.category() == filter.category())
                .filter(resource -> filter.type() == null || resource.type().equals(filter.type()))
                .filter(filter::acceptsCapacityOf)
                .toList();
        return new Catalog(matching, false);
    }

    @Override
    public Optional<Resource> details(String resourceId) {
        return CATALOG.stream().filter(resource -> resource.id().equals(resourceId)).findFirst();
    }

    @Override
    public Map<String, OperationalStatus> operationalStatus(Collection<String> resourceIds) {
        return CATALOG.stream()
                .filter(resource -> resourceIds.contains(resource.id()))
                .collect(Collectors.toMap(Resource::id, Resource::operationalStatus, (a, b) -> a));
    }
}
