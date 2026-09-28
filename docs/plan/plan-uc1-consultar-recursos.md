# Implementation Plan: Consultar recursos (UC1)

**Date**: 2026-09-21
**Spec**: [spec-modulo2-uc1-consultar-recursos.md](../specs/spec-modulo2-uc1-consultar-recursos.md)
**Plan general**: [plan-arquitectura.md](./plan-arquitectura.md)

## Summary

UC1 es la puerta de entrada del módulo. Estudiante, Monitor y Dirección de Programa consultan el catálogo de recursos filtrando por fecha, franja horaria, tipo y, para espacios, aforo mínimo. Ven **todos** los recursos que cumplen los filtros, cada uno con su estado en esa franja, y solo pueden seleccionar los que están `DISPONIBLE` (FR-002, FR-012).

**Enfoque técnico:**

1. El caso de uso `ConsultarRecursosUseCase` pide el catálogo filtrado al Módulo 1 a través de `InventarioPort`, recorriendo todas sus páginas (UC8 FR-011), y lo ordena por nombre e identificador (FR-014).
2. Calcula el estado de **todo** el conjunto filtrado con `ConsultarDisponibilidadPort` (el `<<include>>` a UC8). El estado sale de cruzar el estado operativo del Módulo 1 con las ocupaciones que guarda el Módulo 2 (reservas, préstamos y bloqueos), en una sola consulta a PostgreSQL con rangos `tstzrange`.
3. Pagina en memoria, de 20 en 20 (FR-009). Como el estado se calcula para todo el conjunto, se sabe si **ninguno** es seleccionable (escenario 7) y se puede sugerir la siguiente franja libre.
4. Si quien consulta es Estudiante o Monitor, consulta sus sanciones con `ConsultarSancionesPort` (el `<<include>>` a UC6) y agrega un aviso a la respuesta (FR-016).
5. Si el Módulo 1 no responde, la consulta falla con un error explícito y nunca muestra una lista vieja (FR-008, P-14).

