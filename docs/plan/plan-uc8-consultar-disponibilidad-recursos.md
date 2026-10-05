# Implementation Plan: Consultar disponibilidad de los recursos (UC8)

**Date**: 2026-10-02
**Spec**: [spec-modulo2-uc8-consultar-disponibilidad-recursos.md](../specs/spec-modulo2-uc8-consultar-disponibilidad-recursos.md)
**Plan general**: [plan-arquitectura.md](./plan-arquitectura.md) — incluida la [convención de nombres](./plan-arquitectura.md#convención-de-nombres)
**Planes previos**: [UC1](./plan-uc1-consultar-recursos.md) implementó la versión por lote y [UC2](./plan-uc2-reservar-recursos.md) la de un solo recurso con el "hasta cuándo"

## Summary

UC8 es la pregunta que el módulo se hace a sí mismo: **¿está libre este recurso en esta franja?** No tiene actor humano y no se ve por fuera. La hacen UC1 para armar su lista y UC2 antes de confirmar, y es donde vive la única definición de "disponible" de todo el módulo.

Este plan **casi no añade lógica nueva**: UC1 y UC2 ya la escribieron entre los dos, porque ninguno podía funcionar sin ella. Lo que hace es lo que faltaba:

1. **Reunir en un sitio** qué FR quedó resuelto dónde, para que no haya que leer dos planes y adivinar.
2. **Las pruebas transversales** que ningún plan individual podía escribir: que UC1 y UC2 den exactamente la misma respuesta sobre el mismo recurso (SC-003, SC-004), que es la única forma de garantizar que no haya dos definiciones de "disponible" conviviendo.
3. **El borde**, que el spec pide explícito y que estaba repartido entre dos planes sin estar enunciado en ninguno.
4. **Un endpoint propio**, que hoy no existe y que el frontend necesita para revalidar antes de confirmar sin tener que pedir el catálogo entero.
5. **Decidir con qué operación del Módulo 1 se pregunta**, que ningún plan decía: la ficha para un recurso, el lote para muchos, y una sola llamada por petición de reserva.

> Si lo que buscas es el algoritmo, está en [UC1 § Decisiones de diseño](./plan-uc1-consultar-recursos.md#decisiones-de-diseño-de-este-caso-de-uso): la tabla de prioridad y la consulta de ocupaciones. Este plan no lo repite.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 4.1.1. **Ninguna nueva.**
**Storage**: PostgreSQL. UC8 solo **lee**: `reservation`, `loan` y `academic_block`. No escribe nada, nunca (FR-009).
**Testing**: JUnit 5 y AssertJ, Testcontainers con PostgreSQL (los bordes de los rangos y la coherencia entre UC1 y UC2), WireMock (Módulo 1 caído), `@WebMvcTest` (el endpoint nuevo), ArchUnit
**Target Platform**: Servidor Linux con JVM 21
**Performance Goals**: Menos de 5 s para un recurso y una franja (SC-001), de los que el Módulo 1 consume casi todo; el cruce contra nuestra base son milisegundos y no debe añadir tiempo apreciable.
**Constraints**:
- Nunca responde "disponible" por defecto si el Módulo 1 no contestó (FR-007, SC-003).
- Nunca revela quién tiene el recurso (FR-005).
- Nunca modifica nada (FR-009).
- Toda respuesta negativa lleva motivo (FR-004, SC-002).
- No reutiliza respuestas anteriores (FR-006).
**Scale/Scope**: La versión por lote responde por cientos de recursos de una vez. Sin pantalla propia.

## Project Structure

### Source Code (repository root)

```text
src/main/java/edu/unimagdalena/reservasunimag/
├── domain/
│   ├── model/
│   │   ├── resource/
│   │   │   ├── DisplayedStatus.java            # (existe, UC1) la tabla de prioridad
│   │   │   └── AvailabilityAnswer.java         # nuevo: la entidad RespuestaDeDisponibilidad
│   │   └── reservation/
│   │       └── Occupancy.java                  # (existe, UC1)
│   ├── port/
│   │   ├── in/CheckAvailabilityPort.java       # (existe, UC1 por lote + UC2 individual)
│   │   └── out/OccupancyRepositoryPort.java    # (existe, UC1)
│   └── usecase/checkavailability/
│       └── CheckAvailabilityUseCase.java       # (existe) sin cambios de lógica
│
└── infrastructure/adapter/in/web/resource/
    ├── AvailabilityController.java             # nuevo: GET /api/resources/{id}/availability
    └── AvailabilityResponse.java               # nuevo

src/test/java/edu/unimagdalena/reservasunimag/
├── domain/usecase/checkavailability/
│   ├── CheckAvailabilityUseCaseTest.java       # (existe, UC1) + los escenarios que faltaban
│   └── AvailabilityBoundaryTest.java           # nuevo: los bordes de los rangos
└── infrastructure/adapter/
    ├── in/web/resource/AvailabilityControllerTest.java
    └── out/persistence/AvailabilityConsistencyIT.java   # nuevo: UC1 y UC2 coinciden
```

**Structure Decision**: solo se añaden un controlador y pruebas. `AvailabilityAnswer` existía implícito dentro de `ResourceWithStatus` de UC1; se extrae porque el endpoint nuevo responde por un recurso y no por una lista, y porque es la entidad que el spec nombra.

### Decisiones de diseño de este caso de uso

**Dónde quedó cada requisito.** Es el contenido principal de este plan:

| FR | Qué pide | Dónde se implementó |
|---|---|---|
| FR-001 | Responder por un recurso y una franja | UC2 T011 (versión individual) |
| FR-002 | No disponible si está `RESERVADO`, `BLOQUEO_ACADEMICO`, `EN_USO` o `EN_MANTENIMIENTO` | UC1 T028, con la tabla de prioridad; el `EN_MANTENIMIENTO` sale de la ficha del Módulo 1 (ver la decisión de abajo) |
| FR-003 | Cualquier cruce, aunque sea un minuto, contra reservas, bloqueos y préstamos abiertos | UC1 T032, la consulta de ocupaciones |
| FR-004 | Indicar el motivo | UC1 T024, el campo `reason` derivado del estado |
| FR-005 | No revelar al titular | UC1, el texto fijo del motivo; UC2 T020 lo prueba |
| FR-006 | Estado del momento, sin reutilizar respuestas | UC1 T037, `Cache-Control: no-store` |
| FR-007 | Si el Módulo 1 no responde, no dar por disponible | UC1 T033, `ExternalServiceUnavailableException` → `503`, igual en las dos operaciones |
| FR-008 | Responder por varios de una vez | UC1 T027, la versión por lote |
| FR-009 | No cambiar nada | UC1 T036, `@Transactional(readOnly = true)` |
| FR-010 | Todo en `America/Bogota` | UC1 T009, `TimeSlot` |
| FR-011 | Recorrer todas las páginas del Módulo 1 | UC1 T033 |
| FR-012 | Decir **hasta cuándo** está comprometido | UC2 T011 |

No queda ningún FR sin implementar. Lo que queda son garantías sin probar y una pieza de interfaz que falta.

**Con qué operación del Módulo 1 se pregunta, y por qué solo una vez por petición.** El plan hablaba de "preguntarle al Módulo 1" sin decir por dónde, y eso dejaba dos caminos abiertos para el mismo dato. Queda fijado así:

| Quién pregunta | Operación del Módulo 1 | Por qué |
|---|---|---|
| La lista de UC1 | **Ninguna aparte**: el catálogo ya trae el `operationalStatus` de cada recurso | Pedirlo otra vez duplicaría la latencia del presupuesto de SC-001 |
| La consulta individual, incluido el endpoint de §1 | La **ficha**, `GET /api/v1/resources/{id}` ([UC2 §4](./plan-uc2-reservar-recursos.md#4-módulo-1--ficha-de-un-recurso)) | Es una sola llamada que trae el estado, el nombre y la categoría, que es todo lo que la respuesta necesita. Un lote de un elemento daría el estado y obligaría a otra llamada para el nombre |
| La carga de horarios de UC3 y la tarea de inicio de uso de UC7 | El **lote**, `POST /api/v1/resources/operational-status` ([UC1 §2.2](./plan-uc1-consultar-recursos.md#22-estado-operativo-por-lote--post-apiv1resourcesoperational-status)) | Son los dos que preguntan por muchos recursos de una vez y no necesitan la ficha completa |

Y la consecuencia que importa: **dentro de una misma petición de reserva, la ficha se pide una sola vez.** UC2 ya la pide en el paso 0 de su tabla de decisiones —de ahí saca la categoría y el plazo de préstamo—, así que el paso 3 recibe ese estado ya resuelto y **no vuelve a salir a la red**. La operación individual del puerto acepta el estado operativo cuando quien llama ya lo tiene, y lo pide ella misma cuando no. No es solo ahorrar una llamada: si se preguntara dos veces en la misma petición, el estado podría cambiar entre una y otra y la respuesta quedaría incoherente consigo misma —un `RES-004` por mantenimiento cuyo `detectedStatus` dijera `DISPONIBLE`—.

El endpoint público de §1 no viene de UC2, así que ahí sí pide la ficha él mismo.

**El borde de los rangos, enunciado de una vez.** El spec lo pide explícito —"el criterio del borde debe ser el mismo siempre"— y hasta ahora vivía implícito en el `[inicio, fin)` del plan general. Dicho sin rodeos:

| Caso | Se cruzan | Por qué |
|---|---|---|
| Clase 08:00–10:00 vs consulta 10:00–12:00 | **No** | El fin es exclusivo: a las 10:00 la clase ya terminó (edge case **Franjas que se tocan**) |
| Clase 08:00–10:00 vs consulta 09:30–10:30 | **Sí** | Media hora de intersección basta (escenario 6, FR-003) |
| Clase 08:00–10:00 vs consulta 09:59–10:30 | **Sí** | Un minuto basta |
| Préstamo abierto desde el 01 vs consulta del 03 | **Sí** | Un préstamo entregado y sin devolver ocupa sin fin (escenario 5, UC2 FR-015) |

En SQL es el operador `&&` sobre `tstzrange(..., '[)')`, que es exactamente esta semántica. No hay comparaciones de horas escritas a mano en ninguna parte, y eso es deliberado: cada `<` o `<=` suelto sería una oportunidad de que el borde quede distinto en dos sitios.

**La franja fuera de la ventana operativa es un `400`, no un "no disponible".** El último edge case del spec dice que una consulta sobre una franja nocturna o que cruza la medianoche "se reporta como no disponible". Este plan **no** lo implementa así: `TimeSlot` la rechaza antes, con `400` y el código del motivo. La razón es que no es una afirmación sobre el recurso —el recurso puede estar perfectamente libre— sino sobre la pregunta, que está mal formulada. Responder "no disponible" invitaría a que el frontend mostrara un salón como ocupado a las 23:00, cuando lo que pasa es que a esa hora no se reserva. Es la misma decisión que ya tomaron UC1 y UC2 y aquí se hace explícita. (Es una decisión de este plan; conviene corregir la redacción de ese edge case.)

**Recurso que no existe: `404`, no "ocupado"** (edge case). Lo dice el spec y sale del `notFound` de la operación por lote del Módulo 1, que UC1 §2.2 ya definió. La versión por lote **omite** de la lista los que no existen y los registra; la individual responde `404`.

**Por qué hace falta un endpoint propio.** Hoy el frontend no puede preguntar por un solo recurso: tiene que pedir `GET /api/resources` con una franja y buscar el suyo en la lista. Eso es caro —recorre el catálogo entero del Módulo 1— para responder una pregunta de un recurso. El endpoint nuevo sirve dos casos concretos:

- La pantalla de confirmación de UC2, que quiere revalidar justo antes de que la persona pulse el botón, para no llevarla a un `RES-004` evitable.
- Volver a mirar un recurso del que la lista ya dio su estado hace un par de minutos.

No sustituye la revalidación de UC2 dentro de la transacción: esa sigue siendo la que decide (FR-006, edge case **La respuesta envejece enseguida**). Este endpoint es una cortesía para la interfaz, y su respuesta lo dice.

**Lo que este plan no hace: una caché.** Sería la optimización obvia —el mismo recurso se consulta muchas veces en una sesión— y está prohibida por FR-006: "sin reutilizar respuestas anteriores". La prueba de T008 lo fija, para que nadie añada un `@Cacheable` de buena fe.

## Contratos

Se aplican las **convenciones comunes** de [UC1 § Contratos](./plan-uc1-consultar-recursos.md#contratos).

---

### 1. `GET /api/resources/{resourceId}/availability`

El endpoint nuevo. Cualquier rol autenticado.

```http
GET /api/resources/ESP-0107/availability?date=2026-09-01&start=10:00&end=12:00
Cookie: sesion=<jwt>
```

**`200 OK`** — libre (escenario 1)

```json
{
  "resourceId": "ESP-0107",
  "name": "Sala de Estudio 3",
  "category": "ESPACIO",
  "timeSlot": { "date": "2026-09-01", "start": "10:00", "end": "12:00" },
  "available": true,
  "status": "DISPONIBLE",
  "answeredAt": "2026-09-01T09:14:22-05:00"
}
```

**`200 OK`** — ocupado por una clase (escenario 2)

```json
{
  "resourceId": "ESP-0412",
  "name": "Salón 201",
  "category": "ESPACIO",
  "timeSlot": { "date": "2026-09-01", "start": "08:00", "end": "10:00" },
  "available": false,
  "status": "BLOQUEO_ACADEMICO",
  "reason": "El recurso está reservado para actividad docente en la franja consultada.",
  "occupiedUntil": "2026-09-01T10:00:00-05:00",
  "answeredAt": "2026-09-01T09:14:22-05:00"
}
```

**`200 OK`** — activo prestado (escenario 5, FR-012)

```json
{
  "resourceId": "ACT-004512",
  "name": "Libro de Cálculo I",
  "category": "ACTIVO",
  "timeSlot": { "date": "2026-09-03", "start": "10:00", "end": "12:00" },
  "available": false,
  "status": "EN_USO",
  "reason": "El recurso está en uso en la franja consultada.",
  "occupiedUntil": "2026-09-10T22:00:00-05:00",
  "answeredAt": "2026-09-03T09:14:22-05:00"
}
```

| Campo | Nota |
|---|---|
| `available` | El booleano que responde la pregunta. Es `true` solo si `status` es `DISPONIBLE`. |
| `status` | Uno de los cinco de `DisplayedStatus`, con la prioridad de UC1. |
| `reason` | Solo cuando `available` es `false` (FR-004, SC-002). El mismo texto fijo de UC1: **nunca** nombra al titular (FR-005). |
| `occupiedUntil` | **Lo que pide FR-012**: el fin de la franja que lo ocupa si es un espacio, y el vencimiento del préstamo si es un activo. Se omite en `EN_MANTENIMIENTO`, porque el Módulo 1 no nos dice hasta cuándo (P-20 punto 3), y en `DISPONIBLE`. |
| `answeredAt` | FR-006: la respuesta vale para ese instante y nada más. |

Lleva `Cache-Control: no-store`, como la lista de UC1.

**Decisión: `occupiedUntil` de un préstamo abierto y vencido se omite.** Un préstamo entregado y sin devolver ocupa *sin fin* (UC2 FR-015), así que no hay una fecha honesta que poner: el vencimiento ya pasó y el recurso sigue fuera. En ese caso el campo se omite y el `reason` lo explica —"está en uso y su plazo ya venció"—. Poner el vencimiento pasado invitaría a que alguien reservara justo después.

**Errores**

| Código | Cuándo |
|---|---|
| `400` | Franja fuera de 06:00–22:00, que cruza la medianoche o con `end <= start`, con el `code` de `TimeSlot` (ver la decisión de arriba) |
| `401` | Sin sesión |
| `404` | El recurso no existe en el Módulo 1 (edge case **Recurso que no existe**) |
| `503` | El Módulo 1 no respondió: **no** se responde `available: true` (FR-007, SC-003) |

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/inventory-unavailable",
  "title": "Inventario no disponible",
  "status": 503,
  "detail": "No se pudo comprobar la disponibilidad en este momento. No se puede confirmar que el recurso esté libre.",
  "instance": "/api/resources/ESP-0107/availability",
  "module": "MODULO_1"
}
```

El `detail` dice explícitamente que **no se puede confirmar que esté libre**, en vez de un "intenta más tarde" genérico: es lo que el edge case **El inventario no responde** pide que nadie malinterprete.

---

### 2. La forma interna: `AvailabilityAnswer`

El puerto de entrada tiene dos operaciones, y las dos devuelven lo mismo por recurso. Es lo que el spec llama `RespuestaDeDisponibilidad`:

```text
AvailabilityAnswer
├── resourceId        identificador del Módulo 1
├── category          ESPACIO | ACTIVO
├── timeSlot          la franja preguntada
├── available         boolean
├── status            DisplayedStatus
├── reason            texto fijo, solo si no está disponible
├── occupiedUntil     instante, opcional (FR-012)
└── answeredAt        instante de la respuesta (FR-006)
```

La versión por lote devuelve una por `resourceId` y **omite** los que el Módulo 1 reportó en `notFound`. La individual es la misma estructura para un recurso. Que las dos compartan tipo es lo que hace posible la prueba de coherencia de T010: si divergieran, UC1 y UC2 podrían dar respuestas distintas sobre el mismo recurso y la misma franja.

---

### 3. Fixtures compartidos

```text
src/test/resources/contratos/
├── api-availability-free.json
├── api-availability-academic-block.json
├── api-availability-asset-on-loan.json
└── api-availability-503.json
```

---

## Phase 1: Setup

**Purpose**: Nada. UC8 no añade dependencias, parámetros ni migraciones: su esquema y su configuración salen de UC1 y UC2.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T001 Extraer `AvailabilityAnswer` en `domain/model/resource/` desde lo que hoy devuelve `CheckAvailabilityUseCase`, sin cambiar la lógica, y hacer que las dos operaciones del puerto lo usen
- [ ] T002 [P] Revisar que `CheckAvailabilityUseCase` sigue sin escribir nada: `@Transactional(readOnly = true)` en su bean y ninguna llamada a un puerto de escritura (FR-009)
- [ ] T002b Dejar que la operación individual del puerto reciba el estado operativo ya conocido y lo pida por la **ficha** solo si no lo trae, y enganchar el paso 3 de UC2 al estado que su paso 0 ya obtuvo (la decisión de la llamada única)

**Checkpoint**: la respuesta tiene una forma única

---

## Phase 3: User Story 1 - Responder si un recurso está libre (Priority: P1)

**Goal**: La pregunta tiene una sola respuesta en todo el módulo, con su motivo y su "hasta cuándo", sin revelar al titular, sin inventar disponibilidad cuando el Módulo 1 calla y sin reutilizar respuestas viejas. Y el frontend puede hacerla por un recurso.

**Independent Test**: Con el perfil local, consultar un recurso libre, uno con clase, uno reservado por otra persona, uno en mantenimiento y un activo prestado, y comprobar los cinco estados con su motivo; tirar el adaptador del Módulo 1 y comprobar que responde `503` y no `available: true`.

### Tests for User Story 1

- [ ] T003 [P] [US1] Completar `CheckAvailabilityUseCaseTest.java` con los seis escenarios del spec, incluido el 3 —reservado por otra persona, comprobando que **ninguna** parte de la respuesta nombra al titular (FR-005)— y el 5, con su `occupiedUntil` (FR-012)
- [ ] T004 [P] [US1] Prueba `AvailabilityBoundaryTest.java` con la tabla de bordes: 08:00–10:00 contra 10:00–12:00 **no** se cruzan; contra 09:30–10:30 y contra 09:59–10:30 **sí**; un préstamo abierto cruza cualquier franja posterior a su inicio; y el criterio es el mismo en la versión individual y en la de lote
- [ ] T005 [P] [US1] Prueba de FR-004 y SC-002: **ninguna** respuesta negativa sale sin `reason`, recorriendo los cuatro estados que bloquean
- [ ] T006 [P] [US1] Prueba de FR-007 y SC-003: con el Módulo 1 caído, la versión individual responde `503` y la de lote lanza la excepción; en ningún caso aparece un `available: true`. Y un recurso en `notFound` se omite del lote en vez de darse por libre
- [ ] T007 [P] [US1] Prueba de FR-012 y su borde: un espacio ocupado devuelve el fin de la franja que lo ocupa; un activo prestado, el vencimiento; un préstamo **vencido y sin devolver** omite el campo y lo explica en el `reason`; y un `EN_MANTENIMIENTO` lo omite porque el Módulo 1 no da fecha
- [ ] T008 [P] [US1] Prueba de FR-006: dos consultas seguidas sobre el mismo recurso, con una reserva creada entre ambas, devuelven respuestas **distintas**; y la respuesta HTTP lleva `Cache-Control: no-store`. Es la prueba que impide añadir una caché
- [ ] T009 [P] [US1] Prueba `AvailabilityControllerTest.java` contra los *fixtures*: los tres `200`, el `400` de la franja nocturna con su `code`, el `401`, el `404` del recurso inexistente y el `503` con su `detail`
- [ ] T010 [P] [US1] Prueba `AvailabilityConsistencyIT.java` con Testcontainers, la prueba transversal de SC-003 y SC-004: para un conjunto de recursos con los cinco estados, **la respuesta de la lista de UC1 y la del endpoint individual coinciden recurso por recurso**; y un recurso que esta consulta reportó ocupado no puede reservarse con éxito en UC2
- [ ] T010b [P] [US1] Prueba de la llamada única con WireMock: una reserva confirmada pide la ficha del recurso **una sola vez** —el paso 3 no genera una segunda llamada—, y el endpoint de §1, que no viene de UC2, sí la pide él mismo

### Implementation for User Story 1

- [ ] T011 [US1] Implementar `AvailabilityController` y `AvailabilityResponse` según [Contratos §1](#1-get-apiresourcesresourceidavailability), con `Cache-Control: no-store` y documentado con OpenAPI (depende de T001)
- [ ] T012 [US1] Añadir el campo `occupiedUntil` a la respuesta de la lista de UC1, que lo tenía **reservado sin enviar** desde su plan, ahora que UC2 ya lo calcula
- [ ] T013 [P] [US1] Frontend: usar el endpoint en la pantalla de confirmación de UC2 para revalidar antes de que la persona pulse el botón, dejando claro que la decisión final la toma el backend

**Checkpoint**: UC8 queda cerrado y con una sola definición de "disponible" probada

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T014 [P] Verificar SC-001 midiendo la consulta individual con el Módulo 1 simulado a su latencia prometida, y comprobar que el cruce contra nuestra base no añade tiempo apreciable
- [ ] T015 [P] Registrar en logs cada consulta con su resultado y su duración, sin el titular de ninguna reserva
- [ ] T016 Llevar a `pendientes-clarificacion.md` la redacción del último edge case del spec, que pide responder "no disponible" a una franja fuera de la ventana cuando lo correcto es un `400`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 2)**: depende de que UC1 y UC2 estén hechos; sin ellos no hay nada que consolidar
- **User Story 1 (Phase 3)**: depende de Foundational
- **Polish (Phase 4)**: depende de la Phase 3

### Dependencias con otros casos de uso

- **UC1 `Consultar recursos`**: implementó la versión por lote, la tabla de prioridad y la consulta de ocupaciones. Es donde vive el algoritmo; este plan solo le añade el `occupiedUntil` que tenía reservado.
- **UC2 `Reservar recursos`**: implementó la versión individual y el `occupiedUntil`, y es quien revalida dentro de la transacción. Lo de este plan **no** sustituye esa revalidación.
- **UC3 `Importar horarios semestrales`**: usa la misma consulta de cruces para clasificar sus filas, así que el borde de T004 también lo gobierna.
- **UC7 `Actualizar estado de los recursos`**: la tabla de prioridad de UC1 es, de hecho, su FR-003 y su FR-006. Los dos planes se apoyan en lo mismo visto desde lados distintos: UC7 escribe las ocupaciones, UC8 las lee.
- **Módulo 1**: aporta el catálogo y el estado operativo. Sin él esta consulta no responde (FR-007).

### Within User Story 1

- `AvailabilityAnswer` (T001) → controlador (T011) → frontend (T013)
- Las pruebas (T003 a T010) solo dependen de T001

### Parallel Opportunities

- En User Story 1: todas las pruebas (T003 a T010)
- En Polish: T014 y T015

## Notes

- La numeración `T0XX` es propia de este plan
- [P] tasks = different files, no dependencies
- Verify tests pass
- Commit after each task or logical group
- **Este plan es de cierre, no de construcción.** Los doce FR ya estaban implementados por UC1 y UC2; lo que aporta es la trazabilidad, las pruebas transversales que ningún plan individual podía escribir, el borde enunciado en un sitio y el endpoint que faltaba
- **Decisiones de los contratos que el spec no fija**: una franja fuera de la ventana operativa es `400` y no "no disponible", porque el problema está en la pregunta y no en el recurso; `occupiedUntil` se omite en un préstamo vencido y sin devolver, porque no hay fecha honesta; y el endpoint individual es una cortesía para la interfaz que **no** sustituye la revalidación de UC2 dentro de la transacción
- **Decisión explícita de no hacer**: ninguna caché, por FR-006. La prueba de T008 lo fija
