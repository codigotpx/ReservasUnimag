# Implementation Plan: Consultar recursos (UC1)

**Date**: 2026-09-21
**Spec**: [spec-modulo2-uc1-consultar-recursos.md](../specs/spec-modulo2-uc1-consultar-recursos.md)
**Plan general**: [plan-arquitectura.md](./plan-arquitectura.md)

## Summary

UC1 es la puerta de entrada del módulo. Estudiante, Monitor y Dirección de Programa consultan el catálogo de recursos filtrando por fecha, franja horaria, tipo y, para espacios, aforo mínimo. Ven **todos** los recursos que cumplen los filtros, cada uno con su estado en esa franja, y solo pueden seleccionar los que están `DISPONIBLE` (FR-002, FR-012).

**Enfoque técnico:**

1. El caso de uso `SearchResourcesUseCase` pide el catálogo filtrado al Módulo 1 a través de `InventoryPort`, recorriendo todas sus páginas (UC8 FR-011), y lo ordena por nombre e identificador (FR-014).
2. Calcula el estado de **todo** el conjunto filtrado con `CheckAvailabilityPort` (el `<<include>>` a UC8). El estado sale de cruzar el estado operativo del Módulo 1 con las ocupaciones que guarda el Módulo 2 (reservas, préstamos y bloqueos), en una sola consulta a PostgreSQL con rangos `tstzrange`.
3. Pagina en memoria, de 20 en 20 (FR-009). Como el estado se calcula para todo el conjunto, se sabe si **ninguno** es seleccionable (escenario 7) y se puede sugerir la siguiente franja libre.
4. Si quien consulta es Estudiante o Monitor, consulta sus sanciones con `CheckSanctionsPort` (el `<<include>>` a UC6) y agrega un aviso a la respuesta (FR-016).
5. Si el Módulo 1 no responde, la consulta falla con un error explícito y nunca muestra una lista vieja (FR-008, P-14).

Como UC1 es el primer caso de uso que se implementa, este plan también monta la base compartida del proyecto: dependencias, esquema inicial, seguridad, manejo de errores, reloj y el esqueleto del frontend. De UC8 y UC6 implementa solo la parte que UC1 necesita; sus propios planes la completan.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript con Next.js y React (frontend)
**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Data JPA, Security, OAuth2 Resource Server para validar el JWT propio, Validation, RestClient), Flyway, springdoc-openapi; Next.js (App Router)
**Storage**: PostgreSQL con `btree_gist`. UC1 solo **lee** del Módulo 2 las tablas `reservation`, `loan` y `academic_block`; el catálogo no se guarda (FR-006).
**Testing**: JUnit 5 y AssertJ (dominio y casos de uso con puertos falsos), `@WebMvcTest` (controlador), Testcontainers con PostgreSQL (persistencia), WireMock (clientes de los Módulos 1 y 3), ArchUnit (regla de dependencias)
**Target Platform**: Servidor Linux con JVM 21; navegador web para el frontend
**Project Type**: Web: backend en la raíz del repositorio y frontend en `frontend/`
**Performance Goals**: Una página de 20 recursos en menos de 6 s con 100 usuarios concurrentes, y en menos de 12 s con 500 (SC-001). Del presupuesto, el Módulo 1 consume hasta 5 s y 10 s respectivamente.
**Constraints**:
- Timeout del cliente del Módulo 1 alineado con SC-001, sin reintentos dentro de la misma consulta, porque no hay presupuesto de tiempo para reintentar.
- Nunca se muestra una lista que no se pudo comprobar (FR-008).
- Zona horaria `America/Bogota` y ventana de 06:00 a 22:00 del mismo día (FR-010, FR-011).
- No se revela quién tiene reservado un recurso (UC8 FR-005).
**Scale/Scope**: Hasta 500 usuarios concurrentes en pico (SC-001); catálogo de cientos de recursos por tipo (edge case **Catálogo muy grande**). Una pantalla de consulta en el frontend.

## Project Structure

### Documentation (this feature)

```text
docs/
├── specs/
│   ├── spec-modulo2-uc1-consultar-recursos.md                 # spec de este plan
│   ├── spec-modulo2-uc8-consultar-disponibilidad-recursos.md  # <<include>>: parte implementada aquí
│   └── spec-modulo2-uc6-consultar-sanciones.md                # <<include>>: parte implementada aquí
└── plan/
    ├── plan-arquitectura.md            # plan general: capas, carpetas y base de datos
    └── plan-uc1-consultar-recursos.md  # este archivo
```

### Source Code (repository root)

