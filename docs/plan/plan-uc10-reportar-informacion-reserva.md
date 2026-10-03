# Implementation Plan: Reportar información de la reserva (UC10)

**Date**: 2026-10-02
**Spec**: [spec-modulo2-uc10-reportar-informacion-reserva.md](../specs/spec-modulo2-uc10-reportar-informacion-reserva.md)
**Plan general**: [plan-arquitectura.md](./plan-arquitectura.md) — incluida la [convención de nombres](./plan-arquitectura.md#convención-de-nombres)
**Planes previos**: [UC2](./plan-uc2-reservar-recursos.md) montó la *outbox*, el publicador y el evento de la ficha; [UC3](./plan-uc3-importar-horarios-semestrales.md) añadió la ficha académica; [UC11](./plan-uc11-reportar-cancelacion-reserva.md) resolvió el mismo problema para las cancelaciones y este plan reusa su patrón

## Summary

UC10 es el `<<include>>` que le cuenta al Módulo 3 **qué se apartó, quién lo apartó y hasta cuándo**. Sin eso el Módulo 3 no puede sancionar, ni medir mora, ni cerrar un check-out: no hace reservas, así que todo lo que sabe de ellas se lo decimos nosotros.

Igual que con UC11, el camino feliz ya funciona: UC2 encola la ficha en la misma transacción que la reserva y UC3 hace lo propio con cada bloqueo académico. Lo que falta son **las garantías** y **la pieza que nadie implementó**:

**Enfoque técnico:**

1. **La ficha de la renovación** (FR-003). UC2 decidió el nombre del evento —`ReservationRecordUpdated`— pero dejó la emisión en su Phase 4. Aquí se completa: al renovar, el vencimiento nuevo es la fecha contra la que el Módulo 3 mide el retraso, así que si no se la decimos, mide contra la vieja.
2. **El historial de fichas enviadas** (FR-007). Mismo problema y misma solución que UC11: `outbox_message` es una tabla de trabajo purgable, y la entidad `ReservationRecord` del spec pide una constancia propia con el resultado de cada envío. Se reusa `DeliveryStatus` y la forma de la tabla de UC11.
3. **Que un reenvío no cuente dos veces** (FR-006, SC-003). La *outbox* entrega *at-least-once*, así que el Módulo 3 **va a** recibir repeticiones. Lo que se garantiza de nuestro lado es que el `eventId` identifique el hecho y no el intento, y que la clave del historial impida dos fichas distintas de la misma reserva.
4. **Que nada no confirmado salga** (FR-008, SC-005). Sale gratis de cómo está montado: la ficha se inserta en la misma transacción que la reserva, así que una denegación hace *rollback* de las dos. Lo que falta es la prueba que lo demuestre.
5. **Que una ficha académica no se pueda tomar por sancionable** (FR-009, SC-006): `sanctionable: false` y `holder` ausente, con la asignatura, el programa y el docente en su lugar.

Este plan **no redefine el evento**: su contrato quedó cerrado en [UC2 § Contratos §7](./plan-uc2-reservar-recursos.md#7-evento-hacia-el-módulo-3--module2reservationrecordv1).

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 4.1.1, Spring for Apache Kafka, Flyway, springdoc-openapi. **Ninguna nueva.**
**Storage**: PostgreSQL. UC10 **escribe** la tabla nueva `reservation_record` y lee `reservation`, `loan`, `academic_block` y `outbox_message`.
**Testing**: JUnit 5 y AssertJ, Testcontainers con PostgreSQL (la atomicidad con la reserva y el historial) y con Kafka (entrega, reintentos y la caída de 30 minutos de SC-004), `@WebMvcTest` (el endpoint de auditoría), ArchUnit
**Target Platform**: Servidor Linux con JVM 21
**Performance Goals**: La ficha **sale en menos de 5 segundos** desde que la reserva queda confirmada (SC-002). Con `reservations.outbox.interval=5s` el primer intento entra justo en el presupuesto, así que el publicador se dispara además **al confirmar la transacción** y no solo por el reloj; ver la decisión de diseño.
**Constraints**:
- Ninguna ficha de una reserva no confirmada (FR-008, SC-005).
- El Módulo 2 no decide consecuencias (FR-004).
- Si el Módulo 3 está caído, la reserva se confirma igual (FR-005, SC-004).
- Las fichas académicas van marcadas como no sancionables (FR-009, SC-006).
**Scale/Scope**: Una ficha por reserva confirmada, más una por renovación. Una carga de horarios produce miles de golpe. Sin pantalla: su único endpoint es de auditoría.

## Project Structure

### Source Code (repository root)

```text
src/main/java/edu/unimagdalena/reservasunimag/
├── domain/
│   ├── model/reservation/
│   │   ├── ReservationRecord.java          # nuevo: la constancia de FR-007
│   │   ├── RecordKind.java                 # nuevo: CREATED, UPDATED
│   │   └── DeliveryStatus.java             # (existe, de UC11) se reusa
│   ├── port/
│   │   ├── in/ReportReservationPort.java   # (existe, de UC2) + la renovación
│   │   └── out/ReservationRecordRepositoryPort.java
│   └── usecase/reportreservation/
│       └── ReportReservationUseCase.java   # (existe, de UC2) se completa aquí
│
└── infrastructure/
    ├── adapter/
    │   ├── in/web/reservation/
    │   │   └── ReservationRecordController.java    # GET de auditoría
    │   └── out/
    │       ├── persistence/
    │       │   ├── entity/ReservationRecordJpa.java
    │       │   ├── repository/ReservationRecordJpaRepository.java
    │       │   └── ReservationRecordPersistenceAdapter.java
    │       └── messaging/
    │           ├── OutboxPublisher.java            # (existe) + disparo tras el commit
    │           └── evento/ReservationRecordEvent.java   # (existe) + el tipo UPDATED
    └── config/
        └── UseCasesConfig.java

src/main/resources/db/migration/
└── V7__reservation_record.sql

src/test/java/edu/unimagdalena/reservasunimag/
├── domain/usecase/reportreservation/ReportReservationUseCaseTest.java
└── infrastructure/adapter/
    ├── in/web/reservation/ReservationRecordControllerTest.java
    └── out/
        ├── persistence/ReservationRecordIT.java
        └── messaging/RecordDeliveryIT.java
```

**Structure Decision**: ninguna carpeta nueva. UC10 es el gemelo de UC11 y comparte con él `DeliveryStatus`, la forma del historial y el endpoint de auditoría. Esa simetría es deliberada: dos tablas con la misma estructura y dos endpoints con la misma forma se entienden y se mantienen a la vez.

### Decisiones de diseño de este caso de uso

**El historial, otra vez, y por qué no se unifica con el de UC11.** Las dos tablas se parecen tanto que la tentación es hacer una sola `module3_report` con una columna `kind`. Se mantienen separadas porque **los datos del negocio no son los mismos**: una ficha lleva el periodo de préstamo, el plazo y la asignatura; una cancelación lleva el origen, la antelación y si es imputable. Mezclarlas daría una tabla con media docena de columnas nulas según la fila, que es precisamente lo que la convención de "nunca `null`" evita en la API. Lo que sí se comparte es el tipo `DeliveryStatus`, los índices y la forma del endpoint.

**El publicador se dispara al confirmar, no solo cada 5 segundos** (SC-002). El plan general dejó el publicador con `@Scheduled` cada 5 segundos, lo que en el peor caso pone la ficha en el broker justo en el límite de SC-002, y si el reloj acaba de pasar, fuera. Se añade un disparo por evento de Spring (`TransactionalEventListener` con `AFTER_COMMIT`) que despierta al publicador en cuanto la transacción de la reserva confirma. El `@Scheduled` **se queda**, como red de seguridad para lo que el disparo no alcance —un fallo del publicador, una instancia que se cayó—, y es lo que sigue garantizando SC-004. La *outbox* no se toca: lo único que cambia es cuándo se mira.

**Un `eventId` por hecho, no por intento** (FR-006, SC-003). Es la regla que hace que un reenvío no cuente dos veces, y conviene decir con precisión qué garantiza cada lado:

| Quién | Qué garantiza |
|---|---|
| Nosotros | Que el `eventId` de una ficha no cambie entre reintentos, porque es el `outbox_message.id`; y que no existan dos fichas distintas de la misma reserva y el mismo `kind`, porque es la clave del historial |
| El Módulo 3 | Que al ver un `eventId` repetido lo descarte en vez de contar otra reserva |

No podemos garantizar *exactly-once* contra un sistema ajeno, y el spec no lo pide: FR-006 dice que un reenvío no debe producir una segunda reserva **contabilizada**, y eso se cumple dándole al Módulo 3 lo que necesita para deduplicar. Hay que decírselo explícitamente (T016).

**La renovación es un hecho aparte, con su propia fila** (FR-003). La clave del historial es `(reservation_id, kind)`, de modo que una reserva tiene una fila `CREATED` y, si se renueva, una `UPDATED`. No se sobrescribe la primera: el Módulo 3 mide el retraso contra el vencimiento vigente, pero para auditar hace falta saber cuál era el anterior y cuándo cambió. Si UC2 decide en el futuro permitir más de una renovación, la clave pasa a llevar un número de versión; hoy la única renovación posible la fija UC2 FR-016.

> La renovación solo existe mientras el spec de UC2 la conserve. La rama de revisión de specs propone eliminarla; si entra, esta decisión y la tarea T011 se caen, y con ellas FR-003.

**Ninguna ficha de algo que no se confirmó** (FR-008, SC-005). No hay nada que programar: la ficha se inserta en `outbox_message` y en `reservation_record` dentro de la transacción de la reserva, así que una denegación por `RES-00X` o una violación de la restricción de exclusión las deshace. Lo que este plan aporta es la **prueba** de que es así (T008), porque es el tipo de garantía que se rompe sin que nadie se dé cuenta el día que alguien mueve el `INSERT` fuera de la transacción.

**La ficha académica no puede confundirse con una sancionable** (FR-009, SC-006). Tres cosas a la vez, y las tres se prueban: `origin: "ACADEMICO"`, `sanctionable: false` y **ausencia** del objeto `holder`, con `academic` en su lugar. La ausencia del titular es la más importante: un `holder` con datos vacíos invitaría a que el Módulo 3 le anotara algo a alguien.

**Qué no se envía.** La ficha no lleva el motivo de la reserva, ni el equipamiento del espacio, ni nada que el Módulo 3 no necesite para cumplir su función. FR-002 fija la lista y este plan no la amplía: cada campo de más es un dato personal o institucional viajando sin razón.

## Contratos

Se aplican las **convenciones comunes** de [UC1 § Contratos](./plan-uc1-consultar-recursos.md#contratos).

---

### 1. El evento `ReservationRecordCreated` / `ReservationRecordUpdated`

**No se redefine aquí.** El contrato, con sus dos formas —estudiantil y académica—, está en [UC2 § Contratos §7](./plan-uc2-reservar-recursos.md#7-evento-hacia-el-módulo-3--module2reservationrecordv1). Lo que fija este plan es **la diferencia entre los dos tipos**, que es lo que FR-003 necesita:

| | `ReservationRecordCreated` | `ReservationRecordUpdated` |
|---|---|---|
| Cuándo | La reserva queda confirmada (FR-001) | Un préstamo se renueva (FR-003) |
| `data.loan.dueAt` | El vencimiento inicial | **El vencimiento nuevo** |
| `data.loan.renewed` | `false` | `true` |
| `data.loan.previousDueAt` | ausente | el vencimiento que se reemplaza |
| Resto de `data` | completo | completo, igual que el primero |

**Decisión: el evento de renovación va completo, no como un parche.** Podría llevar solo `reservationId` y el vencimiento nuevo, que es lo único que cambió. Va entero para que el Módulo 3 pueda procesarlo sin haber guardado el primero, y para que un reproceso desde el topic reconstruya el estado sin depender del orden de llegada de dos eventos parciales. El coste en bytes es irrelevante frente a esa propiedad.

Los dos van al mismo topic `module2.reservation.record.v1` con la clave `reservation_id`, así que la actualización nunca puede adelantar a la creación.

---

### 2. `GET /api/reservations/{reservationId}/record`

La cara consultable de FR-007. Titular o `DIRECCION_PROGRAMA`.

```json
{
  "reservationId": "4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3",
  "records": [
    {
      "kind": "CREATED",
      "eventId": "c3a7f1d2-4e58-4b09-9a61-7d2e8f0b5c34",
      "origin": "ESTUDIANTIL",
      "sanctionable": true,
      "sentAt": "2026-08-30T09:14:22-05:00",
      "loan": { "pickup": "2026-09-01T14:30:00-05:00", "dueAt": "2026-09-10T22:00:00-05:00", "termBusinessDays": 7 },
      "delivery": { "status": "SENT", "attempts": 1, "deliveredAt": "2026-08-30T09:14:24-05:00", "lastError": null }
    },
    {
      "kind": "UPDATED",
      "eventId": "9b2e5f71-3c84-4d16-8a0f-6e1d7c3b2a95",
      "origin": "ESTUDIANTIL",
      "sanctionable": true,
      "sentAt": "2026-09-08T11:02:40-05:00",
      "loan": { "pickup": "2026-09-01T14:30:00-05:00", "dueAt": "2026-09-21T22:00:00-05:00", "previousDueAt": "2026-09-10T22:00:00-05:00", "renewed": true },
      "delivery": { "status": "PENDING", "attempts": 2, "nextAttemptAt": "2026-09-08T11:03:10-05:00", "lastError": "Connection refused" }
    }
  ]
}
```

Es una **lista**, no un objeto, porque una reserva puede tener dos fichas. Van ordenadas por `sentAt`. `delivery` tiene la misma forma que en [UC11 § Contratos §2](./plan-uc11-reportar-cancelacion-reserva.md#2-get-apireservationsreservationidcancellation-report), incluido el `lastError` que viaja como `null` a propósito.

Una ficha académica se ve así, sin `holder` y con `academic`:

```json
{
  "kind": "CREATED",
  "eventId": "8d41b6c0-2f93-4a7e-8c15-9b0e3d7f2a68",
  "origin": "ACADEMICO",
  "sanctionable": false,
  "sentAt": "2026-08-30T09:14:22-05:00",
  "academic": { "course": "Redes de Computadores", "courseCode": "IS-402", "program": "Ingeniería de Sistemas" },
  "delivery": { "status": "SENT", "attempts": 1, "deliveredAt": "2026-08-30T09:14:25-05:00", "lastError": null }
}
```

El `instructor` **no** aparece en el endpoint de auditoría aunque sí viaje en el evento: en la auditoría no hace falta y es un dato de una persona que no es parte de la reserva.

**Errores**: `404` si la reserva no existe o no es del titular que pregunta; `403` si quien pregunta no es ni el titular ni Dirección de Programa.

---

### 3. `GET /api/reservation-records`

Auditoría en bloque, solo `DIRECCION_PROGRAMA`. Es con lo que se responde SC-001 y SC-006 con datos.

```http
GET /api/reservation-records?delivery=FAILED&origin=ACADEMICO&from=2026-08-10&to=2026-12-05&page=1
```

```json
{
  "records": [ "…la misma forma de §2, con reservationId en cada fila…" ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "total": 3 },
  "summary": { "total": 3, "sent": 0, "pending": 1, "failed": 2, "sanctionable": 0 }
}
```

`summary.sanctionable` cuenta cuántas de las fichas del filtro van marcadas como sancionables. Con `origin=ACADEMICO` tiene que dar **cero**, y esa es exactamente la comprobación de SC-006 convertida en una consulta que cualquiera puede repetir.

---

### 4. La tabla `reservation_record`

| Columna | Tipo | Nota |
|---|---|---|
| reservation_id | uuid FK | Parte de la clave |
| kind | varchar | `CREATED`, `UPDATED`. **PK compuesta** con `reservation_id` |
| event_id | uuid, único | El `outbox_message.id` |
| origin | varchar | `ESTUDIANTIL`, `ACADEMICO` |
| sanctionable | boolean | `false` en las académicas (FR-009) |
| holder_user_id | uuid FK, nulo | Nulo en las académicas |
| resource_id | varchar | |
| resource_category | varchar | |
| occupancy_starts_at | timestamptz | La franja, o la recogida |
| occupancy_ends_at | timestamptz | La franja, o el vencimiento |
| loan_term_business_days | int, nulo | Solo en activos |
| previous_due_at | timestamptz, nulo | Solo en `UPDATED` |
| sent_at | timestamptz | |
| delivery_status | varchar | `PENDING`, `SENT`, `FAILED` |
| attempts | int | |
| first_attempt_at | timestamptz, nulo | |
| delivered_at | timestamptz, nulo | |
| last_error | varchar, nulo | |

```sql
ALTER TABLE reservation_record ADD PRIMARY KEY (reservation_id, kind);
CREATE INDEX reservation_record_delivery ON reservation_record (delivery_status, sent_at);
CREATE INDEX reservation_record_origin ON reservation_record (origin, sent_at);
```

**La clave primaria compuesta es lo que cumple FR-006**: no puede haber dos fichas `CREATED` de la misma reserva, ni dos `UPDATED`. Es el mismo criterio que la clave de `cancellation_report` en UC11 y que la *inbox* del plan general: la unicidad la sostiene la base, no una comprobación.

Los datos académicos **no se copian**: salen de `academic_block` por la clave foránea de la reserva. El nombre del recurso tampoco (UC1 FR-006): lo pide el endpoint al Módulo 1 al responder.

---

### 5. Fixtures compartidos

```text
src/test/resources/contratos/
├── api-reservation-record-with-renewal.json
├── api-reservation-record-academic.json
├── api-reservation-records-list.json
└── event-reservation-record-updated.json      # el de la renovación, que faltaba
```

Los dos `event-ficha-*.json` de UC2 se renombran a `event-reservation-record-created-{student,academic}.json`, para que los tres eventos de este topic queden juntos y con el nombre del tipo que llevan.

---

## Phase 1: Setup

- [ ] T001 Añadir `reservations.outbox.publish-on-commit=true` a `application.properties` y a `ReservationProperties`, para poder desactivar el disparo inmediato y quedarse solo con el `@Scheduled` si diera problemas

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T002 Escribir `V7__reservation_record.sql` con la tabla de [Contratos §4](#4-la-tabla-reservation_record), su clave primaria compuesta y sus dos índices
- [ ] T003 [P] Crear `ReservationRecord` y `RecordKind` en `domain/model/reservation/`, reusando el `DeliveryStatus` de UC11, con los invariantes: `holder_user_id` nulo si y solo si el origen es `ACADEMICO`, `sanctionable` falso en ese caso, y `previous_due_at` solo en `UPDATED`
- [ ] T004 [P] Definir `ReservationRecordRepositoryPort` en `domain/port/out/` y extender `ReportReservationPort` con la ficha de renovación
- [ ] T005 [P] Crear la entidad JPA, el repositorio y `ReservationRecordPersistenceAdapter`, con el `INSERT` que falla por clave repetida y la actualización del resultado del envío

**Checkpoint**: existe dónde guardar la constancia de cada ficha

---

## Phase 3: User Story 1 - Informar al Módulo 3 de cada reserva confirmada (Priority: P3)

**Goal**: Toda reserva confirmada —estudiantil o académica— y toda renovación llegan al Módulo 3 con su titular o su asignatura, su recurso y su tiempo apartado; ninguna reserva denegada se informa; ninguna ficha se pierde con el Módulo 3 caído media hora; y ninguna ficha académica puede tomarse por sancionable.

**Independent Test**: Reservar un activo y comprobar contra un simulador del Módulo 3 que llegó la ficha con el vencimiento; renovarlo y comprobar que llega la segunda con el vencimiento nuevo y el anterior; forzar una denegación y comprobar que no llegó nada; cargar un horario y comprobar que todas las fichas van con `sanctionable: false` y sin titular.

### Tests for User Story 1

- [ ] T006 [P] [US1] Pruebas en `ReportReservationUseCaseTest.java`: la ficha de un espacio lleva la franja y la de un activo el periodo con su vencimiento (FR-002); la académica va con `origin: ACADEMICO`, `sanctionable: false`, **sin** `holder` y con asignatura, programa y docente (FR-009, SC-006); y ninguna lleva campos que FR-002 no pida
- [ ] T007 [P] [US1] Pruebas de la ficha de renovación (FR-003): se emite un `UPDATED` con el vencimiento nuevo y el `previousDueAt`, el `CREATED` original **no** se sobrescribe, y el evento va completo y no como un parche
- [ ] T008 [P] [US1] Prueba de FR-008 y SC-005: una reserva denegada por cada uno de los cuatro `RES-00X` y una que choca con la restricción de exclusión no dejan ni fila en `reservation_record` ni mensaje en `outbox_message`
- [ ] T009 [P] [US1] Prueba `ReservationRecordIT.java` con Testcontainers: la ficha, el `outbox_message` y la reserva se escriben en la misma transacción; un segundo `CREATED` de la misma reserva choca con la clave primaria (FR-006, SC-003); y un `UPDATED` sí se admite junto al `CREATED`
- [ ] T010 [P] [US1] Prueba `RecordDeliveryIT.java` con Testcontainers de Kafka: la ficha sale **en menos de 5 segundos** desde el *commit* gracias al disparo tras la transacción (SC-002); el Módulo 3 caído 30 minutos no pierde ninguna (SC-004); `delivery_status` recorre `PENDING` → `SENT` con sus `attempts`; y al agotarlos queda `FAILED` con su `last_error`
- [ ] T011 [P] [US1] Prueba de la carga masiva: un horario de UC3 con 300 clases produce 300 fichas, todas `ACADEMICO` y ninguna sancionable, y el `summary` del listado lo confirma con `sanctionable: 0` (SC-006)
- [ ] T012 [P] [US1] Prueba `ReservationRecordControllerTest.java` contra los *fixtures*: la lista con `CREATED` y `UPDATED` ordenada por `sentAt`, la ficha académica **sin** `instructor` en la respuesta, el filtrado y el `summary` del listado, el `404` de una reserva de otra persona y el `403` por rol

### Implementation for User Story 1

- [ ] T013 [US1] Completar `ReportReservationUseCase`: guarda el `ReservationRecord` además de encolar el evento, con los invariantes de T003 (depende de T002 a T005)
- [ ] T014 [US1] Emitir la ficha `UPDATED` desde `RenewLoanUseCase` por el mismo puerto, con `previousDueAt` (FR-003) (depende de T013)
- [ ] T015 [US1] Extender `OutboxPublisher` con el disparo tras el *commit* y con la actualización del resultado del envío en `reservation_record` (FR-007, SC-002)
- [ ] T016 [US1] Implementar `ReservationRecordController` con los dos endpoints de [Contratos §2 y §3](#2-get-apireservationsreservationidrecord), y dejar escrito en el documento de contrato que el Módulo 3 debe deduplicar por `eventId` (FR-006)
- [ ] T017 [US1] Registrar el bean y la transacción de UC10 en `UseCasesConfig`

**Checkpoint**: UC10 queda completo

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T018 [P] Verificar SC-001 de punta a punta: reservar, cargar un horario y renovar, y comprobar que el 100 % quedó informado con sus datos
- [ ] T019 [P] Registrar en logs cada ficha con su tipo, su origen y el resultado de cada intento, sin datos personales más allá del identificador
- [ ] T020 [P] Documentar en el README cómo consultar las fichas `FAILED` y reencolarlas, junto a lo mismo de UC11
- [ ] T021 Acordar con el Módulo 3 la deduplicación por `eventId` y el tipo `ReservationRecordUpdated`, que el plan general no había nombrado, y anotar lo que quede abierto

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: depende de UC2 (*outbox*, evento y renovación), UC3 (ficha académica) y UC11 (`DeliveryStatus` y el patrón del historial)
- **Foundational (Phase 2)**: depende de Setup - BLOCKS la user story
- **User Story 1 (Phase 3)**: depende de Foundational
- **Polish (Phase 4)**: depende de la Phase 3

### Dependencias con otros casos de uso

- **UC2 `Reservar recursos`**: es quien dispara la ficha, y la llamada ya está dentro de su transacción. UC10 le añade el historial y la ficha de la renovación, que su Phase 4 había dejado pendiente.
- **UC3 `Importar horarios semestrales`**: produce las fichas académicas en bloque. No hay que tocarlo.
- **UC11 `Reportar cancelación de reserva`**: el gemelo. Comparten `DeliveryStatus`, la forma del historial, la forma del `delivery` en la API y el publicador. Lo que UC10 añade al publicador —el disparo tras el *commit*— mejora también la entrega de las cancelaciones.
- **UC12 `Recibir check-out`**: mide contra el vencimiento que esta ficha informa. Si FR-003 no se implementa, un check-out de un préstamo renovado se compara contra una fecha vieja.
- **UC6 `Consultar sanciones`**: el camino de vuelta de todo esto, y la razón de que UC10 no decida nada (FR-004).

### Within User Story 1

- Esquema (T002) → modelo y puertos (T003, T004) → persistencia (T005) → caso de uso (T013) → renovación (T014)
- Publicador (T015) y controlador (T016) son independientes entre sí

### Parallel Opportunities

- En Foundational: T003, T004 y T005
- En User Story 1: todas las pruebas (T006 a T012)
- En Polish: T018 a T020

## Notes

- La numeración `T0XX` es propia de este plan
- [P] tasks = different files, no dependencies
- Verify tests pass
- Commit after each task or logical group
- Este plan **no redefine el evento**: su contrato vive en UC2, y aquí solo se fija la diferencia entre `CREATED` y `UPDATED`
- **Decisiones de los contratos que el spec no fija**: el historial va en su propia tabla y no se unifica con el de UC11, porque los datos del negocio no son los mismos; la clave primaria es `(reservation_id, kind)`, de modo que la base garantiza FR-006; el evento de renovación va completo y no como un parche; el publicador se dispara al confirmar la transacción además del `@Scheduled`, porque los 5 s de SC-002 no caben en el intervalo del reloj; y el `instructor` viaja en el evento pero no en el endpoint de auditoría
- **NEEDS CLARIFICATION abiertos en este plan**:
  - **Deduplicación en el Módulo 3**: FR-006 solo se cumple si ellos descartan por `eventId`. Hay que acordarlo explícitamente
  - **`ReservationRecordUpdated`**: el nombre del tipo lo propuso el plan de UC2 y sigue sin confirmar con el Módulo 3
  - **La renovación puede desaparecer**: la rama de revisión de specs propone eliminar la renovación de UC2. Si entra, FR-003, la fila `UPDATED` y la tarea T014 se caen
  - **Purga de la *outbox***: igual que en UC11, nadie ha fijado cuándo se borran los mensajes enviados; el historial existe para no depender de eso