Como UC1 es el primer caso de uso que se implementa, este plan también monta la base compartida del proyecto: dependencias, esquema inicial, seguridad, manejo de errores, reloj y el esqueleto del frontend. De UC8 y UC6 implementa solo la parte que UC1 necesita; sus propios planes la completan.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript con Next.js y React (frontend)
**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Data JPA, Security, OAuth2 Resource Server para validar el JWT propio, Validation, RestClient), Flyway, springdoc-openapi; Next.js (App Router)
**Storage**: PostgreSQL con `btree_gist`. UC1 solo **lee** del Módulo 2 las tablas `reserva`, `prestamo` y `bloqueo_academico`; el catálogo no se guarda (FR-006).
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
│   │   ├── comun/
│   │   │   ├── Pagina.java                         # página de resultados con total y número
│   │   │   └── SolicitudPagina.java
│   │   ├── recurso/
│   │   │   ├── CategoriaRecurso.java               # ESPACIO, ACTIVO
│   │   │   ├── Recurso.java                        # ficha del catálogo que llega del Módulo 1
│   │   │   ├── EstadoOperativo.java                # DISPONIBLE, EN_USO, EN_MANTENIMIENTO (Módulo 1)
│   │   │   ├── EstadoVisible.java                  # los cinco estados con su prioridad
│   │   │   ├── FiltroRecursos.java                 # tipo, categoría, aforo mínimo
│   │   │   └── RecursoConEstado.java               # recurso + estado + seleccionable + motivo
│   │   ├── reserva/
│   │   │   ├── FranjaHoraria.java                  # valida ventana, mismo día y zona horaria
│   │   │   ├── Ocupacion.java                      # lo que ocupa un recurso en el tiempo
│   │   │   └── TipoOcupacion.java                  # RESERVA_ESPACIO, PRESTAMO_APARTADO,
│   │   │                                           # PRESTAMO_ENTREGADO, BLOQUEO_ACADEMICO
│   │   ├── cumplimiento/
│   │   │   ├── ReporteDeCumplimiento.java
│   │   │   └── Sancion.java
│   │   ├── usuario/
│   │   │   ├── Rol.java
│   │   │   └── UsuarioActual.java                  # id y rol de quien consulta
│   │   └── error/
│   │       ├── FranjaInvalidaException.java
│   │       └── ServicioExternoNoDisponibleException.java
│   ├── port/
│   │   ├── in/
│   │   │   ├── ConsultarRecursosPort.java
│   │   │   ├── ConsultarDisponibilidadPort.java    # UC8 (versión por lote)
│   │   │   └── ConsultarSancionesPort.java         # UC6 (lectura)
│   │   └── out/
│   │       ├── InventarioPort.java                 # Módulo 1
│   │       ├── OcupacionRepositoryPort.java        # reservas, préstamos y bloqueos del Módulo 2
│   │       ├── CumplimientoPort.java               # Módulo 3
│   │       └── Reloj.java
│   └── usecase/
│       ├── consultarrecursos/
│       │   ├── ConsultarRecursosUseCase.java
│       │   ├── ConsultaRecursos.java               # comando: filtros + franja + página + usuario
│       │   ├── ResultadoConsultaRecursos.java      # página + aviso de sanción + sugerencia
│       │   └── BuscadorSiguienteFranja.java        # escenario 7
│       ├── consultardisponibilidad/
│       │   └── ConsultarDisponibilidadUseCase.java
│       └── consultarsanciones/
│           └── ConsultarSancionesUseCase.java
│
└── infrastructure/
    ├── adapter/
    │   ├── in/web/
    │   │   ├── recurso/
    │   │   │   ├── RecursoController.java          # GET /api/recursos
    │   │   │   ├── ConsultaRecursosParams.java
    │   │   │   └── ConsultaRecursosResponse.java
    │   │   └── error/
    │   │       └── ManejadorGlobalErrores.java     # excepciones → ProblemDetail
    │   └── out/
    │       ├── persistence/
    │       │   ├── entity/                         # ReservaJpa, PrestamoJpa, BloqueoAcademicoJpa, UsuarioJpa
    │       │   ├── repository/OcupacionJpaRepository.java
    │       │   └── OcupacionPersistenceAdapter.java
    │       ├── modulo1/
    │       │   ├── InventarioRestAdapter.java
    │       │   ├── InventarioFakeAdapter.java      # perfil local, mientras el Módulo 1 no esté
    │       │   └── dto/
    │       └── modulo3/
    │           ├── CumplimientoRestAdapter.java
    │           ├── CumplimientoFakeAdapter.java    # perfil local
    │           └── dto/
    └── config/
        ├── CasosDeUsoConfig.java
        ├── SeguridadConfig.java
        ├── RelojConfig.java
        ├── ClientesHttpConfig.java
        └── PropiedadesReservas.java                # ventana, tamaño de página, paso de sugerencia

src/main/resources/
├── application.properties
├── application-local.properties                    # perfil local: adaptadores falsos
└── db/migration/
    ├── V1__esquema_inicial.sql
    └── V2__datos_local.sql                         # solo perfil local (usuarios de prueba)

src/test/java/edu/unimagdalena/reservasunimag/
├── ArquitecturaTest.java
├── domain/model/reserva/FranjaHorariaTest.java
├── domain/model/recurso/EstadoVisibleTest.java
├── domain/usecase/consultarrecursos/ConsultarRecursosUseCaseTest.java
├── domain/usecase/consultarrecursos/BuscadorSiguienteFranjaTest.java
├── domain/usecase/consultardisponibilidad/ConsultarDisponibilidadUseCaseTest.java
└── infrastructure/adapter/
    ├── in/web/recurso/RecursoControllerTest.java
    ├── out/persistence/OcupacionPersistenceAdapterIT.java
    ├── out/modulo1/InventarioRestAdapterTest.java
    └── out/modulo3/CumplimientoRestAdapterTest.java

frontend/
├── package.json, next.config.ts, tsconfig.json
└── src/
    ├── app/(app)/recursos/page.tsx                 # pantalla de consulta
    ├── features/recursos/
    │   ├── FiltrosRecursos.tsx
    │   ├── ListaRecursos.tsx
    │   ├── Paginacion.tsx
    │   ├── AvisoSancion.tsx
    │   ├── api.ts
    │   └── tipos.ts
    └── lib/api/cliente.ts                          # fetch hacia /api con la cookie de sesión
