# Feature Specification: Consultar recursos

**Created**: 2026-08-24
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Consultar recursos`
**Prioridad global**: P1

## Contexto

Punto de entrada del Módulo 2. Permite al Estudiante (y al Monitor, que hereda sus capacidades) ver el catálogo de recursos físicos de la universidad —salones, laboratorios, salas de estudio y equipos— y saber cuáles están realmente disponibles en una fecha y franja horaria.

Es el único caso de uso del módulo que no depende de ningún otro: el estudiante puede entrar, mirar qué hay libre y salir sin hacer nada más. Desde aquí puede continuar hacia `Reservar recursos` si decide apartar algo. Lo que ve es una foto del momento: la disponibilidad de cada recurso se comprueba contra el inventario del Módulo 1 a través de `Consultar disponibilidad de los recursos`, y se vuelve a comprobar al confirmar una reserva, porque entre mirar y decidir alguien más pudo haberse adelantado.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Estudiante | Primario | Consulta el catálogo filtrando por fecha, franja y tipo de recurso. |
| Monitor | Primario | Especialización de Estudiante: hereda esta capacidad. |
| Dirección de Programa | Primario | Consulta el catálogo para ver la ocupación real de la infraestructura. |
| Módulo 1 | Secundario | Aporta el estado real de cada recurso; es el dueño del inventario. |

**Casos de uso relacionados**

- `Reservar recursos` — continuación opcional de esta consulta: el estudiante puede quedarse solo mirando, o seguir y apartar uno de los recursos que encontró libres; ver [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md)
- `Consultar disponibilidad de los recursos` — la comprobación que responde, recurso por recurso, si está libre en la franja pedida; es con lo que se arma esta lista; ver [spec-modulo2-uc8-consultar-disponibilidad-recursos.md](./spec-modulo2-uc8-consultar-disponibilidad-recursos.md)
- `Consultar reportes` — obtiene del Módulo 3 si la persona está sancionada, para poder avisárselo antes de que intente apartar algo; ver [spec-modulo2-uc6-consultar-reportes.md](./spec-modulo2-uc6-consultar-reportes.md)
- `Importar horarios semestrales` — fuente de los estados `BLOQUEO_ACADEMICO` que esta consulta debe respetar; ver [spec-modulo2-uc3-importar-horarios-semestrales.md](./spec-modulo2-uc3-importar-horarios-semestrales.md)

**Estados del recurso (Módulo 1)**: conforme a [gestionunimag.md](../gestionunimag.md), el inventario gestiona cinco estados: `DISPONIBLE`, `RESERVADO`, `BLOQUEO_ACADEMICO`, `EN_USO` y `EN_MANTENIMIENTO`. Esta consulta respeta ese catálogo.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar recursos disponibles (Priority: P1)

Como Estudiante (o Monitor), quiero consultar el catálogo de recursos filtrando por fecha, franja horaria y tipo de recurso, para ver únicamente aquellos que están realmente disponibles y no perder tiempo intentando apartar espacios ocupados o bloqueados por clase.

**Why this priority**: Es la puerta de entrada del módulo y el único caso de uso que no depende de ningún otro (todos los demás parten de aquí o usan la información que aquí se muestra). Por sí solo ya entrega valor: elimina el recorrido físico por el campus para averiguar qué está libre.

**Independent Test**: Se puede probar de forma independiente cargando un conjunto de recursos con estados mixtos (`DISPONIBLE`, `RESERVADO`, `EN_USO`, `BLOQUEO_ACADEMICO`, `EN_MANTENIMIENTO`) y verificando que la consulta para una franja dada devuelve solo los disponibles, sin necesidad de que exista la funcionalidad de reserva.

**Acceptance Scenarios**:

1. **Scenario**: Consulta de franja completamente libre
   - **Given** el recurso "Sala de Estudio 3" no tiene reservas ni clases registradas para el 2026-09-01 entre 10:00 y 12:00
   - **When** el Estudiante consulta los recursos disponibles para esa fecha y franja
   - **Then** el sistema muestra "Sala de Estudio 3" con estado `DISPONIBLE` y su aforo máximo

2. **Scenario**: Ocultamiento por bloqueo académico
   - **Given** el "Laboratorio de Redes" tiene una clase importada del horario semestral el 2026-09-01 de 08:00 a 10:00
   - **When** el Estudiante consulta los recursos disponibles para el 2026-09-01 de 08:00 a 10:00
   - **Then** el "Laboratorio de Redes" no aparece dentro de los recursos seleccionables y se indica que está en `BLOQUEO_ACADEMICO`

3. **Scenario**: Ocultamiento por recurso reservado
   - **Given** el "Auditorio Menor" tiene una reserva estudiantil confirmada el 2026-09-02 de 14:00 a 16:00
   - **When** otro Estudiante consulta ese mismo día y franja
   - **Then** el "Auditorio Menor" se muestra como `RESERVADO` y no es seleccionable

4. **Scenario**: Ocultamiento por recurso en uso
   - **Given** el "Salón 201" tiene una reserva activa cuya franja actual es 2026-09-01 de 14:00 a 16:00
   - **When** el Estudiante consulta los recursos disponibles para el 2026-09-01 de 14:00 a 16:00
   - **Then** el "Salón 201" se muestra como `EN_USO` y no es seleccionable

5. **Scenario**: Sin resultados
   - **Given** todos los recursos del tipo "Laboratorio" están ocupados o bloqueados en la franja consultada
   - **When** el Estudiante ejecuta la consulta filtrando por tipo "Laboratorio"
   - **Then** el sistema devuelve una lista vacía con un mensaje explicativo, muestra los recursos en reparación con estado `EN_MANTENIMIENTO` y sugiere la siguiente franja con disponibilidad

### Edge Cases

- **Solapamiento parcial**: un recurso con bloqueo académico de 08:00 a 10:00 no debe presentarse como disponible para una consulta de 09:30 a 10:30; cualquier intersección no vacía lo excluye.
- **Recurso en mantenimiento**: un recurso en `EN_MANTENIMIENTO` nunca debe ser seleccionable, aunque no tenga reservas ni bloqueos en la franja.
- **Zona horaria y cambio de día**: franjas definidas en el límite del día o que crucen la medianoche. [NEEDS CLARIFICATION: ¿se permiten franjas que crucen la medianoche?]
- **Vista desactualizada**: qué se muestra cuando un recurso pasa a `EN_USO` justo después de renderizarse la lista de resultados; la disponibilidad mostrada es orientativa y se revalida al reservar.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir consultar los recursos filtrando por fecha, franja horaria, tipo de recurso y, para espacios, por aforo mínimo.
- **FR-002**: El sistema DEBE excluir de la selección estudiantil todo recurso cuyo estado en la franja consultada sea `RESERVADO`, `BLOQUEO_ACADEMICO`, `EN_USO` o `EN_MANTENIMIENTO`.
- **FR-003**: El sistema DEBE calcular la disponibilidad como la ausencia de intersección con cualquier bloqueo académico o reserva vigente sobre el mismo recurso.
- **FR-004**: El sistema DEBE indicar, para cada recurso no disponible, el estado que motiva su exclusión.
- **FR-005**: El sistema DEBE aplicar al rol Monitor todas las capacidades de consulta del rol Estudiante.

### Key Entities

- **Recurso**: espacio o equipo reservable. Conforme a la tipificación del Módulo 1, separa dos categorías con atributos propios:
  - **Espacio (Aforo)** —p. ej. Salón, Auditorio, Laboratorio, Sala de estudio—: identificador (ID de salón/auditorio), nombre, aforo máximo, equipamiento fijo (proyector, aire acondicionado, sillas), facultad a la que pertenece y ubicación.
  - **Recurso físico (Mueble/Equipo)** —p. ej. Libro, Microscopio, Kit de dibujo, Videobeam—: identificador (placa de inventario), nombre, tipo, estado físico y ubicación. No tiene capacidad.
- **FranjaHoraria**: intervalo con fecha, hora de inicio y hora de fin; unidad sobre la que se calcula el solapamiento.
- **Reserva**: apartado vigente que hace que un recurso figure como `RESERVADO` en una franja.
- **BloqueoAcadémico**: ocupación de máxima prioridad que hace que un recurso figure como `BLOQUEO_ACADEMICO` en una franja.
- **Mantenimiento**: estado operativo por el que un recurso se encuentra en reparación y figura como `EN_MANTENIMIENTO` en una franja.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La consulta de disponibilidad responde en menos de 2 segundos con 500 usuarios concurrentes.
- **SC-002**: El 100 % de los recursos con bloqueo académico o reserva vigente en la franja consultada quedan excluidos de la lista de recursos seleccionables.
- **SC-003**: Un estudiante localiza un recurso disponible para una fecha y franja dadas en menos de 60 segundos desde el inicio de la sesión.
- **SC-004**: Cero desplazamientos físicos al campus para averiguar disponibilidad reportados en la encuesta de fin de semestre.