Solo los archivos que crea o toca este plan. La organización completa está en [plan-arquitectura.md](./plan-arquitectura.md#organización-de-carpetas).

```text
build.gradle                                        # dependencias nuevas (T002)

src/main/java/edu/unimagdalena/reservasunimag/
├── domain/
│   ├── model/
│   │   ├── common/
│   │   │   ├── Page.java                         # página de resultados con total y número
│   │   │   └── PageRequest.java
│   │   ├── resource/
│   │   │   ├── ResourceCategory.java               # ESPACIO, ACTIVO
│   │   │   ├── Resource.java                        # ficha del catálogo que llega del Módulo 1
│   │   │   ├── OperationalStatus.java                # DISPONIBLE, EN_USO, EN_MANTENIMIENTO (Módulo 1)
│   │   │   ├── DisplayedStatus.java                  # los cinco estados con su prioridad
│   │   │   ├── ResourceFilter.java                 # tipo, categoría, aforo mínimo
│   │   │   └── ResourceWithStatus.java               # recurso + estado + seleccionable + motivo
│   │   ├── reservation/
│   │   │   ├── TimeSlot.java                  # valida ventana, mismo día y zona horaria
│   │   │   ├── Occupancy.java                      # lo que ocupa un recurso en el tiempo
│   │   │   └── OccupancyType.java                  # SPACE_RESERVATION, LOAN_HELD,
│   │   │                                           # LOAN_PICKED_UP, BLOQUEO_ACADEMICO
│   │   ├── compliance/
│   │   │   ├── ComplianceReport.java
│   │   │   └── Sanction.java
│   │   ├── user/
│   │   │   ├── Role.java
│   │   │   └── CurrentUser.java                  # id y rol de quien consulta
│   │   └── error/
│   │       ├── InvalidTimeSlotException.java
│   │       └── ExternalServiceUnavailableException.java
│   ├── port/
│   │   ├── in/
│   │   │   ├── SearchResourcesPort.java
│   │   │   ├── CheckAvailabilityPort.java    # UC8 (versión por lote)
│   │   │   └── CheckSanctionsPort.java         # UC6 (lectura)
│   │   └── out/
│   │       ├── InventoryPort.java                 # Módulo 1
│   │       ├── OccupancyRepositoryPort.java        # reservas, préstamos y bloqueos del Módulo 2
│   │       ├── CompliancePort.java               # Módulo 3
│   │       └── Clock.java
│   └── usecase/
│       ├── searchresources/
│       │   ├── SearchResourcesUseCase.java
│       │   ├── SearchResourcesQuery.java               # comando: filtros + franja + página + usuario
│       │   ├── SearchResourcesResult.java      # página + aviso de sanción + sugerencia
│       │   └── NextFreeSlotFinder.java        # escenario 7
│       ├── checkavailability/
│       │   └── CheckAvailabilityUseCase.java
│       └── checksanctions/
│           └── CheckSanctionsUseCase.java
│
└── infrastructure/
    ├── adapter/
    │   ├── in/web/
    │   │   ├── resource/
    │   │   │   ├── ResourceController.java          # GET /api/resources
    │   │   │   ├── SearchResourcesParams.java
    │   │   │   └── SearchResourcesResponse.java
    │   │   └── error/
    │   │       └── GlobalErrorHandler.java     # excepciones → ProblemDetail
    │   └── out/
    │       ├── persistence/
    │       │   ├── entity/                         # ReservationJpa, LoanJpa, AcademicBlockJpa, UserJpa
    │       │   ├── repository/OccupancyJpaRepository.java
    │       │   └── OccupancyPersistenceAdapter.java
    │       ├── modulo1/
    │       │   ├── InventoryRestAdapter.java
    │       │   ├── InventoryFakeAdapter.java      # perfil local, mientras el Módulo 1 no esté
    │       │   └── dto/
    │       └── modulo3/
    │           ├── ComplianceRestAdapter.java
    │           ├── ComplianceFakeAdapter.java    # perfil local
    │           └── dto/
    └── config/
        ├── UseCasesConfig.java
        ├── SecurityConfig.java
        ├── ClockConfig.java
        ├── HttpClientsConfig.java
        └── ReservationProperties.java                # ventana, tamaño de página, paso de sugerencia,
                                                    # tamaño y tope de páginas del Módulo 1, tipos del filtro

src/main/resources/
├── application.properties
├── application-local.properties                    # perfil local: adaptadores falsos
└── db/migration/
    ├── V1__initial_schema.sql
    └── V2__local_data.sql                         # solo perfil local (usuarios de prueba)

src/test/java/edu/unimagdalena/reservasunimag/
├── ArchitectureTest.java
├── domain/model/reservation/FranjaHorariaTest.java
├── domain/model/resource/EstadoVisibleTest.java
├── domain/usecase/searchresources/ConsultarRecursosUseCaseTest.java
├── domain/usecase/searchresources/BuscadorSiguienteFranjaTest.java
├── domain/usecase/checkavailability/ConsultarDisponibilidadUseCaseTest.java
└── infrastructure/adapter/
    ├── in/web/resource/RecursoControllerTest.java
    ├── out/persistence/OcupacionPersistenceAdapterIT.java
    ├── out/module1/InventarioRestAdapterTest.java
    └── out/module3/CumplimientoRestAdapterTest.java

src/test/resources/contratos/                       # los JSON de la sección Contratos (T022)

frontend/
├── package.json, next.config.ts, tsconfig.json
└── src/
    ├── app/(app)/resources/page.tsx                 # pantalla de consulta
    ├── features/resources/
    │   ├── ResourceFilters.tsx
    │   ├── ResourceList.tsx
    │   ├── Pagination.tsx
    │   ├── SanctionNotice.tsx
    │   ├── api.ts
    │   └── types.ts
    └── lib/api/client.ts                          # fetch hacia /api con la cookie de sesión
```

**Structure Decision**: Se usa la estructura web del plan general: backend hexagonal en la raíz (`domain` e `infrastructure`) y frontend Next.js en `frontend/`. Hay una diferencia con el plan general: la pantalla de recursos va en el grupo `(app)` y no en `(student)`, porque Dirección de Programa también la usa. Los adaptadores falsos del Módulo 1 y del Módulo 3 existen para poder desarrollar y hacer la demo mientras esos módulos no publiquen su API (P-16).

### Decisiones de diseño de este caso de uso

**Cómo se calcula el estado de cada recurso.** Lo hace `CheckAvailabilityUseCase`, en este orden de prioridad (spec, sección **Estados que se muestran**):

| Prioridad | Estado | Condición                                                                                                                                                                             |
|---|---|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | `EN_MANTENIMIENTO` | El Módulo 1 lo reporta así. Se aplica a cualquier franja consultada (el mantenimiento no reporta fecha, ellos hacen el cambio de mantenimiento a disponible).                         |
| 2 | `BLOQUEO_ACADEMICO` | Hay un bloqueo académico `CONFIRMADA` que se cruza con la franja.                                                                                                                     |
| 3 | `EN_USO` | Hay un préstamo **entregado** y sin devolver que se cruza con la franja (escenario 6), o el Módulo 1 reporta `EN_USO` y la franja consultada incluye el momento actual (escenario 4). |
| 3 | `RESERVADO` | Hay una reserva de espacio o un préstamo apartado sin recoger que se cruza con la franja.                                                                                             |
| 4 | `DISPONIBLE` | Ninguna de las anteriores.                                                                                                                                                            |

**Préstamo vencido y no devuelto.** Sigue ocupando el recurso aunque haya pasado su vencimiento (UC2 FR-015). Por eso la consulta de ocupaciones no se limita al rango `occupancy`: un préstamo con `picked_up_at` y sin `returned_at` ocupa desde su inicio y sin fin.

```sql
SELECT r.resource_id, r.origin, r.resource_category, r.starts_at, r.ends_at,
       p.picked_up_at, p.returned_at
FROM reservation r
LEFT JOIN loan p ON p.reservation_id = r.id
WHERE r.status = 'CONFIRMADA'
  AND r.resource_id = ANY(:resourceIds)
  AND ( r.occupancy && tstzrange(:starts_at, :ends_at, '[)')
     OR (p.picked_up_at IS NOT NULL AND p.returned_at IS NULL AND r.starts_at < :ends_at) );
```

**Paginación.** El Módulo 2 decide el tamaño de página, no el Módulo 1 (FR-009). Cada petición de página repite la consulta con los mismos filtros y el número de página. No se congela nada entre páginas (edge case **Cambio de estado entre páginas**). La respuesta incluye `total`, `page`, `totalPages` y `anySelectable`, con la forma exacta de [Contratos §1](#1-get-apiresources--frontend--módulo-2).

**Sugerencia de la siguiente franja (escenario 7).** Cuando ningún recurso del conjunto filtrado es seleccionable, se prueban franjas de la misma duración que la consultada, que empiezan desde el fin de esa franja en pasos de 30 minutos y llegan hasta las 22:00 del mismo día. Se usa la foto del catálogo ya obtenida y una sola consulta de ocupaciones que cubre el resto del día. Si no hay ninguna, la respuesta lo dice. El paso de 30 minutos y el límite al mismo día son una decisión de este plan, porque el spec no los fija; son parametrizables.

**Sanciones durante la consulta (FR-016).** Solo para los roles `ESTUDIANTE` y `MONITOR`: Dirección de Programa no reserva. Si hay sanción vigente, la respuesta trae un aviso con su motivo y su fecha de fin (UC6 FR-004), pero la lista se muestra igual. Si el Módulo 3 no responde, la consulta **no falla**: trae un aviso de que no se pudo comprobar la situación de la persona (UC6 FR-006), porque la comprobación que decide es la de `Reservar recursos` al confirmar. (NEEDS CLARIFICATION: P-10 fija la política al reservar; aquí se asume que la consulta no se bloquea.)

**Contrato con el Módulo 1.** `InventoryPort` expone dos operaciones pensadas desde nuestro lado: `buscarCatalogo(ResourceFilter)`, que devuelve la ficha de cada recurso con su estado operativo, y `operationalStatus(resourceIds)`. Si el Módulo 1 no filtra por aforo, el filtro se aplica en el caso de uso después de traer el catálogo. El JSON de las dos operaciones, su mapeo hacia el dominio y qué se hace con cada error están en [Contratos §2](#2-módulo-1--inventoryport).

## Contratos

Todo lo que entra y sale de UC1 queda definido aquí: la API que consume el frontend, lo que le pedimos al Módulo 1 y al Módulo 3, y el login mínimo que necesita T013. Los ejemplos de esta sección son el contrato: las pruebas de T022 y T023 se escriben contra ellos y los mismos JSON viven como *fixtures* en `src/test/resources/contratos/`.

**Convenciones comunes**

| Regla | Detalle |
|---|---|
| Formato | JSON con `Content-Type: application/json`, UTF-8. |
| Nombres | Campos en `camelCase`; enumeraciones en `MAYUSCULA_CON_GUION_BAJO`. |
| Nulos | **Nunca se envía `null`**: un campo que no aplica se omite. Un espacio no trae `condition` y un activo no trae `maxCapacity`. |
| Fechas | `date` como `yyyy-MM-dd` y las horas de una franja como `HH:mm`, siempre en `America/Bogota` (FR-010). Un instante completo va en ISO-8601 **con desplazamiento**: `2026-09-01T09:14:22-05:00`. Nunca se envía un instante sin zona. |
| Intervalos | Toda franja es `[inicio, fin)`: el fin no se incluye, así que 10:00–12:00 y 12:00–14:00 no se cruzan. |
| Identificadores | El `id` de un recurso es el identificador del Módulo 1 y viaja **como cadena**, nunca como número, aunque parezca numérico. |
| Errores | RFC 9457 (`application/problem+json`). El `type` es `https://reservasunimag.unimagdalena.edu.co/errors/<slug>`. |
| Caché | Las respuestas de consulta llevan `Cache-Control: no-store`: lo que se muestra es la foto del momento y no se reutiliza (UC8 FR-006). |

---

### 1. `GET /api/resources` — frontend → Módulo 2

**Parámetros de consulta**

| Parámetro | Tipo | Obligatorio | Validación | Origen |
|---|---|---|---|---|
| `date` | `yyyy-MM-dd` | sí | fecha válida | FR-001 |
| `start` | `HH:mm` | sí | dentro de 06:00–22:00 | FR-011 |
| `end` | `HH:mm` | sí | dentro de 06:00–22:00, `fin > inicio`, mismo día | FR-011 |
| `category` | `ESPACIO` \| `ACTIVO` | no | si se omite, se consultan las dos | FR-001 |
| `type` | cadena | no | se pasa tal cual al Módulo 1 | FR-001 |
| `minCapacity` | entero ≥ 1 | no | solo válido con `categoria=ESPACIO`; con `ACTIVO` es 400 | FR-001 |
| `page` | entero ≥ 1 | no | por defecto `1`; base 1, no 0 | FR-009 |

El tamaño de página **no es un parámetro**: son 20 fijos, los decide el Módulo 2 (FR-009). `type` es una cadena opaca que pertenece al Módulo 1; el Módulo 2 no tiene una enumeración propia de tipos y no la valida. Mientras el Módulo 1 no publique la lista de tipos (P-16), el frontend muestra la lista configurada en `ReservationProperties`; no se agrega un endpoint de tipos en UC1.

**Petición**

```http
GET /api/resources?date=2026-09-01&start=10:00&end=12:00&page=1 HTTP/1.1
Cookie: session=<jwt>
```

**`200 OK`** — caso normal, con los dos tipos de recurso y estados mezclados

```json
{
  "timeSlot": { "date": "2026-09-01", "start": "10:00", "end": "12:00" },
  "filters": { "category": null, "type": null, "minCapacity": null },
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 3, "total": 47 },
  "anySelectable": true,
  "catalogTruncated": false,
  "queriedAt": "2026-09-01T09:14:22-05:00",
  "resources": [
    {
      "id": "ESP-0412",
      "name": "Laboratorio de Redes",
      "category": "ESPACIO",
      "type": "LABORATORIO",
      "location": "Bloque 7, piso 2",
      "faculty": "Ingeniería",
      "maxCapacity": 30,
      "equipment": ["PROYECTOR", "AIRE_ACONDICIONADO"],
      "status": "BLOQUEO_ACADEMICO",
      "selectable": false,
      "reason": "El recurso está reservado para actividad instructor en la timeSlot consultada."
    },
    {
      "id": "ACT-000912",
      "name": "Microscopio M-014",
      "category": "ACTIVO",
      "type": "MICROSCOPIO",
      "location": "Laboratorio de Biología",
      "condition": "BUENO",
      "loanTermBusinessDays": 3,
      "status": "EN_MANTENIMIENTO",
      "selectable": false,
      "reason": "El recurso está en mantenimiento."
    },
    {
      "id": "ESP-0107",
      "name": "Sala de Estudio 3",
      "category": "ESPACIO",
      "type": "SALA_DE_ESTUDIO",
      "location": "Biblioteca, piso 1",
      "faculty": "Biblioteca Central",
      "maxCapacity": 8,
      "equipment": ["AIRE_ACONDICIONADO"],
      "status": "DISPONIBLE",
      "selectable": true
    }
  ]
}
```

El orden del ejemplo no es casual: es por `name` y, ante nombres iguales, por `id` (FR-014). El estado no influye, por eso el `DISPONIBLE` queda de último. `filters` repite lo que se pidió —con `null` cuando no se envió, porque aquí sí importa distinguir "no filtré" de "filtré"— para que el frontend pueda reconstruir la pantalla desde la respuesta.

**Campos del sobre**

| Campo | Tipo | Nota |
|---|---|---|
| `timeSlot` | objeto | La franja consultada, ya normalizada. |
| `filters` | objeto | Los filtros aplicados; `null` en los que no se enviaron. |
| `paginacion.pagina` | entero | Página que se está viendo, base 1 (FR-013). |
| `paginacion.pageSize` | entero | Siempre 20 (FR-009). |
| `paginacion.totalPages` | entero | `ceil(total / 20)`; `1` cuando `total` es 0. |
| `paginacion.total` | entero | Total de recursos que cumplen los filtros, no de la página (FR-013). |
| `anySelectable` | booleano | `false` cuando **ningún** recurso del conjunto filtrado completo es seleccionable, no solo de esta página (FR-012). |
| `catalogTruncated` | booleano | `true` cuando el catálogo del Módulo 1 superó `reservations.module1.max-pages` y no se recorrió entero; ver *Catálogo muy grande*. |
| `queriedAt` | instante | Momento de la respuesta: lo mostrado es una foto y se revalida al reservar. |
| `resources` | arreglo | Como máximo 20 elementos. |
| `message` | cadena | Solo cuando `anySelectable` es `false` (FR-012). |
| `nextTimeSlot` | objeto | Solo cuando `anySelectable` es `false` y se encontró una franja con disponibilidad (escenario 7). |
| `sanctionNotice` | objeto | Solo para `ESTUDIANTE` y `MONITOR`, y solo si hay algo que avisar (FR-016). |

**Campos de cada recurso**

| Campo | Tipo | Categoría | Nota |
|---|---|---|---|
| `id` | cadena | las dos | Identificador del Módulo 1. En un activo es su placa de inventario. |
| `name` | cadena | las dos | |
| `category` | `ESPACIO` \| `ACTIVO` | las dos | Discrimina qué atributos vienen. |
| `type` | cadena | las dos | Del Módulo 1. |
| `location` | cadena | las dos | |
| `faculty` | cadena | espacio | |
| `maxCapacity` | entero | espacio | Lo que pide el escenario 1. |
| `equipment` | arreglo de cadenas | espacio | Equipamiento fijo; códigos opacos del Módulo 1. |
| `condition` | cadena | activo | Del Módulo 1. |
| `loanTermBusinessDays` | entero | activo | Días hábiles de préstamo según el tipo. Lo usa UC2 FR-012; UC1 solo lo muestra. Se omite mientras el Módulo 1 no lo exponga (P-16). |
| `status` | enumeración | las dos | `DISPONIBLE`, `RESERVADO`, `EN_USO`, `BLOQUEO_ACADEMICO`, `EN_MANTENIMIENTO`. Es la etiqueta única que arma el Módulo 2 con la tabla de prioridad. |
| `selectable` | booleano | las dos | `true` solo si `status` es `DISPONIBLE` (FR-002). |
| `reason` | cadena | las dos | Texto listo para mostrar; se omite cuando `selectable` es `true`. |

**Decisión: un solo objeto para espacios y activos.** No hay dos formas distintas de `recurso` ni envoltorios por categoría: es un objeto con los campos comunes más los de su categoría, y `category` dice cuáles esperar. Así el frontend recorre una sola lista, que es exactamente lo que pide FR-012 (todos juntos, ordenados por nombre sin importar la categoría).

**Decisión: `status` es el código y `reason` es el texto.** FR-004 pide indicar qué motiva la exclusión, y `status` ya lo dice en forma de código. `reason` es la traducción fija de ese código, para que cualquier consumidor —no solo nuestro frontend— pueda explicárselo a la persona sin duplicar la tabla. Ningún texto nombra al titular de la reserva (UC8 FR-005):

| `status` | `reason` |
|---|---|
| `EN_MANTENIMIENTO` | `El recurso está en mantenimiento.` |
| `BLOQUEO_ACADEMICO` | `El recurso está reservado para actividad docente en la franja consultada.` |
| `EN_USO` | `El recurso está en uso en la franja consultada.` |
| `RESERVADO` | `El recurso ya está reservado en la franja consultada.` |
| `DISPONIBLE` | se omite |

**Reservado para UC8 FR-012.** Un recurso ocupado debería decir también *hasta cuándo* lo está. UC1 no lo envía todavía: el campo se llama `occupiedUntil` (instante, opcional, a nivel de recurso) y lo agrega el plan de UC8. Queda nombrado aquí para que el frontend no lo invente con otro nombre.

**`200 OK`** — escenario 7: ningún laboratorio seleccionable

```json
{
  "timeSlot": { "date": "2026-09-01", "start": "10:00", "end": "12:00" },
  "filters": { "category": "ESPACIO", "type": "LABORATORIO", "minCapacity": 20 },
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "total": 4 },
  "anySelectable": false,
  "catalogTruncated": false,
  "queriedAt": "2026-09-01T09:14:22-05:00",
  "message": "Ninguno de los 4 resources que cumplen los filters está disponible entre las 10:00 y las 12:00.",
  "nextTimeSlot": { "date": "2026-09-01", "start": "14:00", "end": "16:00" },
  "resources": [
    {
      "id": "ESP-0412",
      "name": "Laboratorio de Redes",
      "category": "ESPACIO",
      "type": "LABORATORIO",
      "location": "Bloque 7, piso 2",
      "faculty": "Ingeniería",
      "maxCapacity": 30,
      "equipment": ["PROYECTOR"],
      "status": "BLOQUEO_ACADEMICO",
      "selectable": false,
      "reason": "El recurso está reservado para actividad instructor en la timeSlot consultada."
    }
  ]
}
```

Cuando no hay ninguna franja libre hasta las 22:00, `nextTimeSlot` se omite y el `message` lo dice: `"Ninguno de los 4 recursos que cumplen los filtros está disponible entre las 10:00 y las 22:00 de hoy."`. La lista **siempre** se envía completa, aunque nada sea seleccionable (FR-012).

**`sanctionNotice`** — dos formas posibles (FR-016)

```json
"sanctionNotice": {
  "type": "ACTIVE_SANCTION",
  "reason": "Dos ausencias no justificadas en el semestre",
  "until": "2026-09-30",
  "message": "Tienes una sanción vigente until el 2026-09-30. Puedes consultar los resources, pero no podrás confirm una reserva."
}
```

```json
"sanctionNotice": {
  "type": "NOT_VERIFIED",
  "message": "No se pudo comprobar tu situación de sanciones en este momento. Se volverá a comprobar al confirm una reserva."
}
```

`reason` y `until` solo vienen con `ACTIVE_SANCTION` (UC6 FR-004). El campo entero se omite cuando la persona está al día, y también cuando el rol es `DIRECCION_PROGRAMA`, que no reserva. `NOT_VERIFIED` no es un error: la consulta responde `200` igual, porque la comprobación que decide es la de `Reservar recursos` (UC6 FR-006).

**Errores**

`400` — franja fuera de la ventana operativa (FR-011)

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/invalid-time-slot",
  "title": "Franja horaria inválida",
  "status": 400,
  "detail": "La timeSlot debe estar entre las 06:00 y las 22:00 del mismo día.",
  "instance": "/api/resources",
  "code": "TIME_SLOT_OUTSIDE_WINDOW",
  "timeSlot": { "date": "2026-09-01", "start": "05:00", "end": "07:00" }
}
```

`code` toma uno de `TIME_SLOT_OUTSIDE_WINDOW`, `TIME_SLOT_CROSSES_MIDNIGHT` o `TIME_SLOT_END_NOT_AFTER_START`, que son los tres rechazos de `TimeSlot` (T009).

`400` — parámetro mal formado o combinación inválida

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/invalid-parameters",
  "title": "Parámetros de consulta inválidos",
  "status": 400,
  "detail": "Revisa los parámetros de la consulta.",
  "instance": "/api/resources",
  "errors": [
    { "field": "minCapacity", "message": "solo aplica cuando la categoría es ESPACIO" },
    { "field": "page", "message": "debe ser mayor o igual a 1" }
  ]
}
```

`401` — sin sesión o con la cookie vencida

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/unauthenticated",
  "title": "Sesión requerida",
  "status": 401,
  "detail": "Inicia sesión para consultar los resources.",
  "instance": "/api/resources"
}
```

`503` — el Módulo 1 no respondió (FR-008)

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/inventory-unavailable",
  "title": "Inventario no disponible",
  "status": 503,
  "detail": "La información de resources no está disponible en este momento. Intenta de nuevo en unos minutos.",
  "instance": "/api/resources",
  "module": "MODULO_1"
}
```

Nunca se responde `200` con una lista vieja o parcial del catálogo: si el Módulo 1 falla, la consulta falla (FR-008, P-14). La caída del Módulo 3 **no** produce `503`: produce el `sanctionNotice` con `NOT_VERIFIED`.

Un `404` no existe en este endpoint: una consulta sin resultados es `200` con `total: 0`, `recursos: []`, `anySelectable: false` y un `message` que dice que ningún recurso cumple los filtros.

---

### 2. Módulo 1 — `InventoryPort`

Lo que sigue es **nuestra propuesta de contrato**, la misma que P-16 les pidió por escrito. Mientras no respondan, el adaptador real se escribe contra esto, el `InventoryFakeAdapter` lo imita y WireMock lo simula en T022; si el contrato final difiere, cambia solo `InventoryRestAdapter` y sus DTO. La autenticación entre módulos todavía no está acordada: se asume `Authorization: Bearer <token de servicio>` tomado de una variable de entorno (NEEDS CLARIFICATION, P-16).

#### 2.1 Catálogo filtrado — `GET /api/v1/resources`

```http
GET /api/v1/resources?category=ESPACIO&type=LABORATORIO&minCapacity=20&page=1&size=100 HTTP/1.1
Authorization: Bearer <token>
```

```json
{
  "content": [
    {
      "id": "ESP-0412",
      "name": "Laboratorio de Redes",
      "category": "ESPACIO",
      "type": "LABORATORIO",
      "operationalStatus": "DISPONIBLE",
      "location": "Bloque 7, piso 2",
      "faculty": "Ingeniería",
      "maxCapacity": 30,
      "equipment": ["PROYECTOR", "AIRE_ACONDICIONADO"]
    },
    {
      "id": "ACT-000912",
      "name": "Microscopio M-014",
      "category": "ACTIVO",
      "type": "MICROSCOPIO",
      "operationalStatus": "EN_MANTENIMIENTO",
      "statusReason": "Reparación de lente",
      "location": "Laboratorio de Biología",
      "assetTag": "ACT-000912",
      "condition": "BUENO",
      "loanTermBusinessDays": 3
    }
  ],
  "page": 1,
  "size": 100,
  "totalPages": 3,
  "total": 247
}
```

`operationalStatus` solo puede ser `DISPONIBLE`, `EN_USO` o `EN_MANTENIMIENTO`: son los tres estados que el Módulo 1 posee. Si llega cualquier otro valor —por ejemplo `RESERVADO`, que es nuestro— el adaptador lo registra y lo trata como `ExternalServiceUnavailableException`, porque significa que el reparto de estados se rompió y no queremos adivinar.

**Mapeo hacia el dominio**

| Campo del Módulo 1 | Campo nuestro | Nota |
|---|---|---|
| `id`, `assetTag` | `Resource.id` | En un activo, `assetTag` y `id` son lo mismo; si llegan distintos manda `id`. |
| `category` | `ResourceCategory` | `ESPACIO` o `ACTIVO`; otro valor es error de contrato. |
| `operationalStatus` | `OperationalStatus` | Entra en la tabla de prioridad de `DisplayedStatus`. |
| `statusReason` | — | **No se propaga al frontend.** Es texto libre de otro módulo y nuestro `reason` es fijo; se usa solo en logs. |
| `maxCapacity`, `equipment`, `faculty` | atributos del espacio | |
| `condition`, `loanTermBusinessDays` | atributos del activo | `loanTermBusinessDays` se omite si no viene (P-16); UC1 no lo necesita para decidir nada. |
| `total`, `totalPages` | — | Para recorrer las páginas, no para nuestra paginación. |

**Paginación del Módulo 1 y nuestro recorrido (UC8 FR-011).** Pedimos páginas de `reservations.module1.page-size` (por defecto 100) y recorremos **todas** antes de responder, porque `anySelectable` y la sugerencia del escenario 7 necesitan el conjunto completo, no solo los 20 que se muestran. El recorrido para al llegar a `totalPages` o al tope de `reservations.module1.max-pages` (por defecto 20, es decir 2000 recursos).

**Catálogo muy grande.** Si se alcanza el tope, no se falla ni se miente: se responde con lo recorrido, `catalogTruncated: true`, y el frontend invita a filtrar más. Ese tope y el `catalogTruncated` son una decisión de este plan; el spec no fija un límite, y sin él una consulta sin filtros sobre un catálogo enorme deja colgada la petición. Si el filtro de aforo no lo soporta el Módulo 1 y hay que aplicarlo nosotros, el tope se mide sobre los recursos traídos, no sobre los que sobreviven al filtro.

**Errores**

| Respuesta del Módulo 1 | Qué hace el adaptador | Qué ve la persona |
|---|---|---|
| `200` | Caso normal. | La lista. |
| `400` | Filtro que no soporta: se registra con el detalle y se trata como indisponible. | `503` |
| `401`, `403` | Problema de credenciales: se registra como error de configuración. | `503` |
| `5xx`, timeout, conexión rechazada | `ExternalServiceUnavailableException`. | `503` (FR-008) |
| Cuerpo ilegible o campo obligatorio ausente | `ExternalServiceUnavailableException` con el detalle en el log. | `503` |

No hay reintentos dentro de la misma consulta: el presupuesto de SC-001 ya está consumido por el propio Módulo 1 (5 s y 10 s). El timeout del cliente se configura en `reservations.module1.timeout` (P-17).

#### 2.2 Estado operativo por lote — `POST /api/v1/resources/operational-status`

```json
{ "resourceIds": ["ESP-0412", "ESP-0107", "ACT-000912"] }
```

```json
{
  "statuses": [
    { "resourceId": "ESP-0412", "operationalStatus": "DISPONIBLE" },
    { "resourceId": "ESP-0107", "operationalStatus": "EN_USO" },
    { "resourceId": "ACT-000912", "operationalStatus": "EN_MANTENIMIENTO", "statusReason": "Reparación de lente" }
  ],
  "notFound": []
}
```

Es `POST` y no `GET` con los ids en la URL porque la lista puede traer cientos de identificadores y pasaría del límite práctico de longitud de una URL. No modifica nada (UC8 FR-009).

UC1 **no llama a esta operación**: el catálogo ya trae `operationalStatus` de cada recurso en la misma respuesta, y pedirlo dos veces duplicaría la latencia del presupuesto de SC-001. Queda definida porque `InventoryPort.operationalStatus(resourceIds)` existe desde UC1 y la usan la revalidación de UC2 y la consulta de un solo recurso de UC8. Un `resourceId` que vuelva en `notFound` se trata como **no comprobable**, nunca como disponible (UC8 FR-007): el recurso se omite de la lista y queda registrado en el log, porque significa que el Módulo 1 lo dio de baja entre el catálogo y esta llamada (edge case *Cambio de estado entre páginas*).

---

### 3. Módulo 3 — `CompliancePort`

#### 3.1 Reporte de cumplimiento — `GET /api/v1/compliance/people/{codigo}`

Se pide por el **código institucional** de una sola persona, nunca por lotes y nunca por correo (UC6 FR-007). El `code` sale del claim del JWT, no de un parámetro que mande el frontend.

```http
GET /api/v1/compliance/people/2019114045 HTTP/1.1
Authorization: Bearer <token>
```

```json
{
  "person": { "code": "2019114045" },
  "activeSanctions": [
    {
      "reason": "Dos ausencias no justificadas en el semestre",
      "start": "2026-09-01",
      "end": "2026-09-30",
      "scope": "RESERVAS"
    }
  ],
  "absenceCount": 2,
  "lateReturns": 1,
  "generatedAt": "2026-09-01T09:14:21-05:00"
}
```

Persona al día: `activeSanctions: []` con los contadores en `0`. Persona que reserva por primera vez: el Módulo 3 puede responder `200` con el reporte vacío o `404`; **las dos se leen como "sin sanciones"**, no como fallo (UC6 FR-008).

El reporte se usa solo para avisar. UC1 no guarda nada de él (UC6 FR-005), no lo cachea entre consultas y toma únicamente la sanción de `end` más lejano para armar el `sanctionNotice`. `absenceCount` y `lateReturns` se reciben porque son parte del reporte, pero UC1 no los muestra.

**Errores**

| Respuesta del Módulo 3 | Qué hace el adaptador | Qué ve la persona |
|---|---|---|
| `200` | Reporte normal. | `sanctionNotice` o nada. |
| `404` | Sin historial → reporte vacío (UC6 FR-008). | Nada. |
| `401`, `403` | Error de configuración, registrado. | `sanctionNotice` con `NOT_VERIFIED`. |
| `5xx`, timeout, conexión rechazada | `ExternalServiceUnavailableException`, capturada por `SearchResourcesUseCase`. | `sanctionNotice` con `NOT_VERIFIED`. |

La diferencia con el Módulo 1 es deliberada: sin catálogo no hay nada que mostrar, pero sin sanciones sí (UC6 FR-006 y P-10). El timeout va en `reservations.module3.timeout` y es más corto que el del Módulo 1, porque esta llamada no puede comerse el presupuesto de la que sí es obligatoria.

> Las sanciones son la única integración con el Módulo 3 que sigue siendo REST síncrona. La ficha, la cancelación, la no asistencia y el check-out van por Kafka (ver [plan-arquitectura.md](./plan-arquitectura.md#mensajería-con-kafka)) y no entran en UC1.

---

### 4. Login mínimo (T013)

No es el spec de registro e inicio de sesión, que sigue pendiente. Es lo mínimo para que UC1 se pueda probar y demostrar con usuarios sembrados por `V2__local_data.sql`.

**`POST /api/auth/login`**

```json
{ "email": "estudiante@unimagdalena.edu.co", "password": "..." }
```

`204 No Content` con la cookie de sesión; el cuerpo va vacío a propósito, para que el JWT no quede al alcance de JavaScript:

```http
HTTP/1.1 204 No Content
Set-Cookie: session=<jwt>; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=28800
```

Credenciales incorrectas → `401` con `type` `.../errors/invalid-credentials` y un `detail` que **no** distingue si el correo no existe o si la contraseña está mal.

**`GET /api/auth/me`** — lo usa el layout del grupo `(app)` para saber si hay sesión y qué rol tiene

```json
{ "id": "5f1b...", "code": "2019114045", "name": "Camilo Cerpa", "role": "ESTUDIANTE" }
```

Sin cookie válida → `401` con el mismo `ProblemDetail` de `no-autenticado`.

**`POST /api/auth/logout`** → `204` y la cookie borrada (`Max-Age=0`).

**Claims del JWT**

| Claim | Contenido |
|---|---|
| `sub` | `usuario.id` (uuid) |
| `code` | Código institucional; es lo que se le manda al Módulo 3 (UC6 FR-007) |
| `role` | `ESTUDIANTE`, `MONITOR` o `DIRECCION_PROGRAMA` |
| `iss`, `iat`, `exp` | Emisor y vigencia de 8 horas |

El `CurrentUser` que reciben los casos de uso se arma con `sub`, `code` y `role`, y nunca llega desde el cuerpo o la URL de la petición.

---

### 5. Tipos del frontend

Son la traducción literal del contrato de la sección 1 y viven en `frontend/src/features/resources/types.ts`. Lo que el backend omite es opcional aquí; nada es `| null` salvo los filtros, que sí distinguen "no filtré".

```ts
export type Categoria = "ESPACIO" | "ACTIVO";

export type DisplayedStatus =
  | "DISPONIBLE" | "RESERVADO" | "EN_USO"
  | "BLOQUEO_ACADEMICO" | "EN_MANTENIMIENTO";

export interface Franja { date: string; start: string; end: string }

export interface ResourceWithStatus {
  id: string;
  name: string;
  category: Categoria;
  type: string;
  location: string;
  faculty?: string;                   // espacio
  maxCapacity?: number;                // espacio
  equipment?: string[];             // espacio
  condition?: string;               // active
  loanTermBusinessDays?: number;   // active
  status: DisplayedStatus;
  selectable: boolean;
  reason?: string;
  occupiedUntil?: string;               // lo agrega el plan de UC8 (FR-012)
}

export interface SanctionNotice {
  type: "ACTIVE_SANCTION" | "NOT_VERIFIED";
  reason?: string;
  until?: string;
  message: string;
}

export interface SearchResourcesParams {
  date: string;
  start: string;
  end: string;
  category?: Categoria;
  type?: string;
  minCapacity?: number;
  page?: number;
}

export interface SearchResourcesResponse {
  timeSlot: Franja;
  filters: { category: Categoria | null; type: string | null; minCapacity: number | null };
  pagination: { page: number; pageSize: number; totalPages: number; total: number };
  anySelectable: boolean;
  catalogTruncated: boolean;
  queriedAt: string;
  resources: ResourceWithStatus[];
  message?: string;
  nextTimeSlot?: Franja;
  sanctionNotice?: SanctionNotice;
}

/** ProblemDetail (RFC 9457) tal como lo traduce GlobalErrorHandler. */
export interface ErrorApi {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  code?: string;
  errors?: { field: string; message: string }[];
  module?: "MODULO_1" | "MODULO_3";
}
```

`frontend/src/lib/api/client.ts` (T015) convierte cualquier respuesta `>= 400` en un error tipado con este `ErrorApi`, así que las pantallas distinguen el `503` del inventario del `400` de la franja sin leer textos.

---

### 6. Fixtures compartidos

Los JSON de esta sección se guardan una sola vez y los usan las pruebas y el perfil local, para que el contrato no se reescriba en cada test:

```text
src/test/resources/contratos/
├── modulo1-catalogo-pagina1.json        # y -pagina2, -pagina3 (UC8 FR-011)
├── modulo1-catalogo-vacio.json
├── modulo1-estado-operativo.json
├── modulo3-reporte-sancionado.json
├── modulo3-reporte-al-dia.json
├── api-recursos-respuesta.json          # el 200 normal, forma esperada en T023
├── api-recursos-sin-seleccionables.json # escenario 7
└── api-recursos-error-503.json
```

Los adaptadores falsos del perfil local (T035) sirven datos coherentes con estos mismos archivos, para que lo que se ve en la demo sea lo mismo que afirman las pruebas.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Dejar el proyecto listo para construir la arquitectura del plan general.

- [ ] T001 Crear la estructura de paquetes `domain/{model,port/in,port/out,usecase}` e `infrastructure/{adapter/in,adapter/out,config}` en `src/main/java/edu/unimagdalena/reservasunimag/`, con un `package-info.java` por capa que explique qué puede importar
- [ ] T002 Agregar a `build.gradle` Security, OAuth2 Resource Server, Validation, Flyway (`flyway-core` y `flyway-database-postgresql`), springdoc-openapi, y en pruebas WireMock y ArchUnit
- [ ] T003 [P] Configurar `src/main/resources/application.properties`: zona horaria `America/Bogota`, `spring.jpa.hibernate.ddl-auto=validate`, Flyway activado, URLs y timeouts de los Módulos 1 y 3 como propiedades; y crear `application-local.properties` para el perfil local
- [ ] T004 [P] Fijar la imagen de PostgreSQL en `src/test/java/.../TestcontainersConfiguration.java` a una versión concreta en vez de `postgres:latest`, para que las pruebas sean reproducibles
- [ ] T005 [P] Crear el frontend con `create-next-app` en `frontend/` (TypeScript, App Router, ESLint) y configurar en `frontend/next.config.ts` el *rewrite* de `/api/*` hacia el backend

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Infraestructura compartida que UC1 necesita y que también van a usar los demás casos de uso.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T006 Escribir `src/main/resources/db/migration/V1__initial_schema.sql` con la extensión `btree_gist` y las tablas `app_user`, `reservation` (con `occupancy` generada, la restricción de exclusión y el `CHECK` de titular según origen), `loan` y `academic_block`, según el modelo del plan general
- [ ] T007 [P] Crear `ArchitectureTest.java` con ArchUnit: `domain` no depende de `infrastructure` ni de `org.springframework`, `jakarta.persistence` o `com.fasterxml`
- [ ] T008 [P] Crear el puerto `Clock` en `domain/port/out/Clock.java` y su implementación sobre `java.time.Clock` en `infrastructure/config/ClockConfig.java`
- [ ] T009 [P] Crear `domain/model/reservation/TimeSlot.java`: fecha, hora de inicio y hora de fin en `America/Bogota`; rechaza con `InvalidTimeSlotException` una franja fuera de 06:00 a 22:00, que cruce la medianoche o con inicio ≥ fin (FR-010, FR-011); expone `seCruzaCon(inicio, fin)`
- [ ] T010 [P] Crear `domain/model/common/Page.java` y `PageRequest.java`, con tamaño por defecto de 20 tomado de `ReservationProperties`
- [ ] T011 Crear `infrastructure/adapter/in/web/error/GlobalErrorHandler.java`, que traduce `InvalidTimeSlotException` a 400 y `ExternalServiceUnavailableException` a 503 como `ProblemDetail`
- [ ] T012 Configurar `infrastructure/config/SecurityConfig.java`: validación del JWT desde la cookie `httpOnly`, roles `ESTUDIANTE`, `MONITOR` (hereda de `ESTUDIANTE`, FR-005) y `DIRECCION_PROGRAMA`; y un resolvedor que entrega `CurrentUser` a los controladores
- [ ] T013 Crear `V2__local_data.sql`, que solo se aplica con el perfil local, con un usuario por rol, y el login mínimo (`POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`) según [Contratos §4](#4-login-mínimo-t013), para poder probar la consulta. El autorregistro queda para su propio spec
- [ ] T014 [P] Crear `infrastructure/config/HttpClientsConfig.java` con un `RestClient` por módulo externo, cada uno con su URL base y su timeout
- [ ] T015 [P] Crear `frontend/src/lib/api/client.ts`, que envía la cookie de sesión y convierte los `ProblemDetail` en errores tipados, y el layout del grupo `(app)`, que redirige al login si no hay sesión

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Consultar recursos disponibles (Priority: P1)

**Goal**: Estudiante, Monitor y Dirección de Programa ven, para una fecha y franja, todos los recursos que cumplen los filtros con su estado, paginados de 20 en 20, y solo los `DISPONIBLE` son seleccionables.

**Independent Test**: Con el perfil local, cargar en los adaptadores falsos y en la base recursos con los cinco estados, consultar una franja y verificar que aparecen todos con su estado y que solo los `DISPONIBLE` son seleccionables. No necesita que exista `Reservar recursos`.

### Tests for User Story 1

- [ ] T016 [P] [US1] Pruebas unitarias en `FranjaHorariaTest.java`: ventana de 06:00 a 22:00, medianoche, inicio ≥ fin, cruce de un minuto y cruce parcial (edge case **Solapamiento parcial**)
- [ ] T017 [P] [US1] Pruebas unitarias en `EstadoVisibleTest.java`: la tabla de prioridad completa, incluidos los empates entre `EN_MANTENIMIENTO` y un bloqueo, y entre un bloqueo y una reserva
- [ ] T018 [P] [US1] Pruebas en `ConsultarDisponibilidadUseCaseTest.java` con puertos falsos: los escenarios 1 a 6 del spec, el préstamo vencido y no devuelto, y el Módulo 1 caído (UC8 FR-007)
- [ ] T019 [P] [US1] Pruebas en `ConsultarRecursosUseCaseTest.java`: orden por nombre e identificador (FR-014), 47 recursos en páginas de 20, 20 y 7 sin repetir (escenario 8), 20 o menos en una sola página (FR-009), filtro de aforo mínimo, aviso de sanción para Estudiante y no para Dirección (FR-016), y el Módulo 3 caído sin tumbar la consulta
- [ ] T020 [P] [US1] Pruebas en `BuscadorSiguienteFranjaTest.java`: ningún laboratorio seleccionable con una franja libre más tarde (escenario 7), y sin franja libre hasta las 22:00
- [ ] T021 [P] [US1] Prueba de integración `OcupacionPersistenceAdapterIT.java` con Testcontainers: la consulta de ocupaciones con rangos, las reservas canceladas que no ocupan, el préstamo vencido sin devolver que sí ocupa, y los bloqueos académicos
- [ ] T022 [P] [US1] Guardar los JSON de [Contratos §2 y §3](#2-módulo-1--inventoryport) como *fixtures* en `src/test/resources/contratos/` y escribir con ellos `InventarioRestAdapterTest.java` y `CumplimientoRestAdapterTest.java` con WireMock: respuesta normal, catálogo en varias páginas que se recorren todas (UC8 FR-011), tope de `max-pages` que marca `catalogTruncated`, `operationalStatus` desconocido, timeout y 5xx convertidos en `ExternalServiceUnavailableException`, y persona sin historial —`200` vacío y `404`— leída como "sin sanciones" (UC6 FR-008)
- [ ] T023 [P] [US1] Prueba `RecursoControllerTest.java` con `@WebMvcTest`, comparando contra los *fixtures* `api-recursos-*.json`: parámetros válidos, `minCapacity` con `categoria=ACTIVO` y `pagina=0` como 400 de parámetros, 400 por franja inválida con su `code`, 401 sin sesión, 503 con el Módulo 1 caído, las dos formas de `sanctionNotice`, y que ningún campo opcional viaje como `null` ni se filtre el titular de una reserva (UC8 FR-005)

### Implementation for User Story 1

- [ ] T024 [P] [US1] Crear el modelo de recurso en `domain/model/resource/`: `ResourceCategory`, `Resource` (atributos de espacio y de activo según el spec), `OperationalStatus`, `DisplayedStatus` con su prioridad, `ResourceFilter` y `ResourceWithStatus`
- [ ] T025 [P] [US1] Crear `Occupancy` y `OccupancyType` en `domain/model/reservation/`, y `ComplianceReport` y `Sanction` en `domain/model/compliance/`
- [ ] T026 [P] [US1] Definir los puertos de salida `InventoryPort`, `OccupancyRepositoryPort` y `CompliancePort` en `domain/port/out/`
- [ ] T027 [P] [US1] Definir los puertos de entrada `SearchResourcesPort`, `CheckAvailabilityPort` (versión por lote, UC8 FR-008) y `CheckSanctionsPort` en `domain/port/in/`
- [ ] T028 [US1] Implementar `CheckAvailabilityUseCase`: un estado por recurso con su motivo, sin exponer al titular (UC8 FR-004, FR-005) y sin modificar nada (UC8 FR-009) (depende de T024 a T027)
- [ ] T029 [US1] Implementar `CheckSanctionsUseCase` en modo lectura: obtiene el reporte de una sola persona y traduce la falta de respuesta a "no comprobado" (UC6 FR-001, FR-004, FR-006, FR-007)
- [ ] T030 [US1] Implementar `NextFreeSlotFinder` según la decisión de diseño (depende de T028)
- [ ] T031 [US1] Implementar `SearchResourcesUseCase`: catálogo del Módulo 1, filtro de aforo, orden estable, estado de todo el conjunto, paginación, mensaje cuando no hay seleccionables, sugerencia y aviso de sanción (depende de T028 a T030)
- [ ] T032 [P] [US1] Implementar `OccupancyPersistenceAdapter`, con las entidades JPA de lectura y la consulta nativa de ocupaciones en `OccupancyJpaRepository`
- [ ] T033 [P] [US1] Implementar `InventoryRestAdapter` y sus DTO contra [Contratos §2](#2-módulo-1--inventoryport) (el contrato que pide P-16): recorre todas las páginas del Módulo 1 hasta `max-pages`, aplica el mapeo hacia el dominio y convierte timeouts y errores en `ExternalServiceUnavailableException`
- [ ] T034 [P] [US1] Implementar `ComplianceRestAdapter` y sus DTO según [Contratos §3](#3-módulo-3--complianceport), con el `404` leído como reporte vacío
- [ ] T035 [P] [US1] Implementar `InventoryFakeAdapter` y `ComplianceFakeAdapter` para el perfil local, con un catálogo de ejemplo que cubra espacios y activos de varios tipos
- [ ] T036 [US1] Registrar los casos de uso como beans, con `@Transactional(readOnly = true)`, en `infrastructure/config/UseCasesConfig.java` (depende de T028 a T035)
- [ ] T037 [US1] Implementar `ResourceController` (`GET /api/resources`) exactamente como lo fija [Contratos §1](#1-get-apiresources--frontend--módulo-2): `SearchResourcesParams` validados, `SearchResourcesResponse` que omite los campos que no aplican, `Cache-Control: no-store`, y la documentación OpenAPI con los mismos ejemplos de la sección
- [ ] T038 [P] [US1] Frontend: copiar los tipos de [Contratos §5](#5-tipos-del-frontend) a `frontend/src/features/resources/types.ts` y escribir la llamada en `api.ts`
- [ ] T039 [US1] Frontend: `ResourceFilters.tsx` (fecha, franja limitada a 06:00–22:00, tipo y aforo mínimo solo para espacios), `ResourceList.tsx` (estado de cada recurso y selección solo de los `DISPONIBLE`), `Pagination.tsx` ("20 de 250", anterior y siguiente) y `SanctionNotice.tsx`
- [ ] T040 [US1] Frontend: `frontend/src/app/(app)/resources/page.tsx`, que arma la pantalla y muestra los estados vacío, "no hay disponibles" con la sugerencia, y "el inventario no está disponible en este momento" (FR-008)

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Mejoras que afectan a toda la funcionalidad

- [ ] T041 [P] Medir el tiempo de la consulta con 100 y 500 usuarios concurrentes contra un Módulo 1 simulado con la latencia prometida, y verificar SC-001
- [ ] T042 [P] Registrar en logs cada llamada a los Módulos 1 y 3, con su duración y su resultado, sin datos personales
- [ ] T043 [P] Actualizar el README con el perfil local (`./gradlew bootTestRun --args='--spring.profiles.active=local'`) y con cómo levantar el frontend
- [ ] T044 Enviar la sección **Contratos** (§2 y §3) a los equipos de los Módulos 1 y 3 como propuesta concreta de P-16 y P-20, revisar con el equipo las decisiones marcadas NEEDS CLARIFICATION de este plan y llevarlas a `pendientes-clarificacion.md` si siguen abiertas

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depende de Foundational
- **Polish (Final Phase)**: Depende de que User Story 1 esté completa

### Dependencias con otros casos de uso

- **UC8 `Consultar disponibilidad de los recursos`**: este plan implementa la versión por lote que necesita UC1 (UC8 FR-001 a FR-009 y FR-011). Su propio plan agrega "hasta cuándo" está ocupado (UC8 FR-012) y la consulta de un solo recurso para `Reservar recursos`.
- **UC6 `Consultar sanciones`**: este plan implementa la lectura del reporte. Su plan agrega el registro de auditoría de cada consulta (UC6 FR-009) y la denegación `RES-003` al reservar.
- **UC2 `Reservar recursos`**: no hace falta para UC1. Mientras no exista, las ocupaciones se cargan con datos de prueba.
- **Registro e inicio de sesión**: T013 deja un login mínimo con usuarios sembrados. El autorregistro espera su spec.

### Within User Story 1

- Modelo (T024, T025) → puertos (T026, T027) → casos de uso (T028 → T029, T030 → T031)
- Los adaptadores (T032 a T035) solo dependen de los puertos, así que van en paralelo con los casos de uso
- Beans (T036) → controlador (T037) → frontend (T038 a T040)

### Parallel Opportunities

- En Setup: T003, T004 y T005
- En Foundational: T007 a T010, T014 y T015
- En User Story 1: todas las pruebas (T016 a T023), el modelo y los puertos (T024 a T027), y los adaptadores (T032 a T035)

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Verify tests pass
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- La sección **Contratos** es la única fuente del JSON de UC1: si algo cambia ahí, cambia en los *fixtures*, en el OpenAPI y en los tipos del frontend, no al revés.
- **NEEDS CLARIFICATION abiertos en este plan**: P-14 (se asume que la consulta falla sin el Módulo 1, como ya dice FR-008), P-16 (el contrato de §2 es nuestra propuesta, incluida la autenticación entre módulos, mientras el Módulo 1 no responda), P-17 (timeouts), P-20 punto 3 (si el mantenimiento trae fechas) y P-10 (se asume que la consulta no se bloquea cuando el Módulo 3 falla).