```

**Structure Decision**: Se usa la estructura web del plan general: backend hexagonal en la raíz (`domain` e `infrastructure`) y frontend Next.js en `frontend/`. Hay una diferencia con el plan general: la pantalla de recursos va en el grupo `(app)` y no en `(estudiante)`, porque Dirección de Programa también la usa. Los adaptadores falsos del Módulo 1 y del Módulo 3 existen para poder desarrollar y hacer la demo mientras esos módulos no publiquen su API (P-16).

### Decisiones de diseño de este caso de uso

**Cómo se calcula el estado de cada recurso.** Lo hace `ConsultarDisponibilidadUseCase`, en este orden de prioridad (spec, sección **Estados que se muestran**):

| Prioridad | Estado | Condición                                                                                                                                                                             |
|---|---|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | `EN_MANTENIMIENTO` | El Módulo 1 lo reporta así. Se aplica a cualquier franja consultada (el mantenimiento no reporta fecha, ellos hacen el cambio de mantenimiento a disponible).                         |
| 2 | `BLOQUEO_ACADEMICO` | Hay un bloqueo académico `CONFIRMADA` que se cruza con la franja.                                                                                                                     |
| 3 | `EN_USO` | Hay un préstamo **entregado** y sin devolver que se cruza con la franja (escenario 6), o el Módulo 1 reporta `EN_USO` y la franja consultada incluye el momento actual (escenario 4). |
| 3 | `RESERVADO` | Hay una reserva de espacio o un préstamo apartado sin recoger que se cruza con la franja.                                                                                             |
| 4 | `DISPONIBLE` | Ninguna de las anteriores.                                                                                                                                                            |

**Préstamo vencido y no devuelto.** Sigue ocupando el recurso aunque haya pasado su vencimiento (UC2 FR-015). Por eso la consulta de ocupaciones no se limita al rango `ocupacion`: un préstamo con `entregado_en` y sin `devuelto_en` ocupa desde su inicio y sin fin.

```sql
SELECT r.recurso_id, r.origen, r.categoria_recurso, r.inicio, r.fin,
       p.entregado_en, p.devuelto_en
FROM reserva r
LEFT JOIN prestamo p ON p.reserva_id = r.id
WHERE r.estado = 'CONFIRMADA'
  AND r.recurso_id = ANY(:recursoIds)
  AND ( r.ocupacion && tstzrange(:inicio, :fin, '[)')
     OR (p.entregado_en IS NOT NULL AND p.devuelto_en IS NULL AND r.inicio < :fin) );
```

**Paginación.** El Módulo 2 decide el tamaño de página, no el Módulo 1 (FR-009). Cada petición de página repite la consulta con los mismos filtros y el número de página. No se congela nada entre páginas (edge case **Cambio de estado entre páginas**). La respuesta incluye `total`, `pagina`, `totalPaginas` y `haySeleccionables`.

**Sugerencia de la siguiente franja (escenario 7).** Cuando ningún recurso del conjunto filtrado es seleccionable, se prueban franjas de la misma duración que la consultada, que empiezan desde el fin de esa franja en pasos de 30 minutos y llegan hasta las 22:00 del mismo día. Se usa la foto del catálogo ya obtenida y una sola consulta de ocupaciones que cubre el resto del día. Si no hay ninguna, la respuesta lo dice. El paso de 30 minutos y el límite al mismo día son una decisión de este plan, porque el spec no los fija; son parametrizables.

**Sanciones durante la consulta (FR-016).** Solo para los roles `ESTUDIANTE` y `MONITOR`: Dirección de Programa no reserva. Si hay sanción vigente, la respuesta trae un aviso con su motivo y su fecha de fin (UC6 FR-004), pero la lista se muestra igual. Si el Módulo 3 no responde, la consulta **no falla**: trae un aviso de que no se pudo comprobar la situación de la persona (UC6 FR-006), porque la comprobación que decide es la de `Reservar recursos` al confirmar. (NEEDS CLARIFICATION: P-10 fija la política al reservar; aquí se asume que la consulta no se bloquea.)

**Contrato con el Módulo 1.** `InventarioPort` expone dos operaciones pensadas desde nuestro lado: `buscarCatalogo(FiltroRecursos)`, que devuelve la ficha de cada recurso con su estado operativo, y `estadoOperativo(recursoIds)`. El adaptador REST se escribe contra el contrato pedido en P-16 y se prueba con WireMock. Si el Módulo 1 no filtra por aforo, el filtro se aplica en el caso de uso después de traer el catálogo.

**Contrato HTTP.**

```text
GET /api/recursos?fecha=2026-09-01&inicio=10:00&fin=12:00&tipo=LABORATORIO&aforoMinimo=20&pagina=1
→ 200 { recursos: [{ id, nombre, categoria, tipo, aforo, ubicacion, ..., estado, seleccionable }],
        pagina, totalPaginas, total, haySeleccionables, mensaje?, siguienteFranja?, avisoSancion? }
