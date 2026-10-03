# Implementation Plan: Actualizar estado de los recursos (UC7)

**Date**: 2026-10-02
**Spec**: [spec-modulo2-uc7-actualizar-estado-recursos.md](../specs/spec-modulo2-uc7-actualizar-estado-recursos.md)
**Plan general**: [plan-arquitectura.md](./plan-arquitectura.md) — incluida la [convención de nombres](./plan-arquitectura.md#convención-de-nombres)
**Planes previos**: lo invocan [UC2](./plan-uc2-reservar-recursos.md), [UC3](./plan-uc3-importar-horarios-semestrales.md), [UC4](./plan-uc4-cancelar-reserva.md), [UC9](./plan-uc9-recibir-reporte-no-asistencia.md) y [UC12](./plan-uc12-recibir-check-out.md), y cada uno de esos planes ya implementó su parte

## Summary

UC7 es el `<<include>>` más invocado del módulo y el único que **no se puede terminar hoy**. Conviene decir por qué desde el principio, porque este plan es distinto de los demás.

El spec de UC7 se escribió antes del reparto de estados del 2026-09-14. En su versión actual pide dos cosas: **mantener coherente el estado de cada recurso en el tiempo** (FR-001 a FR-003, FR-005, FR-006, FR-010, FR-011) y **avisarle al Módulo 1 de cada cambio** (FR-004, FR-007, FR-008). Después del reparto, la primera mitad sigue siendo nuestra y la segunda se quedó sin destinatario: el Módulo 1 es dueño de `DISPONIBLE`, `EN_USO` y `EN_MANTENIMIENTO` y nosotros de `RESERVADO` y `BLOQUEO_ACADEMICO`, así que no hay ningún estado nuestro que él necesite recibir. Eso es P-20 punto 1, y sigue abierto.

**Enfoque técnico:**

1. **La primera mitad ya está hecha**, repartida entre cinco planes, y no por casualidad: el estado de un recurso en el Módulo 2 **no es una columna**, es la consecuencia de qué ocupaciones hay escritas. Este plan lo documenta de una vez y añade las pruebas transversales que ningún plan individual podía escribir.
2. Lo que falta y **sí se puede hacer** es el registro de cambios de FR-009, que la entidad `StatusChange` pide y que el plan general dejó explícitamente para cuando se respondiera P-20. Se puede construir sin el Módulo 1, porque es una tabla nuestra.
3. Lo que falta y **no se puede hacer** es el aviso de FR-004. Se deja el puerto diseñado y las dos ramas posibles descritas, detrás de un interruptor apagado, para que el día que P-20 se cierre sea escribir un adaptador y no rediseñar nada.
4. Dos requisitos que parecen trabajo y no lo son: FR-010 —un espacio vuelve a `DISPONIBLE` solo— y FR-011 —un activo no vuelve solo— son **consecuencias del modelo de datos**, no tareas. Merecen una prueba, no un `@Scheduled`.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 4.1.1, Spring for Apache Kafka, Flyway. **Ninguna nueva.**
**Storage**: PostgreSQL. UC7 **escribe** la tabla nueva `status_change` y no escribe nada más: las ocupaciones las escriben los casos de uso que lo invocan.
**Testing**: JUnit 5 y AssertJ, Testcontainers con PostgreSQL (coherencia bajo concurrencia, que es SC-003), ArchUnit. Las pruebas de este plan son casi todas **transversales**: comprueban invariantes que cruzan varios casos de uso.
**Target Platform**: Servidor Linux con JVM 21
**Performance Goals**: Un recurso liberado vuelve a aparecer como disponible en menos de 5 s (SC-002). Se cumple sin esfuerzo porque no hay nada que propagar: UC1 lee el estado en vivo.
**Constraints**:
- Un recurso **nunca** en dos estados para el mismo momento (FR-006, SC-003).
- Una reserva o una cancelación **no** saca a un recurso de `EN_MANTENIMIENTO` (FR-005).
- Ninguna operación de negocio falla por un error al avisar al Módulo 1 (SC-005, FR-007).
- Un espacio se libera al terminar su franja; un activo solo con su check-out (FR-010, FR-011).
**Scale/Scope**: Sin pantalla y sin endpoint de negocio. Un endpoint de auditoría para Dirección de Programa.

## Project Structure

### Source Code (repository root)

```text
src/main/java/edu/unimagdalena/reservasunimag/
├── domain/
│   ├── model/resource/
│   │   ├── StatusChange.java                   # nuevo: el registro de FR-009
│   │   └── StatusChangeReason.java             # nuevo: por qué cambió
│   ├── port/
│   │   ├── in/UpdateResourceStatusPort.java    # (existe, de UC2) se completa aquí
│   │   └── out/
│   │       ├── StatusChangeRepositoryPort.java
│   │       └── InventoryStatusNotifierPort.java    # FR-004: diseñado, sin adaptador real
│   └── usecase/updatestatus/
│       └── UpdateResourceStatusUseCase.java    # (existe, de UC2) se completa aquí
│
└── infrastructure/
    ├── adapter/
    │   ├── in/web/resource/
    │   │   └── StatusChangeController.java     # GET de auditoría
    │   └── out/
    │       ├── persistence/
    │       │   ├── entity/StatusChangeJpa.java
    │       │   ├── repository/StatusChangeJpaRepository.java
    │       │   └── StatusChangePersistenceAdapter.java
    │       └── module1/
    │           └── InventoryStatusNoOpAdapter.java   # no hace nada, y lo dice en el log
    └── config/
        └── UseCasesConfig.java

src/main/resources/db/migration/
└── V9__status_change.sql

src/test/java/edu/unimagdalena/reservasunimag/
├── domain/usecase/updatestatus/UpdateResourceStatusUseCaseTest.java
└── infrastructure/adapter/
    ├── in/web/resource/StatusChangeControllerTest.java
    └── out/persistence/
        ├── StatusChangeIT.java
        └── ResourceStatusCoherenceIT.java      # las pruebas transversales de SC-003
```

**Structure Decision**: `InventoryStatusNoOpAdapter` es un adaptador que **no hace nada**. Existe en lugar de dejar el puerto sin implementación, por tres razones: el contexto de Spring arranca sin perfiles especiales, el log deja constancia de cada aviso que *se habría* enviado —lo que sirve para dimensionar el volumen antes de implementarlo— y el día que P-20 se cierre, el cambio es registrar otro bean. No es un adaptador falso de pruebas: es el comportamiento correcto mientras la decisión no exista.

### Decisiones de diseño de este caso de uso

**El estado de un recurso no se guarda: se deduce.** Es la decisión que ya tomaron los planes anteriores sin enunciarla, y de la que dependen casi todos los requisitos de UC7:

| Lo que UC7 llama | Lo que realmente hay |
|---|---|
| El recurso pasa a `RESERVADO` | Una fila `CONFIRMADA` en `reservation` cuyo rango `occupancy` cubre esa franja |
| El recurso pasa a `BLOQUEO_ACADEMICO` | La misma fila, con `origin = 'ACADEMICO'` |
| El recurso pasa a `EN_USO` | Un préstamo entregado y sin devolver, o el `EN_USO` que informa el Módulo 1 |
| El recurso vuelve a `DISPONIBLE` | **Nada**: desaparece la ocupación, o la franja consultada queda fuera de su rango |
| El recurso está `EN_MANTENIMIENTO` | Lo dice el Módulo 1; nosotros no lo guardamos |

De ahí salen tres requisitos sin escribir una línea:

- **FR-006 y SC-003** —un recurso nunca en dos estados para el mismo momento— los garantiza la restricción `reservation_no_overlap`, que impide dos ocupaciones solapadas confirmadas, más la tabla de prioridad de UC1, que convierte en **una sola** etiqueta las que se pueden dar a la vez (un mantenimiento sobre una reserva, por ejemplo). No hay forma de que dos estados coexistan, porque solo hay una fuente para cada uno.
- **FR-010** —un espacio vuelve a `DISPONIBLE` al terminar su franja, salvo que haya otra reserva encima— es lo que hace un rango `[starts_at, ends_at)` por sí solo. Una consulta de las 14:00 ya no cruza una franja que acabó a las 12:00, y si hay otra reserva encima, esa sí cruza y manda. **No hay ninguna tarea programada** que libere franjas, y no debe haberla.
- **FR-011** —un activo no vuelve solo— es lo que hace la consulta de ocupaciones de UC1: un préstamo con `picked_up_at` y sin `returned_at` ocupa *desde su inicio y sin fin*, no hasta su vencimiento. Esa cláusula, que parece un detalle de UC1, es justamente FR-011.

**FR-005: una reserva no saca a nadie de mantenimiento.** Sale gratis por la misma razón: el mantenimiento es del Módulo 1 y nosotros no lo escribimos, así que no hay operación nuestra que pueda apagarlo. Lo que sí hay que probar es que la **prioridad** se respeta: un recurso en mantenimiento con una reserva confirmada encima se muestra `EN_MANTENIMIENTO` y no `RESERVADO` (la tabla de UC1). Eso es T011.

**FR-003 ya no se puede cumplir literalmente.** Dice "usar solo los cinco estados del inventario", y después del reparto los cinco ya no son del inventario: tres son del Módulo 1 y dos nuestros. Se cumple en lo que queda de su intención —**no inventar un sexto estado visible**— y eso es lo que hace `DisplayedStatus` de UC1, con exactamente cinco valores. (NEEDS CLARIFICATION: la redacción de FR-003 hay que corregirla en el spec; es parte de lo que P-20 punto 4 deja pendiente.)

**El registro de cambios sí se puede hacer, y es lo único nuevo de este plan** (FR-009). La entidad `StatusChange` pide recurso, franja, estado anterior, estado nuevo, motivo, instante y resultado del aviso. Todo eso lo tenemos sin el Módulo 1, salvo la última columna, que queda en `NOT_SENT` mientras no haya a quién avisar.

Dónde se escribe: en `UpdateResourceStatusUseCase`, que ya existe desde UC2 y que hoy no hace nada más que existir para que el `<<include>>` sea real. Los cinco casos de uso que lo invocan le pasan el motivo, y el registro se escribe **en su misma transacción**, porque un cambio de estado que no ocurrió no debe quedar registrado y uno que ocurrió no puede faltar.

| Quién invoca | Motivo | Estado nuevo |
|---|---|---|
| UC2, al confirmar | `RESERVATION_CONFIRMED` | `RESERVADO` |
| UC3, al crear un bloqueo | `ACADEMIC_BLOCK_CREATED` | `BLOQUEO_ACADEMICO` |
| UC4, al cancelar | `RESERVATION_CANCELLED` | `DISPONIBLE` |
| UC9, al registrar una ausencia | `NO_SHOW_REGISTERED` | `DISPONIBLE` |
| UC12, al cerrar un préstamo | `CHECK_OUT_RECEIVED` | `DISPONIBLE` |
| UC12, al declarar una pérdida | `LOAN_DECLARED_LOST` | `DISPONIBLE` |

**Decisión: el "estado anterior" se guarda, pero no se calcula consultando el Módulo 1.** Guardar el estado anterior obligaría, en rigor, a saber qué veía un estudiante en esa franja antes del cambio, lo que incluye el estado operativo del Módulo 1 y una llamada HTTP dentro de la transacción. No se hace: se guarda el estado anterior **según los datos del Módulo 2** —`RESERVADO`, `BLOQUEO_ACADEMICO` o `DISPONIBLE`— y la columna se llama `previous_status_module2` para que nadie la lea como la etiqueta completa. Un registro de auditoría no justifica meter una llamada a otro sistema en la transacción de una reserva.

**El aviso al Módulo 1: las dos ramas, ninguna activa** (FR-004, FR-007, FR-008). El puerto `InventoryStatusNotifierPort` se define ahora para no tener que tocar los casos de uso después. Lo que no se decide aquí es qué va detrás, porque depende de P-20:

| Si P-20 resuelve que… | Lo que hay que escribir | Lo que ya está listo |
|---|---|---|
| **El Módulo 2 no le avisa nada** (lo que dice hoy el reparto) | Nada. `InventoryStatusNoOpAdapter` se queda | Todo |
| **Hay que avisarle** | Un adaptador sobre la *outbox* —no una llamada síncrona—, y el contrato del aviso con ellos | El puerto, el registro, la columna de resultado y las pruebas de FR-008 |

**Si hay que avisar, va por la *outbox*, no por REST síncrono.** Es lo que exigen FR-007 y SC-005: la operación de negocio tiene que completarse aunque el Módulo 1 esté caído, y el aviso reintentarse hasta entregarse. Eso es exactamente lo que la *outbox* de UC2 ya hace para el Módulo 3, y no hay razón para construir un segundo mecanismo. FR-008 —repetir el aviso no produce un segundo cambio— lo cumple el `eventId`, igual que en UC10.

**Lo que este plan no hace: ninguna tarea programada.** Vale la pena decirlo explícitamente porque es la tentación obvia al leer FR-001 ("cada vez que una reserva... empieza a usarse o termina") y FR-010. Un `@Scheduled` que recorriera las reservas para "ponerlas en `EN_USO`" o "liberarlas al terminar" sería código que puede desincronizarse, que hay que vigilar y que no aporta nada: el rango ya dice la verdad en cada consulta. El único `@Scheduled` del módulo relacionado con esto es el publicador de la *outbox*, y el del umbral de pérdida de UC12.

Queda un hueco honesto: **`EN_USO` cuando alguien se presenta**. FR-001 dice que el estado cambia cuando la reserva "empieza a usarse", y UC2 dice que un espacio pasa a `EN_USO` cuando la persona se presenta. Nadie nos informa de que se presentó: eso es P-02, y sigue abierto. Hoy un espacio reservado se muestra `RESERVADO` toda su franja, y `EN_USO` solo aparece si el Módulo 1 lo informa o si hay un préstamo entregado. Es lo que ya decidió el plan de UC1 en su tabla de prioridad, y aquí se documenta como el hueco que es.

## Contratos

Se aplican las **convenciones comunes** de [UC1 § Contratos](./plan-uc1-consultar-recursos.md#contratos). UC7 no tiene API de negocio: lo que sigue es el registro y su consulta de auditoría.

---

### 1. La tabla `status_change`

La entidad `StatusChange` de la spec, que el plan general dejó pendiente.

| Columna | Tipo | Nota |
|---|---|---|
| id | uuid PK | |
| resource_id | varchar | Del Módulo 1 |
| resource_category | varchar | `ESPACIO`, `ACTIVO` |
| reservation_id | uuid FK, nulo | Nulo si el cambio no viene de una reserva |
| occupancy_starts_at | timestamptz | La franja, o el periodo del préstamo |
| occupancy_ends_at | timestamptz | |
| previous_status_module2 | varchar | `DISPONIBLE`, `RESERVADO`, `BLOQUEO_ACADEMICO`; **no** incluye lo que sabe el Módulo 1 |
| new_status | varchar | Uno de los cinco de `DisplayedStatus` |
| reason | varchar | Los siete motivos de la tabla de decisiones |
| changed_at | timestamptz | |
| notified_status | varchar | `NOT_SENT`, `PENDING`, `SENT`, `FAILED`. Hoy siempre `NOT_SENT` |

```sql
CREATE INDEX status_change_resource ON status_change (resource_id, changed_at);
CREATE INDEX status_change_reservation ON status_change (reservation_id);
```

**`notified_status` arranca en `NOT_SENT` y no en `PENDING`.** La diferencia importa: `PENDING` significaría que hay un aviso esperando a salir, y hoy no hay ninguno porque no hay destinatario. `NOT_SENT` dice "no se envió y no se esperaba enviarlo", que es la verdad mientras P-20 esté abierto. El día que se decida avisar, los registros nuevos nacen `PENDING` y los viejos se quedan como constancia de la época en que no se avisaba.

---

### 2. `GET /api/resources/{resourceId}/status-changes`

Auditoría, rol `DIRECCION_PROGRAMA`. Es con lo que se responde SC-001 sin revisar la base a mano.

```json
{
  "resourceId": "ESP-0107",
  "resourceName": "Sala de Estudio 3",
  "changes": [
    {
      "id": "b8f3c107-4d92-4a56-9e01-2c7d8b5f3a64",
      "reservationId": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
      "occupancy": { "start": "2026-09-01T10:00:00-05:00", "end": "2026-09-01T12:00:00-05:00" },
      "previousStatusModule2": "DISPONIBLE",
      "newStatus": "RESERVADO",
      "reason": "RESERVATION_CONFIRMED",
      "changedAt": "2026-08-30T09:14:22-05:00",
      "notifiedStatus": "NOT_SENT"
    },
    {
      "id": "d1a6e284-7b35-4c09-83fd-5e2b9c0a7f18",
      "reservationId": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
      "occupancy": { "start": "2026-09-01T10:00:00-05:00", "end": "2026-09-01T12:00:00-05:00" },
      "previousStatusModule2": "RESERVADO",
      "newStatus": "DISPONIBLE",
      "reason": "RESERVATION_CANCELLED",
      "changedAt": "2026-08-31T09:14:22-05:00",
      "notifiedStatus": "NOT_SENT"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "total": 2 },
  "note": "El estado operativo del recurso (DISPONIBLE, EN_USO, EN_MANTENIMIENTO) lo informa el Módulo 1 y no figura en este historial."
}
```

El campo `note` es fijo y va en la respuesta a propósito: sin él, un historial que nunca menciona `EN_MANTENIMIENTO` parece incompleto, y lo que ocurre es que ese estado no es nuestro. Es la única respuesta de la API que lleva una explicación de este tipo, y se justifica porque este endpoint existe para auditar y una auditoría con un hueco sin explicar es peor que no tenerla.

Filtros opcionales: `from`, `to` y `reason`.

**Errores**: `403` si el rol no es Dirección de Programa; `404` si el recurso no existe en el Módulo 1. Si el inventario no responde, la respuesta llega igual sin `resourceName`, como en UC2 y UC11.

---

### 3. El aviso al Módulo 1, si algún día se activa

**No se implementa.** Queda escrito para que la decisión de P-20 no tenga que rediseñarse, y es lo que se le propondría al Módulo 1:

```json
{
  "eventId": "c9b2f536-8a41-4d07-95ec-3f1a0d7b2e58",
  "type": "ResourceStatusChanged",
  "version": 1,
  "occurredAt": "2026-08-30T09:14:22-05:00",
  "data": {
    "resourceId": "ESP-0107",
    "occupancy": { "start": "2026-09-01T10:00:00-05:00", "end": "2026-09-01T12:00:00-05:00" },
    "newStatus": "RESERVADO",
    "reason": "RESERVATION_CONFIRMED"
  }
}
```

Iría por un topic `module2.resource.status.v1` y por la *outbox*, por FR-007 y SC-005. Lleva `eventId` para que FR-008 se cumpla con la misma deduplicación que el Módulo 3.

> Por qué no se activa: con el reparto de estados, `RESERVADO` y `BLOQUEO_ACADEMICO` son nuestros y el Módulo 1 no los guarda, así que recibir este aviso no le sirve de nada hoy. Si se decide que sí los necesita, también hay que decidir dónde los guarda él, y eso es una conversación de P-20 y no una tarea de este plan.

---

### 4. Fixtures compartidos

```text
src/test/resources/contratos/
├── api-status-changes.json
└── event-resource-status-changed.json     # el que no se envía, para cuando se decida
```

---

## Phase 1: Setup

- [ ] T001 Añadir `reservations.module1.notify-status-changes=false` a `application.properties` y a `ReservationProperties`, documentando que es el interruptor de P-20 punto 1 y que hoy va apagado

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T002 Escribir `V9__status_change.sql` con la tabla de [Contratos §1](#1-la-tabla-status_change) y sus dos índices
- [ ] T003 [P] Crear `StatusChange` y `StatusChangeReason` —con los seis motivos de la tabla de decisiones— en `domain/model/resource/`
- [ ] T004 [P] Definir `StatusChangeRepositoryPort` e `InventoryStatusNotifierPort` en `domain/port/out/`, y completar `UpdateResourceStatusPort` con el motivo y la ocupación
- [ ] T005 [P] Crear la entidad JPA, el repositorio y `StatusChangePersistenceAdapter`, con la consulta paginada por recurso
- [ ] T006 [P] Crear `InventoryStatusNoOpAdapter`, que registra en el log el aviso que no se envía y deja `notified_status` en `NOT_SENT`

**Checkpoint**: existe dónde registrar los cambios

---

## Phase 3: User Story 1 - Mantener coherente el estado de cada recurso (Priority: P1)

**Goal**: Cada cambio de ocupación queda registrado con su motivo y su franja; ningún recurso puede estar en dos estados para el mismo momento; un espacio se libera al terminar su franja y un activo solo con su check-out; y ninguna operación de negocio falla por el estado.

**Independent Test**: No se prueba sola, y eso es parte de su naturaleza: se prueba comprobando que los cinco casos de uso que la invocan dejan el registro y que los invariantes se sostienen. Con el perfil local: reservar, cancelar, cargar un horario, registrar una ausencia y cerrar un préstamo, y comprobar que `GET /api/resources/{id}/status-changes` cuenta la historia completa de ese recurso.

### Tests for User Story 1

- [ ] T007 [P] [US1] Pruebas en `UpdateResourceStatusUseCaseTest.java`: los seis motivos producen su registro con la franja y los dos estados correctos; el registro se escribe en la transacción de quien invoca; y un `previous_status_module2` que nunca es `EN_MANTENIMIENTO` ni `EN_USO` por el Módulo 1 (la decisión de diseño)
- [ ] T008 [P] [US1] Prueba `StatusChangeIT.java`: cada uno de los cinco casos de uso que invocan UC7 deja **exactamente un** registro por ocupación afectada —y una carga de UC3 con 50 bloqueos deja 50—; un *rollback* de la operación no deja ninguno (FR-009)
- [ ] T009 [P] [US1] Prueba `ResourceStatusCoherenceIT.java` para FR-006 y SC-003: con N hilos reservando, cancelando y cargando horarios sobre el mismo recurso, **nunca** quedan dos ocupaciones confirmadas solapadas, y la etiqueta que calcula UC1 es siempre una sola para cada instante
- [ ] T010 [P] [US1] Prueba de FR-010: una franja que termina deja el recurso disponible en la consulta siguiente **sin que ninguna tarea haya corrido**, y si hay otra reserva encima, esa manda; y de FR-011: un préstamo entregado y vencido sigue ocupando y **no** se libera por el paso del tiempo
- [ ] T011 [P] [US1] Prueba de FR-005: un recurso que el Módulo 1 reporta `EN_MANTENIMIENTO` con una reserva confirmada encima se muestra `EN_MANTENIMIENTO`; cancelar esa reserva **no** lo saca de mantenimiento; y ninguna operación nuestra escribe ese estado
- [ ] T012 [P] [US1] Prueba de SC-005: con `InventoryStatusNoOpAdapter` lanzando una excepción a propósito, una reserva y una cancelación **se completan igual**; es la prueba que protege el día que haya un adaptador real
- [ ] T013 [P] [US1] Prueba `StatusChangeControllerTest.java` contra el *fixture*: el historial paginado y ordenado, el campo `note` presente, el filtrado por motivo, el `403` por rol y la respuesta sin `resourceName` cuando el Módulo 1 no contesta

### Implementation for User Story 1

- [ ] T014 [US1] Completar `UpdateResourceStatusUseCase`: recibe el motivo y la ocupación, calcula el estado anterior con los datos del Módulo 2, escribe el registro y llama a `InventoryStatusNotifierPort` (depende de T003 a T006)
- [ ] T015 [US1] Pasar el motivo desde los cinco invocadores: `ReserveResourcesUseCase`, `ApplyScheduleUseCase`, `CancelReservationUseCase`, `ReceiveNoShowUseCase`, `ReceiveCheckOutUseCase` y `DeclareLoanLostUseCase` (depende de T014)
- [ ] T016 [US1] Implementar `StatusChangeController` según [Contratos §2](#2-get-apiresourcesresourceidstatus-changes)
- [ ] T017 [US1] Registrar los beans de UC7 en `UseCasesConfig` y actualizar [modelo-datos-der.md](./modelo-datos-der.md) con la tabla `status_change`, que el plan general había dejado pendiente

**Checkpoint**: la mitad de UC7 que no depende de P-20 queda cerrada y probada

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T018 [P] Verificar SC-001 con una auditoría sobre datos de prueba: para cada franja revisada, la etiqueta que muestra UC1 coincide con lo que cuenta el historial de `status_change`
- [ ] T019 [P] Verificar SC-002: un recurso liberado aparece disponible en menos de 5 s, con los tres motivos de liberación (cancelación, ausencia y check-out)
- [ ] T020 [P] Documentar en el README que el estado del recurso no es una columna sino la consecuencia de las ocupaciones, porque es lo que más desconcierta al leer el código por primera vez
- [ ] T021 Llevar a `pendientes-clarificacion.md` el estado de este plan: qué quedó hecho, qué quedó diseñado sin activar y qué hay que corregir en el spec de UC7 tras el reparto de estados

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: depende de UC2, UC3, UC4, UC9 y UC12, que son los que invocan este caso de uso. UC7 **no se puede hacer antes** que ellos, aunque sea P1: no hay nada que registrar hasta que existan los cambios
- **Foundational (Phase 2)**: depende de Setup - BLOCKS la user story
- **User Story 1 (Phase 3)**: depende de Foundational
- **Polish (Phase 4)**: depende de la Phase 3

### Dependencias con otros casos de uso

- **UC2, UC3, UC4, UC9 y UC12**: son los cinco que invocan UC7, y cada uno ya implementó su parte —dejar la ocupación escrita o liberada—. Lo que este plan les añade es una línea: pasar el motivo. Si alguno se salta la llamada, FR-001 se incumple sin que nada falle, y por eso la prueba de T008 recorre los cinco.
- **UC1 `Consultar recursos`**: es quien **lee** el resultado de UC7. Su tabla de prioridad es, de hecho, la implementación de FR-003 y FR-006: convierte en una etiqueta lo que podrían ser varios estados a la vez. No hay que tocarlo.
- **UC8 `Consultar disponibilidad`**: la consulta de ocupaciones que UC1 y UC2 usan es lo que hace cumplir FR-010 y FR-011. Tampoco hay que tocarlo.
- **Módulo 1**: hoy no recibe nada de este caso de uso. Toda la relación está detrás de P-20 punto 1.

### Within User Story 1

- Esquema (T002) → modelo (T003) → puertos (T004) → persistencia y adaptador vacío (T005, T006) → caso de uso (T014) → los cinco invocadores (T015)
- Controlador (T016) y beans (T017) al final

### Parallel Opportunities

- En Foundational: T003 a T006
- En User Story 1: todas las pruebas (T007 a T013)
- En Polish: T018 a T020

## Notes

- La numeración `T0XX` es propia de este plan
- [P] tasks = different files, no dependencies
- Verify tests pass
- Commit after each task or logical group
- **Este plan es parcial a propósito.** La mitad de coherencia se cierra; la mitad de aviso al Módulo 1 queda diseñada y apagada, porque hoy no tiene destinatario
- **Lo que ya estaba hecho y aquí solo se documenta**: FR-002 (UC2 y UC12), FR-006 y SC-003 (la restricción de exclusión), FR-010 y FR-011 (la consulta de ocupaciones de UC1), FR-005 (el mantenimiento no es nuestro). Lo nuevo es FR-009, el registro
- **Decisiones de los contratos que el spec no fija**: el estado anterior se guarda solo con los datos del Módulo 2 y la columna se llama `previous_status_module2` para no confundirlo con la etiqueta completa; `notified_status` arranca en `NOT_SENT` y no en `PENDING`, porque hoy no hay aviso que esperar; el historial lleva un campo `note` fijo que explica qué no está ahí; y si algún día hay que avisar al Módulo 1, va por la *outbox* y no por REST síncrono, por FR-007 y SC-005
- **Decisión explícita de no hacer**: ninguna tarea programada que recorra reservas para cambiar estados. Los rangos ya dicen la verdad en cada consulta, y un `@Scheduled` solo añadiría algo que puede desincronizarse
- **NEEDS CLARIFICATION abiertos en este plan**:
  - **P-20 punto 1**: ¿el Módulo 2 le avisa algo al Módulo 1? De eso dependen FR-004, FR-007 y FR-008, que son la mitad del spec. Hoy no se avisa
  - **P-20 punto 4**: hay que quitar `RESERVADO` y `BLOQUEO_ACADEMICO` del spec del Módulo 1, y corregir FR-003 de UC7, que todavía llama a los cinco estados "del inventario"
  - **P-02**: nadie nos informa de que una persona se presentó, así que `EN_USO` en un espacio solo aparece si el Módulo 1 lo dice. FR-001 pide cambiar el estado cuando la reserva "empieza a usarse" y ese disparador no existe
  - **P-20 punto 3**: si `EN_MANTENIMIENTO` trae fechas, la prioridad de UC1 podría dejar de aplicarlo a todas las franjas futuras, y este historial tendría que registrarlo
