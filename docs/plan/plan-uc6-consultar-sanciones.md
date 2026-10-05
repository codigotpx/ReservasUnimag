# Implementation Plan: Consultar sanciones (UC6)

**Date**: 2026-10-02
**Spec**: [spec-modulo2-uc6-consultar-sanciones.md](../specs/spec-modulo2-uc6-consultar-sanciones.md)
**Plan general**: [plan-arquitectura.md](./plan-arquitectura.md) — incluida la [convención de nombres](./plan-arquitectura.md#convención-de-nombres)
**Planes previos**: [UC1](./plan-uc1-consultar-recursos.md) implementó la lectura del reporte y [UC2](./plan-uc2-reservar-recursos.md) la denegación `RES-003`

## Summary

UC6 le pregunta al Módulo 3 si una persona está sancionada. No tiene pantalla y **no debe tenerla** (FR-002): lo ejecuta el sistema por su cuenta, dentro de UC1 para avisar y dentro de UC2 para decidir. Lo único que hace es leer: no calcula, no decide y no guarda sanciones (FR-005, FR-010).

UC1 y UC2 ya lo implementaron entre los dos. Lo que este plan añade es **lo que ninguno de los dos hizo**:

1. **El registro de cada consulta** (FR-009). Es el requisito que queda sin cumplir: hoy la consulta deja un rastro en el log, y un log no sirve para responder "por qué se le denegó una reserva a esta persona el 3 de septiembre" seis meses después. La tabla `denial` de UC2 guarda la denegación, pero no lo que el Módulo 3 respondió.
2. **El borde de la vigencia** (edge case **La sanción vence en mitad de la franja pedida**), que el spec pide que sea siempre el mismo y que hoy no está enunciado en ningún plan.
3. **Las dos respuestas incompletas** que el spec prevé y que nadie trató: la sanción sin fecha de fin y el alcance que distingue espacios de activos.
4. **La prueba de que no hay pantalla** (FR-002) y de que nunca se pide el reporte de otra persona (FR-007, SC-005).

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 4.1.1 (RestClient), Flyway. **Ninguna nueva.**
**Storage**: PostgreSQL. UC6 **escribe** la tabla nueva `sanction_check` y **no guarda ninguna sanción** (FR-005).
**Testing**: JUnit 5 y AssertJ, WireMock (las respuestas del Módulo 3, incluidas las incompletas), Testcontainers con PostgreSQL (el registro), ArchUnit
**Target Platform**: Servidor Linux con JVM 21
**Performance Goals**: La consulta responde en **menos de 2 segundos** (SC-002), que es el presupuesto que UC2 le reserva dentro de su confirmación. El timeout del cliente se fija por debajo de eso.
**Constraints**:
- No se ofrece como pantalla de consulta (FR-002).
- No se calcula, decide ni almacena ninguna sanción (FR-005, FR-010).
- Se pide el reporte de **una sola** persona (FR-007, SC-005).
- Si el Módulo 3 no responde, no se da por buena la situación (FR-006, SC-004).
- Una persona sin historial es "sin sanciones", no un fallo (FR-008).
**Scale/Scope**: Una consulta por cada apertura de la pantalla de recursos y una por cada confirmación de reserva. Sin pantalla propia; un endpoint de auditoría.

## Project Structure

### Source Code (repository root)

```text
src/main/java/edu/unimagdalena/reservasunimag/
├── domain/
│   ├── model/compliance/
│   │   ├── ComplianceReport.java            # (existe, UC1)
│   │   ├── Sanction.java                    # (existe, UC1) + vigencia y alcance
│   │   ├── SanctionScope.java               # nuevo: ESPACIOS, ACTIVOS, TODO
│   │   └── SanctionCheck.java               # nuevo: el registro de FR-009
│   ├── port/
│   │   ├── in/CheckSanctionsPort.java       # (existe, UC1)
│   │   └── out/
│   │       ├── CompliancePort.java          # (existe, UC1)
│   │       └── SanctionCheckRepositoryPort.java
│   └── usecase/checksanctions/
│       └── CheckSanctionsUseCase.java       # (existe, UC1) se completa aquí
│
└── infrastructure/
    ├── adapter/
    │   ├── in/web/compliance/
    │   │   └── SanctionCheckController.java     # GET de auditoría, no de consulta
    │   └── out/
    │       ├── persistence/
    │       │   ├── entity/SanctionCheckJpa.java
    │       │   ├── repository/SanctionCheckJpaRepository.java
    │       │   └── SanctionCheckPersistenceAdapter.java
    │       └── module3/
    │           ├── ComplianceRestAdapter.java   # (existe, UC1) + alcance y sanción sin fin
    │           └── ComplianceFakeAdapter.java   # (existe, UC1)
    └── config/
        └── UseCasesConfig.java

src/main/resources/db/migration/
└── V10__sanction_check.sql

src/test/java/edu/unimagdalena/reservasunimag/
├── domain/model/compliance/SanctionValidityTest.java
├── domain/usecase/checksanctions/CheckSanctionsUseCaseTest.java
└── infrastructure/adapter/
    ├── in/web/compliance/SanctionCheckControllerTest.java
    ├── out/module3/ComplianceRestAdapterTest.java      # (existe, UC1) + los casos nuevos
    └── out/persistence/SanctionCheckIT.java
```

**Structure Decision**: aparece `adapter/in/web/compliance`, y conviene aclarar por qué no contradice FR-002: lo que se expone **no es la consulta de sanciones** sino el historial de las consultas que el sistema ya hizo, y solo para Dirección de Programa. Nadie puede preguntarle a este endpoint si una persona está sancionada; solo puede ver qué se preguntó y qué contestó el Módulo 3.

### Decisiones de diseño de este caso de uso

**Dónde quedó cada requisito.**

| FR | Qué pide | Dónde |
|---|---|---|
| FR-001 | Obtener el reporte del Módulo 3 | UC1 T034, `ComplianceRestAdapter` |
| FR-002 | Que lo ejecute el sistema, sin pantalla | UC1 T031 lo llama dentro de la consulta; UC2 T027 dentro de la confirmación |
| FR-003 | Consultar antes de confirmar, para `RES-003` | UC2 T027 |
| FR-004 | Obtener motivo y fecha de fin, y trasladárselos | UC1 (el aviso) y UC2 (el `409`) |
| FR-005 | No calcular ni almacenar sanciones | UC1 T029: el reporte no se persiste |
| FR-006 | No dar por buena una situación no comprobada | UC1 (aviso `NOT_VERIFIED`) y UC2 (`503`) |
| FR-007 | Una sola persona, sin exponer a terceros | UC1 T029, el `code` del JWT |
| FR-008 | Sin historial es "sin sanciones" | UC1 T022, el `404` leído como reporte vacío |
| **FR-009** | **Registro de cada consulta y su resultado** | **Nada. Es lo que hace este plan** |
| FR-010 | Solo lee | UC1 T034 |

**El registro de FR-009, y por qué un log no basta.** El requisito dice "para poder auditar por qué se denegó una reserva". Con lo que hay hoy, esa auditoría es imposible de cerrar: la tabla `denial` de UC2 dice que hubo un `RES-003`, pero no qué respondió el Módulo 3 —qué sanción, con qué motivo, hasta cuándo—, y el log se rota. Si alguien reclama que se le denegó injustamente, no hay con qué contestarle.

Así que se guarda una fila por consulta, con lo que el Módulo 3 contestó. Tres cosas que **no** son esta tabla, para que no se confunda con ellas:

| | Qué guarda |
|---|---|
| `sanction_check` (este plan) | Que preguntamos, cuándo, por quién y qué nos contestaron |
| `denial` (UC2) | Que una reserva se denegó, con su código |
| Una tabla de sanciones | **No existe y no debe existir** (FR-005). El Módulo 3 es la única fuente |

La diferencia con una tabla de sanciones es sutil pero decisiva: aquí no se guarda *que la persona está sancionada*, se guarda *que el 3 de septiembre a las 09:14 el Módulo 3 nos dijo que lo estaba*. Lo primero sería una copia que se desactualiza y que FR-005 prohíbe; lo segundo es un hecho histórico que no cambia. Por eso las columnas se llaman `answered_*` y no `sanction_*`.

**El registro no bloquea la consulta.** Se escribe en su propia transacción (`REQUIRES_NEW`), por la misma razón que las denegaciones de UC2: cuando UC2 deniega con `RES-003`, su transacción se deshace, y el registro de la consulta tiene que sobrevivir a ese *rollback*. Y si escribir el registro falla, la consulta **no** falla: se registra el problema y la decisión sigue adelante. Auditar es importante, pero no tanto como poder reservar.

**El borde de la vigencia, enunciado de una vez** (edge case **La sanción vence en mitad de la franja pedida**). El spec es claro en el fondo —"si está sancionado se le debe impedir hacer reservas"— y pide que el criterio sea siempre el mismo. Dicho con precisión:

```text
la sanción está vigente cuando   startsOn <= hoy <= endsOn
                   donde hoy =   la fecha de HOY en America/Bogota
```

Y las dos consecuencias que importan:

- **Se compara contra el momento de consultar, no contra la franja pedida.** Alguien sancionado hasta el 15 que quiere reservar el 20 **no puede**: la comprobación es sobre la persona ahora, no sobre la fecha de la reserva. Es lo que dice el edge case y es lo que hace que la regla sea simple de explicar.
- **El último día cuenta completo.** Una sanción que termina el 15 bloquea todo el día 15 y deja de bloquear el 16 (escenario 3: la sanción que terminó el 30 de agosto ya no afecta al 3 de septiembre, "sin que nadie tenga que levantarla a mano").

Se compara por **fecha** y no por instante, porque el Módulo 3 nos manda fechas (`2026-09-15`) y no horas. Si algún día mandara instantes, el borde habría que decidirlo otra vez.

**Una sanción sin fecha de fin se trata como vigente** (edge case **Respuesta del Módulo 3 incompleta**). No se inventa la fecha, no se descarta la sanción y no se falla: se considera vigente, se registra que faltó el dato y el mensaje que ve la persona omite el "hasta" en vez de poner un texto vacío. Las tres alternativas eran peores: inventar una fecha es mentir, descartarla deja reservar a alguien sancionado y fallar convierte un dato incompleto del otro módulo en una caída del nuestro.

**El alcance se lee y todavía no filtra** (edge case **Sanciones que no aplican a lo que se está pidiendo**). El spec dice que hay castigos que solo bloquean espacios y otros solo activos. El campo `scope` se lee del reporte y se guarda en el registro, pero **hoy cualquier sanción vigente bloquea cualquier reserva**, que es la opción conservadora. Cuando P-11 se cierre, el cambio es una comparación en `CheckSanctionsUseCase` y la tabla ya tendrá el dato histórico para saber qué se hizo antes. (NEEDS CLARIFICATION: P-11.)

**Dos usos, dos políticas ante una caída.** Es la decisión más visible de UC6 y está repartida entre dos planes, así que aquí se junta:

| Quién consulta | Si el Módulo 3 no responde | Por qué |
|---|---|---|
| UC1, al listar recursos | Aviso `NOT_VERIFIED` y la lista se muestra | Mirar no compromete nada, y la comprobación que decide es la de confirmar |
| UC2, al confirmar | `503` y la reserva **no** se crea | Confirmar sin comprobar es dar por buena una situación que no se comprobó (FR-006, SC-004) |

Las dos cumplen FR-006: ninguna da por buena la situación. Lo que cambia es la consecuencia, porque lo que está en juego es distinto.

**Lo que este plan no hace: cachear el reporte.** Sería tentador —UC1 y UC2 consultan lo mismo en segundos de diferencia— y está descartado por el edge case **Sanción que aparece justo después de consultar**: la comprobación que vale es la del momento de confirmar. Una caché de incluso treinta segundos haría posible confirmar una reserva a alguien a quien acaban de sancionar.

## Contratos

Se aplican las **convenciones comunes** de [UC1 § Contratos](./plan-uc1-consultar-recursos.md#contratos).

---

### 1. La petición al Módulo 3

**No se redefine aquí.** El contrato está en [UC1 § Contratos §3](./plan-uc1-consultar-recursos.md#3-módulo-3--complianceport) y lo que cambia según quién pregunta está en [UC2 § Contratos §5](./plan-uc2-reservar-recursos.md#5-módulo-3--sanciones-al-confirmar).

Lo que este plan añade al contrato son los **dos campos que el spec prevé y que UC1 no detalló**:

```json
{
  "person": { "code": "2019114045" },
  "activeSanctions": [
    {
      "reason": "Dos ausencias no justificadas en el semestre",
      "startsOn": "2026-09-01",
      "endsOn": "2026-09-15",
      "scope": "ESPACIOS"
    }
  ],
  "absenceCount": 2,
  "lateReturns": 1,
  "generatedAt": "2026-09-01T09:14:21-05:00"
}
```

| Campo | Qué hacemos con él |
|---|---|
| `startsOn` | Entra en la comparación de vigencia. Si falta, se asume que empezó (una sanción que llega en la lista de vigentes, lo está). |
| `endsOn` | **Si falta, la sanción se trata como vigente** y se registra `end_date_missing: true`. |
| `scope` | `ESPACIOS`, `ACTIVOS` o `TODO`. Se lee y se registra; **hoy no filtra** (P-11). Un valor desconocido se trata como `TODO`. |
| `absenceCount`, `lateReturns` | Se registran para la auditoría. Ni UC1 ni UC2 los muestran. |

De las vigentes se toma la de `endsOn` más lejano para el mensaje; si alguna no tiene `endsOn`, esa manda.

---

### 2. La tabla `sanction_check`

Lo que pide FR-009.

| Columna | Tipo | Nota |
|---|---|---|
| id | uuid PK | |
| person_code | varchar | El código institucional por el que se preguntó |
| asked_by | varchar | `SEARCH_RESOURCES` (UC1) o `RESERVE_RESOURCES` (UC2): para qué se preguntó |
| asked_at | timestamptz | |
| outcome | varchar | `CLEAR`, `SANCTIONED`, `NO_HISTORY`, `NOT_VERIFIED` |
| answered_sanction_reason | varchar, nulo | **Lo que el Módulo 3 contestó**, no lo que creemos |
| answered_ends_on | date, nulo | |
| answered_scope | varchar, nulo | |
| end_date_missing | boolean | El edge case de la respuesta incompleta |
| answered_absence_count | int, nulo | |
| answered_late_returns | int, nulo | |
| duration_ms | int | Para vigilar SC-002 |
| failure_detail | varchar, nulo | Solo con `NOT_VERIFIED` |

```sql
CREATE INDEX sanction_check_person ON sanction_check (person_code, asked_at);
CREATE INDEX sanction_check_outcome ON sanction_check (outcome, asked_at);
```

Los cuatro `outcome` son exhaustivos y cubren los cuatro escenarios del spec: `CLEAR` (escenario 1), `SANCTIONED` (escenario 2), `NO_HISTORY` (escenario 4) y `NOT_VERIFIED` (el edge case del Módulo 3 caído). El escenario 3 —la sanción que ya venció— es `CLEAR`, porque para nosotros una sanción vencida no es una sanción.

**No hay columna que diga "está sancionado".** El `outcome` dice qué nos contestaron, y los campos `answered_*` lo detallan. Es la diferencia con una tabla de sanciones que FR-005 prohíbe.

---

### 3. `GET /api/sanction-checks`

Auditoría, **solo** `DIRECCION_PROGRAMA`. No es una pantalla de consulta de sanciones: no responde si alguien está sancionado hoy, solo qué se preguntó y qué contestaron (FR-002).

```http
GET /api/sanction-checks?personCode=2019114045&outcome=SANCTIONED&from=2026-09-01&to=2026-09-30&page=1
```

```json
{
  "checks": [
    {
      "id": "e4a9c712-8b35-4d06-91fe-2c7d0b8a3f54",
      "personCode": "2019114045",
      "askedBy": "RESERVE_RESOURCES",
      "askedAt": "2026-09-03T09:14:22-05:00",
      "outcome": "SANCTIONED",
      "answered": {
        "sanctionReason": "Dos ausencias no justificadas en el semestre",
        "endsOn": "2026-09-15",
        "scope": "ESPACIOS",
        "endDateMissing": false,
        "absenceCount": 2,
        "lateReturns": 1
      },
      "durationMs": 312
    },
    {
      "id": "a7f2e508-3c91-4b47-86da-0e5b1d9c2f68",
      "personCode": "2019114045",
      "askedBy": "SEARCH_RESOURCES",
      "askedAt": "2026-09-03T09:14:05-05:00",
      "outcome": "NOT_VERIFIED",
      "failureDetail": "Connection refused",
      "durationMs": 2000
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "total": 2 },
  "summary": { "total": 2, "clear": 0, "sanctioned": 1, "noHistory": 0, "notVerified": 1 }
}
```

`summary.notVerified` es el número que vigila SC-004: cuántas veces no pudimos comprobar. Si crece, el problema está en el Módulo 3 o en el timeout, y conviene verlo sin abrir los logs.

**El objeto `answered` se omite entero** cuando el `outcome` es `CLEAR`, `NO_HISTORY` o `NOT_VERIFIED`: no hay nada que el Módulo 3 haya contestado sobre una sanción.

**Decisión: este endpoint no permite filtrar solo por `personCode` sin rango de fechas.** Pedir el historial completo de una persona sin acotar es, en la práctica, pedir su expediente, y este caso de uso existe para auditar denegaciones concretas. `from` y `to` son obligatorios cuando se pasa `personCode`, y el rango máximo es de un semestre. (Es una decisión de este plan; el spec no habla del endpoint porque no lo previó.)

**Errores**: `403` si el rol no es Dirección de Programa; `400` si se pasa `personCode` sin rango o con un rango mayor de un semestre.

---

### 4. Fixtures compartidos

```text
src/test/resources/contratos/
├── module3-report-sanctioned-no-end-date.json   # el edge case de la respuesta incompleta
├── module3-report-scope-assets.json             # el alcance que todavía no filtra
├── module3-report-expired-sanction.json         # el escenario 3
└── api-sanction-checks.json
```

Los de la persona al día, la sancionada y la sin historial ya los creó UC1.

---

## Phase 1: Setup

- [ ] T001 Fijar `reservations.module3.timeout=1500ms` en `application.properties`, por debajo de los 2 s de SC-002, y documentar que ese presupuesto es el que UC2 reserva dentro de su confirmación

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T002 Escribir `V10__sanction_check.sql` con la tabla de [Contratos §2](#2-la-tabla-sanction_check) y sus dos índices
- [ ] T003 [P] Crear `SanctionScope` y `SanctionCheck` en `domain/model/compliance/`, y extender `Sanction` con el método de vigencia del borde enunciado arriba
- [ ] T004 [P] Definir `SanctionCheckRepositoryPort` en `domain/port/out/`, y crear la entidad JPA, el repositorio y el adaptador con su transacción propia (`REQUIRES_NEW`)

**Checkpoint**: existe dónde registrar cada consulta

---

## Phase 3: User Story 1 - Saber si la persona puede reservar (Priority: P1)

**Goal**: El sistema obtiene del Módulo 3 la situación de una sola persona, con el motivo y la fecha de fin de su sanción; no la calcula ni la guarda; no da por buena una situación que no pudo comprobar; y cada consulta queda registrada con lo que el Módulo 3 contestó.

**Independent Test**: Con el perfil local y el adaptador falso, recorrer los cuatro escenarios —al día, sancionada, sanción vencida y sin historial— y comprobar el `outcome` de cada uno en `GET /api/sanction-checks`; tirar el Módulo 3 y comprobar que UC1 sigue listando con aviso y UC2 responde `503`, y que las dos quedan registradas como `NOT_VERIFIED`.

### Tests for User Story 1

- [ ] T005 [P] [US1] Pruebas en `SanctionValidityTest.java` del borde: vigente el último día (`endsOn` = hoy), no vigente al día siguiente (escenario 3), vigente sin `endsOn` (edge case **Respuesta incompleta**), y que la comparación es contra **hoy** y no contra la fecha de la reserva (edge case **La sanción vence en mitad de la franja pedida**): alguien sancionado hasta el 15 no puede reservar para el 20
- [ ] T006 [P] [US1] Pruebas en `CheckSanctionsUseCaseTest.java`: los cuatro escenarios producen su `outcome`; de varias vigentes se toma la de fin más lejano; una sin `endsOn` manda sobre las demás; el `scope` se lee y **no** filtra todavía (P-11); y un `scope` desconocido se trata como `TODO`
- [ ] T007 [P] [US1] Pruebas en `ComplianceRestAdapterTest.java` con WireMock, contra los *fixtures* nuevos: la sanción sin `endsOn` deja `end_date_missing`; el `scope` se mapea; el `404` y el `200` vacío son los dos "sin sanciones" (FR-008); y un `5xx` o un timeout dan `NOT_VERIFIED` sin inventar nada
- [ ] T008 [P] [US1] Prueba de FR-007 y SC-005: el `code` que se le manda al Módulo 3 sale **siempre** del claim del JWT y nunca de un parámetro de la petición; y una respuesta cuyo `person.code` no coincide con el que se pidió se rechaza en vez de usarse
- [ ] T009 [P] [US1] Prueba `SanctionCheckIT.java` con Testcontainers: la consulta queda registrada con lo que contestó el Módulo 3; el registro **sobrevive** al *rollback* de la transacción de UC2 que deniega con `RES-003`; y si el `INSERT` del registro falla, la consulta **no** falla
- [ ] T010 [P] [US1] Prueba de FR-005 y FR-010: después de consultar, **ninguna** tabla contiene una sanción —solo el registro de lo contestado— y no se hizo ninguna escritura hacia el Módulo 3
- [ ] T011 [P] [US1] Prueba de FR-002: no existe ningún endpoint que, dado un código de persona, responda si está sancionada hoy; el único expuesto es el de auditoría y exige rol `DIRECCION_PROGRAMA`. Se escribe recorriendo las rutas registradas
- [ ] T012 [P] [US1] Prueba `SanctionCheckControllerTest.java` contra el *fixture*: el listado con `answered` presente solo en `SANCTIONED`, el `summary`, el `403` por rol, y el `400` de `personCode` sin rango y de un rango mayor de un semestre
- [ ] T013 [P] [US1] Prueba de que **no hay caché** (edge case **Sanción que aparece justo después de consultar**): dos consultas seguidas de la misma persona llaman dos veces al Módulo 3, y si entre ellas aparece una sanción, la segunda la ve

### Implementation for User Story 1

- [ ] T014 [US1] Completar `CheckSanctionsUseCase`: usa la vigencia de `Sanction`, construye el `SanctionCheck` con el `askedBy` que le pasa quien llama, y lo guarda por `SanctionCheckRepositoryPort` sin dejar que un fallo del registro tumbe la consulta (depende de T003, T004)
- [ ] T015 [US1] Extender `ComplianceRestAdapter` con `scope` y con la sanción sin `endsOn`, y `ComplianceFakeAdapter` con los tres casos nuevos para el perfil local
- [ ] T016 [US1] Pasar el `askedBy` desde `SearchResourcesUseCase` y `ReserveResourcesUseCase`, que son los dos únicos que pueden llamar a este caso de uso
- [ ] T017 [US1] Implementar `SanctionCheckController` según [Contratos §3](#3-get-apisanction-checks), con la validación del rango obligatorio
- [ ] T018 [US1] Registrar el bean y su transacción propia en `UseCasesConfig`

**Checkpoint**: UC6 queda cerrado, FR-009 incluido

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T019 [P] Verificar SC-002 con el Módulo 3 simulado a su latencia prometida, y comprobar que el `durationMs` registrado permite detectar cuándo se pasa de 2 s
- [ ] T020 [P] Verificar SC-001 y SC-003 de punta a punta: toda denegación por sanción indica motivo y fecha de fin, y ninguna reserva se confirma con sanción vigente
- [ ] T021 [P] Registrar en logs cada consulta con su resultado y su duración, **sin** el motivo de la sanción, que es un dato personal: ese queda en la tabla, que exige rol para leerse
- [ ] T022 Llevarle al Módulo 3 los dos campos de [Contratos §1](#1-la-petición-al-módulo-3) —`scope` y qué hacer con una sanción sin `endsOn`— y llevar P-11 a `pendientes-clarificacion.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: depende de UC1 (el adaptador y la lectura) y de UC2 (la denegación)
- **Foundational (Phase 2)**: depende de Setup - BLOCKS la user story
- **User Story 1 (Phase 3)**: depende de Foundational
- **Polish (Phase 4)**: depende de la Phase 3

### Dependencias con otros casos de uso

- **UC1 `Consultar recursos`**: implementó el adaptador del Módulo 3 y la lectura del reporte, y es uno de los dos que pueden llamar a este caso de uso. Su política ante una caída es el aviso.
- **UC2 `Reservar recursos`**: implementó la denegación `RES-003` y es el otro que puede llamarlo. Su política ante una caída es el `503`. La tabla `denial` que creó guarda la denegación; la de este plan guarda lo que el Módulo 3 contestó, y entre las dos se cierra la auditoría que FR-009 pide.
- **UC9 `Recibir reporte de no asistencia`** y **UC11 `Reportar cancelación de reserva`**: son el camino de vuelta. Lo que ellos le reportan al Módulo 3 es lo que después aparece aquí como sanción, y es la razón de que este caso de uso no decida nada (FR-005).
- **Módulo 3**: la única fuente de verdad de las sanciones. No guardamos ni una.

### Within User Story 1

- Esquema (T002) → modelo (T003) → persistencia (T004) → caso de uso (T014) → adaptador (T015) → los dos invocadores (T016)
- Controlador (T017) y beans (T018) al final

### Parallel Opportunities

- En Foundational: T003 y T004
- En User Story 1: todas las pruebas (T005 a T013)
- En Polish: T019 a T021

## Notes

- La numeración `T0XX` es propia de este plan
- [P] tasks = different files, no dependencies
- Verify tests pass
- Commit after each task or logical group
- **De los diez FR, nueve ya estaban implementados por UC1 y UC2.** Lo que este plan construye es FR-009, el registro de cada consulta, más el borde de la vigencia, las dos respuestas incompletas y las pruebas de que no hay pantalla ni caché
- **Decisiones de los contratos que el spec no fija**: una sanción sin fecha de fin se trata como **vigente** y se marca el dato que faltó; la vigencia se compara contra hoy y no contra la fecha de la reserva; el registro va en su propia transacción y un fallo suyo no tumba la consulta; las columnas se llaman `answered_*` para que no se confundan con una copia de las sanciones, que FR-005 prohíbe; y el endpoint de auditoría exige un rango de fechas junto al `personCode`, porque sin él sería pedir el expediente de una persona
- **Decisión explícita de no hacer**: ninguna caché del reporte, por el edge case de la sanción que aparece justo después de consultar
- **NEEDS CLARIFICATION abiertos en este plan**:
  - **P-11**: el alcance de la sanción. Se lee y se registra, pero hoy cualquier sanción vigente bloquea cualquier reserva
  - **P-10**: la política ante la caída del Módulo 3. Las dos ramas están implementadas —aviso al listar, bloqueo al confirmar— y es la lectura conservadora del spec
  - **Formato de la vigencia**: el Módulo 3 nos manda fechas, no instantes. Si algún día manda instantes, el borde hay que decidirlo otra vez
  - **Umbral de ausencias que origina sanción**: lo deja abierto `spec-modulo2.md`, y no afecta a este plan porque la decisión es del Módulo 3