→ 400 ProblemDetail  franja fuera de la ventana, que cruza la medianoche o con inicio ≥ fin
→ 401                sin sesión
→ 503 ProblemDetail  el Módulo 1 no respondió (FR-008)
```

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

- [ ] T006 Escribir `src/main/resources/db/migration/V1__esquema_inicial.sql` con la extensión `btree_gist` y las tablas `usuario`, `reserva` (con `ocupacion` generada, la restricción de exclusión y el `CHECK` de titular según origen), `prestamo` y `bloqueo_academico`, según el modelo del plan general
- [ ] T007 [P] Crear `ArquitecturaTest.java` con ArchUnit: `domain` no depende de `infrastructure` ni de `org.springframework`, `jakarta.persistence` o `com.fasterxml`
- [ ] T008 [P] Crear el puerto `Reloj` en `domain/port/out/Reloj.java` y su implementación sobre `java.time.Clock` en `infrastructure/config/RelojConfig.java`
- [ ] T009 [P] Crear `domain/model/reserva/FranjaHoraria.java`: fecha, hora de inicio y hora de fin en `America/Bogota`; rechaza con `FranjaInvalidaException` una franja fuera de 06:00 a 22:00, que cruce la medianoche o con inicio ≥ fin (FR-010, FR-011); expone `seCruzaCon(inicio, fin)`
- [ ] T010 [P] Crear `domain/model/comun/Pagina.java` y `SolicitudPagina.java`, con tamaño por defecto de 20 tomado de `PropiedadesReservas`
- [ ] T011 Crear `infrastructure/adapter/in/web/error/ManejadorGlobalErrores.java`, que traduce `FranjaInvalidaException` a 400 y `ServicioExternoNoDisponibleException` a 503 como `ProblemDetail`
- [ ] T012 Configurar `infrastructure/config/SeguridadConfig.java`: validación del JWT desde la cookie `httpOnly`, roles `ESTUDIANTE`, `MONITOR` (hereda de `ESTUDIANTE`, FR-005) y `DIRECCION_PROGRAMA`; y un resolvedor que entrega `UsuarioActual` a los controladores
- [ ] T013 Crear `V2__datos_local.sql`, que solo se aplica con el perfil local, con un usuario por rol, y un endpoint mínimo de inicio de sesión para poder probar la consulta. El autorregistro queda para su propio spec
- [ ] T014 [P] Crear `infrastructure/config/ClientesHttpConfig.java` con un `RestClient` por módulo externo, cada uno con su URL base y su timeout
- [ ] T015 [P] Crear `frontend/src/lib/api/cliente.ts`, que envía la cookie de sesión y convierte los `ProblemDetail` en errores tipados, y el layout del grupo `(app)`, que redirige al login si no hay sesión

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
- [ ] T022 [P] [US1] Pruebas `InventarioRestAdapterTest.java` y `CumplimientoRestAdapterTest.java` con WireMock: respuesta normal, catálogo en varias páginas que se recorren todas (UC8 FR-011), timeout y 5xx convertidos en `ServicioExternoNoDisponibleException`, y persona sin historial leída como "sin sanciones" (UC6 FR-008)
- [ ] T023 [P] [US1] Prueba `RecursoControllerTest.java` con `@WebMvcTest`: parámetros válidos, 400 por franja inválida, 401 sin sesión, 503 con el Módulo 1 caído y forma del JSON de respuesta

### Implementation for User Story 1

- [ ] T024 [P] [US1] Crear el modelo de recurso en `domain/model/recurso/`: `CategoriaRecurso`, `Recurso` (atributos de espacio y de activo según el spec), `EstadoOperativo`, `EstadoVisible` con su prioridad, `FiltroRecursos` y `RecursoConEstado`
- [ ] T025 [P] [US1] Crear `Ocupacion` y `TipoOcupacion` en `domain/model/reserva/`, y `ReporteDeCumplimiento` y `Sancion` en `domain/model/cumplimiento/`
- [ ] T026 [P] [US1] Definir los puertos de salida `InventarioPort`, `OcupacionRepositoryPort` y `CumplimientoPort` en `domain/port/out/`
- [ ] T027 [P] [US1] Definir los puertos de entrada `ConsultarRecursosPort`, `ConsultarDisponibilidadPort` (versión por lote, UC8 FR-008) y `ConsultarSancionesPort` en `domain/port/in/`
- [ ] T028 [US1] Implementar `ConsultarDisponibilidadUseCase`: un estado por recurso con su motivo, sin exponer al titular (UC8 FR-004, FR-005) y sin modificar nada (UC8 FR-009) (depende de T024 a T027)
- [ ] T029 [US1] Implementar `ConsultarSancionesUseCase` en modo lectura: obtiene el reporte de una sola persona y traduce la falta de respuesta a "no comprobado" (UC6 FR-001, FR-004, FR-006, FR-007)
- [ ] T030 [US1] Implementar `BuscadorSiguienteFranja` según la decisión de diseño (depende de T028)
- [ ] T031 [US1] Implementar `ConsultarRecursosUseCase`: catálogo del Módulo 1, filtro de aforo, orden estable, estado de todo el conjunto, paginación, mensaje cuando no hay seleccionables, sugerencia y aviso de sanción (depende de T028 a T030)
- [ ] T032 [P] [US1] Implementar `OcupacionPersistenceAdapter`, con las entidades JPA de lectura y la consulta nativa de ocupaciones en `OcupacionJpaRepository`
- [ ] T033 [P] [US1] Implementar `InventarioRestAdapter` y sus DTO contra el contrato pedido en P-16: recorre todas las páginas del Módulo 1 y convierte timeouts y errores en `ServicioExternoNoDisponibleException`
- [ ] T034 [P] [US1] Implementar `CumplimientoRestAdapter` y sus DTO
- [ ] T035 [P] [US1] Implementar `InventarioFakeAdapter` y `CumplimientoFakeAdapter` para el perfil local, con un catálogo de ejemplo que cubra espacios y activos de varios tipos
- [ ] T036 [US1] Registrar los casos de uso como beans, con `@Transactional(readOnly = true)`, en `infrastructure/config/CasosDeUsoConfig.java` (depende de T028 a T035)
- [ ] T037 [US1] Implementar `RecursoController` (`GET /api/recursos`), con `ConsultaRecursosParams` validados y `ConsultaRecursosResponse`, y documentarlo con OpenAPI
- [ ] T038 [P] [US1] Frontend: tipos y llamada en `frontend/src/features/recursos/tipos.ts` y `api.ts`
- [ ] T039 [US1] Frontend: `FiltrosRecursos.tsx` (fecha, franja limitada a 06:00–22:00, tipo y aforo mínimo solo para espacios), `ListaRecursos.tsx` (estado de cada recurso y selección solo de los `DISPONIBLE`), `Paginacion.tsx` ("20 de 250", anterior y siguiente) y `AvisoSancion.tsx`
- [ ] T040 [US1] Frontend: `frontend/src/app/(app)/recursos/page.tsx`, que arma la pantalla y muestra los estados vacío, "no hay disponibles" con la sugerencia, y "el inventario no está disponible en este momento" (FR-008)

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Mejoras que afectan a toda la funcionalidad

- [ ] T041 [P] Medir el tiempo de la consulta con 100 y 500 usuarios concurrentes contra un Módulo 1 simulado con la latencia prometida, y verificar SC-001
- [ ] T042 [P] Registrar en logs cada llamada a los Módulos 1 y 3, con su duración y su resultado, sin datos personales
- [ ] T043 [P] Actualizar el README con el perfil local (`./gradlew bootTestRun --args='--spring.profiles.active=local'`) y con cómo levantar el frontend
- [ ] T044 Revisar con el equipo las decisiones marcadas NEEDS CLARIFICATION de este plan y llevarlas a `pendientes-clarificacion.md` si siguen abiertas

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
- **NEEDS CLARIFICATION abiertos en este plan**: P-14 (se asume que la consulta falla sin el Módulo 1, como ya dice FR-008), P-16 (contrato real del Módulo 1), P-17 (timeouts), P-20 punto 3 (si el mantenimiento trae fechas) y P-10 (se asume que la consulta no se bloquea cuando el Módulo 3 falla).
