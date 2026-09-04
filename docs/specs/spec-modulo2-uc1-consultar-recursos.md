
# Feature Specification: Consultar recursos

**Created**: 2026-08-24
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Consultar recursos`
**Prioridad global**: P1

## Contexto

Punto de entrada del Módulo 2. Permite al Estudiante (Monitor, que hereda sus capacidades) y dirección de programa ver el catálogo de recursos físicos de la universidad —salones, laboratorios, salas de estudio y equipos— y saber cuáles están realmente disponibles en una fecha y franja horaria.

Es el único caso de uso del módulo que no arranca desde ningún otro: el estudiante puede entrar, mirar qué hay libre y salir sin hacer nada más. Desde aquí puede continuar hacia `Reservar recursos` si decide apartar algo.

Ahora bien, el Módulo 2 no es dueño de nada de lo que muestra. Conforme a [gestionunimag.md](../gestionunimag.md), el Módulo 1 *"actúa como la base de datos central que digitaliza la infraestructura física"*: allí viven tanto el **catálogo** —qué recursos existen, de qué tipo son, su aforo, su equipamiento fijo, su facultad y su ubicación— como el **estado** de cada uno. Esta consulta le pide las dos cosas:

| Qué necesita | De dónde sale |
|---|---|
| El catálogo y los atributos de cada recurso | Directamente del Módulo 1; sin él esta consulta no puede mostrar ni una fila. |
| Si el recurso está libre en la franja pedida | De `Consultar disponibilidad de los recursos`, que a su vez lee el inventario del Módulo 1. |

Lo que el estudiante ve es una foto del momento: la disponibilidad se vuelve a comprobar al confirmar una reserva, porque entre mirar y decidir alguien más pudo haberse adelantado.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Estudiante | Primario | Consulta el catálogo filtrando por fecha, franja y tipo de recurso. |
| Monitor | Primario | Especialización de Estudiante: hereda esta capacidad. |
| Dirección de Programa | Primario | Consulta el catálogo para ver la ocupación real de la infraestructura. |
| Módulo 1 | Secundario | Dueño del inventario: aporta el catálogo de recursos con sus atributos y el estado real de cada uno. |

**Casos de uso relacionados**

- `Reservar recursos` — **continuación opcional de esta consulta** (`<<extend>>`, siendo esta el caso base): el estudiante puede quedarse solo mirando, o seguir y apartar uno de los recursos que encontró libres; ver [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md)
- `Consultar disponibilidad de los recursos` — **paso que ocurre siempre por dentro** (`<<include>>`): responde, recurso por recurso, si está libre en la franja pedida; es con lo que se arma esta lista y no se puede saltar; ver [spec-modulo2-uc8-consultar-disponibilidad-recursos.md](./spec-modulo2-uc8-consultar-disponibilidad-recursos.md)
- `Consultar reportes` — obtiene del Módulo 3 si la persona está sancionada, para poder avisárselo antes de que intente apartar algo; ver [spec-modulo2-uc6-consultar-reportes.md](./spec-modulo2-uc6-consultar-reportes.md)
- `Importar horarios semestrales` — fuente de los estados `BLOQUEO_ACADEMICO` que esta consulta debe respetar; ver [spec-modulo2-uc3-importar-horarios-semestrales.md](./spec-modulo2-uc3-importar-horarios-semestrales.md)

**Estados del recurso (Módulo 1)**: conforme a [gestionunimag.md](../gestionunimag.md), el inventario gestiona cinco estados: `DISPONIBLE`, `RESERVADO`, `BLOQUEO_ACADEMICO`, `EN_USO` y `EN_MANTENIMIENTO`. Esta consulta respeta ese catálogo.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar recursos disponibles (Priority: P1)

Como Estudiante (o Monitor), quiero consultar el catálogo de recursos filtrando por fecha, franja horaria y tipo de recurso, para ver únicamente aquellos que están realmente disponibles y no perder tiempo intentando apartar espacios ocupados o bloqueados por clase.

**Why this priority**: Es la puerta de entrada del módulo: ningún otro caso de uso tiene que ocurrir antes para que esta consulta tenga sentido, y todos los demás parten de aquí o usan la información que aquí se muestra. Por dentro sí se apoya en `Consultar disponibilidad de los recursos` y en el inventario del Módulo 1, pero eso el estudiante no lo ve. Por sí sola ya entrega valor: elimina el recorrido físico por el campus para averiguar qué está libre.

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
- **Zona horaria y horario operativo (restricción nocturna)**: La universidad opera en hora local de Colombia (`America/Bogota`, UTC-5). No se permiten consultas ni reservas dentro del intervalo nocturno de 22:00 (10:00 p. m.) a 06:00 (06:00 a. m.) del día siguiente. Todas las franjas deben iniciar y terminar dentro del horario hábil del mismo día (entre las 06:00 y las 22:00); no se permiten franjas que crucen la medianoche.
- **El Módulo 1 no responde**: si el inventario no está disponible, esta consulta no tiene de dónde sacar el catálogo. No puede inventarse una lista ni mostrar una guardada de antes como si fuera de ahora; debe decir que la información no está disponible en este momento. [NEEDS CLARIFICATION: ver P-14]
- **Catálogo muy grande**: una consulta sin filtros sobre un tipo con cientos de recursos no puede traerlos todos de una vez; se pide por páginas y se le dice a la persona cuántos hay en total, para que sepa que está viendo un trozo.
- **Vista desactualizada**: qué se muestra cuando un recurso pasa a `EN_USO` justo después de renderizarse la lista de resultados; la disponibilidad mostrada es orientativa y se revalida al reservar.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir consultar los recursos filtrando por fecha, franja horaria, tipo de recurso y, para espacios, por aforo mínimo.
- **FR-002**: El sistema DEBE excluir de la selección estudiantil todo recurso cuyo estado en la franja consultada sea `RESERVADO`, `BLOQUEO_ACADEMICO`, `EN_USO` o `EN_MANTENIMIENTO`.
- **FR-003**: El sistema DEBE calcular la disponibilidad como la ausencia de intersección con cualquier bloqueo académico o reserva vigente sobre el mismo recurso.
- **FR-004**: El sistema DEBE indicar, para cada recurso no disponible, el estado que motiva su exclusión.
- **FR-005**: El sistema DEBE aplicar al rol Monitor todas las capacidades de consulta del rol Estudiante.
- **FR-006**: El sistema DEBE obtener del Módulo 1 el catálogo de recursos y sus atributos; el Módulo 2 NO DEBE mantener una copia propia como fuente de verdad.
- **FR-007**: El sistema DEBE resolver la disponibilidad de cada recurso de la lista mediante `Consultar disponibilidad de los recursos`.
- **FR-008**: Si el Módulo 1 no está disponible, el sistema NO DEBE presentar como vigente una lista que no pudo comprobar; DEBE informar que la consulta no se puede resolver en ese momento.
- **FR-009**: El sistema DEBE paginar los resultados cuando la consulta devuelva un conjunto grande de recursos, en coherencia con la paginación que impone el Módulo 1, e indicar cuántos resultados hay en total.
- **FR-010**: El sistema DEBE expresar e interpretar toda fecha y hora en hora local de Colombia (`America/Bogota`, UTC-5), la zona a la que el Módulo 1 normaliza el inventario.
- **FR-011**: El sistema DEBE rechazar las consultas cuya franja caiga fuera de la ventana operativa de 06:00 a 22:00 del mismo día, o que crucen la medianoche.

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

- **SC-001**: La consulta responde en menos de 6 segundos con hasta 100 usuarios concurrentes, y en menos de 12 segundos en pico de 500. Los tiempos se derivan de lo que promete el Módulo 1 para `Consultar disponibilidad del recurso` (5 s y 10 s respectivamente) más el margen de composición de la lista; el Módulo 2 no puede responder más rápido que su fuente de datos.
- **SC-002**: El 100 % de los recursos con bloqueo académico o reserva vigente en la franja consultada quedan excluidos de la lista de recursos seleccionables.
- **SC-003**: Un estudiante localiza un recurso disponible para una fecha y franja dadas en menos de 60 segundos desde el inicio de la sesión.
- **SC-004**: Cero desplazamientos físicos al campus para averiguar disponibilidad reportados en la encuesta de fin de semestre.
