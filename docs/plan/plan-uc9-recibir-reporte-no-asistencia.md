# Implementation Plan: Recibir reporte de no asistencia (UC9)

**Date**: 2026-10-02
**Spec**: [spec-modulo2-uc9-recibir-reporte-no-asistencia.md](../specs/spec-modulo2-uc9-recibir-reporte-no-asistencia.md)
**Plan general**: [plan-arquitectura.md](./plan-arquitectura.md) — incluida la [convención de nombres](./plan-arquitectura.md#convención-de-nombres)
**Planes previos**: [UC2](./plan-uc2-reservar-recursos.md) (la reserva y la *outbox*), [UC4](./plan-uc4-cancelar-reserva.md) (la liberación de la ocupación) y [UC11](./plan-uc11-reportar-cancelacion-reserva.md), que creó `reservation_closure`, la tabla de la que depende que una ausencia y una cancelación no coexistan

## Summary

UC9 es el **primer caso de uso que entra por Kafka**: hasta aquí todo lo disparaba el frontend o otro caso de uso. El Módulo 3 constata en el sitio que alguien no se presentó y nos lo reporta; nosotros dejamos la constancia y liberamos el recurso. La sanción la decide y la aplica él (FR-003).

Lo que no hace es igual de importante: **el Módulo 2 no declara ausencias por su cuenta** (FR-001, FR-008, escenario 2). No hay reloj ni tarea programada que revise quién no llegó. Desde una base de datos nadie puede saber si una persona entró a una sala.

**Enfoque técnico:**

1. Un `@KafkaListener` sobre `module3.reservation.no-show.v1` traduce el evento a la petición del puerto de entrada. Es un adaptador de entrada más, al mismo nivel que un controlador REST, y el caso de uso no sabe que detrás hay un broker.
2. La *inbox* del plan general hace el trabajo de FR-007 y SC-002: el `eventId` se inserta en `inbox_message` en la misma transacción, y un evento repetido se descarta sin volver a aplicarlo.
3. **Cinco razones para no registrar la ausencia**, y cada una tiene su desenlace: reporte anticipado (FR-011), reserva cancelada por el titular (FR-005), cancelada por prioridad académica (FR-006), cancelada por mantenimiento (SC-003) y ausencia ya registrada (FR-007).
4. Registrar la ausencia **libera el recurso** (FR-004): un espacio queda disponible el resto de su franja y un activo vuelve a la oferta desde ese mismo instante, sin esperar al vencimiento del préstamo (edge case **Activo que nadie recoge**).
5. Un reporte que llega cuando la franja ya terminó **se registra igual** (FR-008, SC-005): la constancia alimenta el cumplimiento aunque no quede nada que liberar.
6. El Módulo 3 puede **anular** un reporte que mandó por error (FR-012), y la anulación deshace la constancia pero no le devuelve el recurso a nadie.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 4.1.1, **Spring for Apache Kafka** (primer consumidor del proyecto), Flyway, springdoc-openapi. Ninguna nueva respecto a UC2.
**Storage**: PostgreSQL. UC9 **escribe** `absence`, `reservation` (estado), `reservation_closure`, `inbox_message` y `outbox_message` (el acuse), y lee `loan`.
**Testing**: JUnit 5 y AssertJ (caso de uso con puertos falsos), Testcontainers con Kafka (los tres caminos del consumidor: evento nuevo, repetido y inválido) y con PostgreSQL (la transacción y la exclusión con la cancelación), ArchUnit
**Target Platform**: Servidor Linux con JVM 21
**Performance Goals**: La ausencia queda registrada y el recurso liberado **dentro de los 5 minutos** siguientes a la llegada del reporte (SC-001). Es holgado: el trabajo es una transacción local de milisegundos, y el presupuesto cubre un reintento del consumidor si la base está ocupada.
**Constraints**:
- El Módulo 2 **nunca** declara una ausencia por su cuenta (FR-001, FR-008).
- No se aplica ninguna sanción (FR-003).
- Una reserva genera **como máximo una** ausencia (FR-007, SC-002, edge case **Reserva de varias horas**).
- Un reporte anticipado se rechaza indicando desde cuándo se admite (FR-011).
- Una reserva cancelada nunca genera ausencia (FR-005, FR-006, SC-003).
- Los reportes atrasados se aceptan y se registran (FR-008, SC-005).
**Scale/Scope**: Volumen bajo —una ausencia por reserva no usada—, pero con picos al restablecerse el Módulo 3 tras una caída. Sin pantalla propia: la ausencia se ve en `my-reservations`.

## Project Structure

### Documentation (this feature)

```text
docs/
├── specs/
│   ├── spec-modulo2-uc9-recibir-reporte-no-asistencia.md    # spec de este plan
│   ├── spec-modulo2-uc2-reservar-recursos.md                # caso base del <<extend>>, y el plazo
│   ├── spec-modulo2-uc4-cancelar-reserva.md                 # el cierre contrario
│   └── spec-modulo2-uc7-actualizar-estado-recursos.md       # <<include>>: libera el recurso
└── plan/
    ├── plan-uc4-cancelar-reserva.md
    ├── plan-uc11-reportar-cancelacion-reserva.md            # creó reservation_closure
    └── plan-uc9-recibir-reporte-no-asistencia.md           # este archivo
```

### Source Code (repository root)

```text
src/main/java/edu/unimagdalena/reservasunimag/
├── domain/
│   ├── model/
│   │   └── compliance/
│   │       ├── Absence.java                    # nuevo: la constancia de FR-002
│   │       ├── NoShowPolicy.java               # nuevo: el plazo de los 10 minutos
│   │       └── NoShowRejection.java            # nuevo: por qué no se registró
│   ├── port/
│   │   ├── in/
│   │   │   ├── ReceiveNoShowPort.java
│   │   │   └── VoidNoShowPort.java             # FR-012
│   │   └── out/
│   │       ├── AbsenceRepositoryPort.java
│   │       └── ReservationClosurePort.java     # (de UC11) el candado del cierre
│   └── usecase/
│       └── receivenoshow/
│           ├── ReceiveNoShowUseCase.java
│           └── VoidNoShowUseCase.java
│
└── infrastructure/
    ├── adapter/
    │   ├── in/messaging/
    │   │   ├── NoShowListener.java             # @KafkaListener del topic del Módulo 3
    │   │   ├── InboxGuard.java                 # la idempotencia, compartida con UC12
    │   │   └── evento/NoShowReportedEvent.java
    │   └── out/
    │       ├── persistence/
    │       │   ├── entity/AbsenceJpa.java
    │       │   ├── repository/AbsenceJpaRepository.java
    │       │   └── AbsencePersistenceAdapter.java
    │       └── messaging/
    │           └── evento/NoShowAckEvent.java  # el acuse de FR-002
    └── config/
        ├── KafkaConsumerConfig.java            # nuevo: consumidor, DLT y error handler
        └── UseCasesConfig.java                 # (existe) + los beans de UC9

src/main/resources/db/migration/
└── V6__absence.sql

src/test/java/edu/unimagdalena/reservasunimag/
├── domain/model/compliance/NoShowPolicyTest.java
├── domain/usecase/receivenoshow/
│   ├── ReceiveNoShowUseCaseTest.java
│   └── VoidNoShowUseCaseTest.java
└── infrastructure/adapter/
    ├── in/messaging/NoShowListenerIT.java
    └── out/persistence/AbsenceIT.java

frontend/src/features/reservations/
├── MyReservationsList.tsx                      # (existe) + la marca de ausencia
└── types.ts                                    # (existe) + el tipo Absence
```

**Structure Decision**: aparece `infrastructure/adapter/in/messaging`, que el plan general preveía y hasta ahora estaba vacío. `InboxGuard` se crea aquí pero se diseña **compartido**, porque UC12 va a necesitar exactamente lo mismo: es el único trozo de este plan que no es de UC9.

### Decisiones de diseño de este caso de uso

**Las cinco razones para no registrar, y qué hace cada una.** Es la tabla que gobierna el caso de uso, porque un consumidor de Kafka no puede simplemente "devolver un error":

| Situación | FR | Qué hace el sistema | Dónde acaba el evento |
|---|---|---|---|
| Reporte anticipado (antes de `starts_at + 10min`) | FR-011 | Lo rechaza y dice desde cuándo se admite | Acuse de rechazo; **no** al DLT |
| Reserva `CANCELADA` por el titular | FR-005 | No registra nada | Acuse de rechazo |
| Reserva `CANCELADA_POR_PRIORIDAD_ACADEMICA` | FR-006 | No registra nada | Acuse de rechazo |
| Reserva `CANCELADA_POR_RECURSO_NO_DISPONIBLE` | SC-003 | No registra nada | Acuse de rechazo |
| Ya hay una ausencia de esa reserva | FR-007 | No registra una segunda | Acuse de aceptación, porque el estado que el Módulo 3 quería ya existe |
| El `reservationId` no existe, o el JSON no se puede leer | — | Nada | **DLT** (plan general) |

**Decisión: un rechazo de negocio no va al DLT.** El plan general manda al *dead letter topic* los errores no recuperables, y un reporte anticipado lo parece. Pero no es un evento roto: es un evento bien formado que el Módulo 3 mandó demasiado pronto y que puede volver a mandar diez minutos después. Enviarlo al DLT lo escondería en una cola que nadie consume. En cambio se le responde, y el evento se da por procesado.

**El acuse: un topic nuevo que el plan general no tenía.** FR-002 pide **confirmarle al Módulo 3 que se procesó** y FR-011 pide **rechazar indicando desde cuándo se admite**. Por Kafka no hay respuesta a una petición, y el plan general dejó cuatro topics y ningún canal de vuelta para esto. Así que nace un quinto:

| Topic | Evento | Quién |
|---|---|---|
| `module2.reservation.no-show-ack.v1` | `NoShowReportAcknowledged` | publicamos |

Sale por la misma *outbox*, con la misma clave de partición `reservation_id`, de modo que el acuse de una reserva nunca adelanta a su propio reporte. Es la alternativa a abrir un endpoint REST de integración, que el plan general descartó a propósito ("no exponemos endpoints de integración"). (NEEDS CLARIFICATION: hay que acordar este topic con el Módulo 3. Si prefieren un endpoint nuestro, el caso de uso no cambia: cambia el adaptador.)

**El borde del minuto 10, y por qué coincide con el de UC4.** FR-011 admite el reporte "a partir de los 10 minutos siguientes", así que:

```text
se admite cuando   now >= reference + 10min
reference = starts_at          si es un ESPACIO
reference = loan.pickup        si es un ACTIVO   (es el mismo starts_at)
```

El borde es **inclusivo**: a los 10 minutos exactos el reporte ya se admite. Con el borde de UC4 —cancelable hasta `starts_at - 10min` inclusive— los dos criterios encajan sin solaparse y el edge case **Llega justo en el minuto 10** queda determinista. Lo que el spec pide es justamente eso: que ambos módulos cuenten igual, así que el acuse de rechazo lleva el instante `acceptedFrom` calculado, para que el Módulo 3 no tenga que replicar la fórmula.

**En qué estado queda la reserva: se cierra el pendiente.** El plan general lo dejó marcado —"en qué estado queda una reserva con ausencia; la lista de estados de UC2 no tiene uno propio"— y aquí hay que decidirlo para poder escribir el `UPDATE`. Las opciones y por qué se descarta cada una:

| Opción | Problema |
|---|---|
| Un estado nuevo `CANCELADA_POR_AUSENCIA` | Lleva "CANCELADA" en el nombre, y UC11 reportaría una cancelación por algo que no se canceló: choca con UC11 FR-011 y SC-006 |
| Dejarla `CONFIRMADA` y que la ausencia lo explique | La restricción de exclusión seguiría viendo la ocupación y el recurso **no** se liberaría, contra FR-004 |
| **`FINALIZADA` más la fila en `absence`** | Ninguno de los dos |

Se elige `FINALIZADA`: la reserva terminó —mal, pero terminó—, la restricción `reservation_no_overlap` deja de verla porque solo mira las `CONFIRMADA`, el recurso queda libre y el cupo también (UC2 FR-008). Lo que explica *por qué* terminó es la fila de `absence`, que es la constancia que pide FR-002. Así no se inventa un estado que obligaría a tocar UC1, UC2 y UC11. (NEEDS CLARIFICATION: es una propuesta para cerrar el pendiente del plan general; si el equipo prefiere el estado propio, cambia el `UPDATE` y hay que excluirlo en UC11.)

**La exclusión con la cancelación la sostiene la base.** No se comprueba con un `SELECT` previo: se inserta `('ABSENCE')` en la tabla `reservation_closure` que creó UC11, y si la reserva ya estaba cerrada por una cancelación, la clave primaria lo rechaza. Eso cubre FR-005, FR-006 y SC-003 incluso cuando la cancelación y el reporte llegan a la vez, que es el caso que un `if` no cubre.

**Liberar un activo es cancelar el periodo completo** (FR-004, edge case **Activo que nadie recoge**). Quien aparta un libro para el jueves a las 14:30 y no aparece genera **una** ausencia a las 14:40, y el libro vuelve a la oferta desde ese instante: no se espera al vencimiento. Como la ocupación de un préstamo es `[pickup, dueAt)` y la reserva pasa a `FINALIZADA`, se libera entera de una vez, sin recortar rangos.

En un espacio FR-004 dice "queda disponible para el resto de su franja". No hace falta recortar `ends_at`: liberar la fila completa es equivalente, porque nadie puede reservar el tramo que ya pasó. Se documenta para que nadie busque un `UPDATE` de recorte que no existe.

**Una reserva de varias horas genera una sola ausencia** (edge case). Sale gratis: la clave primaria de `absence` es el `reservation_id`, así que la unicidad no es una regla que haya que programar sino la forma de la tabla. Lo mismo vale para el reporte repetido de FR-007.

**La anulación deshace la constancia, no el reparto del recurso** (FR-012). `VoidNoShowUseCase` marca `absence.voided` y deja la fecha, suelta el cierre en `reservation_closure` —para que la reserva pueda cerrarse de otra forma más adelante— y **no** devuelve la reserva a `CONFIRMADA`: el recurso ya volvió a la oferta y puede que otra persona lo haya tomado, y resucitarla rompería la restricción de exclusión. Es exactamente lo que dice el edge case **Reporte equivocado**, y la consecuencia para la persona la deshace el Módulo 3 por su lado.

**Avisarle a la persona** (FR-010). No hay canal de notificación en ningún spec del módulo: no hay correo, ni *push*, ni tabla de avisos. Lo que este plan hace es dejar la ausencia **visible donde la persona ya mira**: `GET /api/reservations/mine` devuelve la marca y `MyReservationsList.tsx` la muestra con la reserva que la originó. Es lo que se puede cumplir sin inventar infraestructura que nadie pidió. (NEEDS CLARIFICATION: FR-010 dice "informar", y si se espera un correo hay que decidir el canal; afecta también a UC4 y a UC3, que tampoco avisan a los desplazados.)

## Contratos

Se aplican las **convenciones comunes** de [UC1 § Contratos](./plan-uc1-consultar-recursos.md#contratos). UC9 casi no tiene API: su entrada es un topic y su salida un acuse.

---

### 1. Evento que consumimos — `module3.reservation.no-show.v1`

Lo publica el Módulo 3. El envoltorio es el del plan general; `data` es lo que esperamos de ellos.

```json
{
  "eventId": "6a1f3b84-2c57-4e90-81d6-9f4e0a7c3b25",
  "type": "NoShowReported",
  "version": 1,
  "occurredAt": "2026-09-01T10:11:03-05:00",
  "data": {
    "reservationId": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
    "verifiedAt": "2026-09-01T10:10:30-05:00",
    "verifiedBy": "MODULO_3",
    "note": "Se verificó en sitio a los 10 minutos."
  }
}
```

| Campo | Obligatorio | Nota |
|---|---|---|
| `reservationId` | sí | Si no existe, el evento va al DLT: no podemos adivinar a quién anotarle la ausencia. |
| `verifiedAt` | sí | **El instante de la comprobación en sitio**, que es el que se mide contra el plazo de FR-011, no el `occurredAt` del envoltorio ni la hora en que nos llega. Un reporte que viaja tarde no se vuelve anticipado por eso. |
| `verifiedBy` | no | Quién constató. Se guarda en el log, no en la tabla: no necesitamos saberlo y puede ser un dato personal de su lado. |
| `note` | no | Texto libre. **No se propaga** a ninguna respuesta nuestra. |

**La anulación de FR-012 llega por el mismo topic**, con otro tipo:

```json
{
  "eventId": "b9d2e5a7-4f81-4c36-92be-7a0c1d8f3e64",
  "type": "NoShowReportVoided",
  "version": 1,
  "occurredAt": "2026-09-01T11:02:14-05:00",
  "data": {
    "reservationId": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
    "voidedReason": "El reporte se envió por error: la persona sí se presentó."
  }
}
```

Van por el mismo topic y no por uno nuevo para que la clave de partición `reservation_id` garantice el orden: la anulación nunca puede adelantar al reporte que anula.

---

### 2. Evento que publicamos — `module2.reservation.no-show-ack.v1`

El acuse de FR-002 y FR-011. Un evento por cada reporte recibido, aceptado o no.

**Aceptado** (escenario 1):

```json
{
  "eventId": "e7c4a018-5b93-4d27-86fa-1c2e9d0b4f73",
  "type": "NoShowReportAcknowledged",
  "version": 1,
  "occurredAt": "2026-09-01T10:11:05-05:00",
  "data": {
    "reservationId": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
    "sourceEventId": "6a1f3b84-2c57-4e90-81d6-9f4e0a7c3b25",
    "accepted": true,
    "absenceRegisteredAt": "2026-09-01T10:11:05-05:00",
    "resourceReleased": true,
    "holder": { "code": "2019114045" },
    "reservedTime": {
      "start": "2026-09-01T10:00:00-05:00",
      "end": "2026-09-01T12:00:00-05:00"
    }
  }
}
```

**Rechazado** — reporte anticipado (FR-011):

```json
{
  "eventId": "3f8b6d20-9a14-4e75-b0c8-5d1e7f2a9c46",
  "type": "NoShowReportAcknowledged",
  "version": 1,
  "occurredAt": "2026-09-01T10:04:12-05:00",
  "data": {
    "reservationId": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
    "sourceEventId": "c2a7e591-8d36-4b04-97fe-0a3d1c8b5f27",
    "accepted": false,
    "rejection": "TOO_EARLY",
    "acceptedFrom": "2026-09-01T10:10:00-05:00",
    "detail": "El reporte se admite a partir de las 10:10. La persona todavía está a tiempo de llegar."
  }
}
```

**Rechazado** — la reserva estaba cancelada (FR-005, FR-006, SC-003):

```json
{
  "data": {
    "reservationId": "a1b2c3d4-5e6f-4789-9abc-def012345678",
    "sourceEventId": "d5f1b382-6c09-4a47-83de-2b7a9e0c1d58",
    "accepted": false,
    "rejection": "RESERVATION_CANCELLED",
    "reservationStatus": "CANCELADA_POR_PRIORIDAD_ACADEMICA",
    "detail": "La reserva se canceló por prioridad académica, así que no corresponde ninguna ausencia."
  }
}
```

| `rejection` | Cuándo | FR |
|---|---|---|
| `TOO_EARLY` | Antes de `verifiedAt >= reference + 10min`. Lleva `acceptedFrom`. | FR-011 |
| `RESERVATION_CANCELLED` | La reserva está en cualquiera de los tres estados de cancelación. Lleva `reservationStatus`. | FR-005, FR-006, SC-003 |
| `ALREADY_RETURNED` | El activo ya tenía check-out: no se puede estar ausente de algo que se usó. | — (decisión de este plan) |

Un reporte **repetido** (FR-007) no se rechaza: responde `accepted: true` con el `absenceRegisteredAt` de la primera vez y `duplicate: true`. El estado que el Módulo 3 buscaba ya existe, así que decirle "no" sería engañoso.

`resourceReleased` es `false` cuando la franja ya había terminado (edge case **Reporte que llega tarde**, FR-008): la constancia se registró, pero no había nada que liberar. Es el campo que distingue los dos desenlaces de SC-005.

---

### 3. La tabla `absence`

Extiende la del plan general con lo que FR-002 y FR-012 piden guardar.

| Columna | Tipo | Nota |
|---|---|---|
| reservation_id | uuid **PK**, FK | Era `id` con un único; pasa a ser la clave primaria: una ausencia por reserva, garantizada por la forma de la tabla (FR-007, SC-002) |
| source_event_id | uuid, único | El `eventId` del Módulo 3; une la constancia con lo que llegó |
| verified_at | timestamptz | El instante de la comprobación en sitio |
| reported_at | timestamptz | Cuándo nos llegó el reporte |
| resource_released | boolean | `false` si la franja ya había terminado |
| voided | boolean | FR-012 |
| voided_at | timestamptz, nulo | |
| voided_reason | varchar, nulo | |

```sql
CREATE INDEX absence_reported ON absence (reported_at);
```

No se guardan la persona ni el recurso: salen de `reservation` por la clave foránea, y duplicarlos abriría la puerta a que dejen de coincidir. La entidad del spec los nombra como atributos de la ausencia, y lo son —el endpoint los devuelve—, pero viven en un solo sitio.

**El cambio respecto al plan general**: `absence.id` desaparece y la clave primaria pasa a ser `reservation_id`. Hay que actualizar [modelo-datos-der.md](./modelo-datos-der.md) (T019).

---

### 4. `GET /api/reservations/mine` — el campo nuevo

UC9 no añade endpoints. Lo que añade es la marca de FR-010 a la lista que ya existe desde [UC2 § Contratos §4](./plan-uc2-reservar-recursos.md#3-get-apireservationsmine):

```json
{
  "id": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
  "resourceId": "ESP-0107",
  "name": "Sala de Estudio 3",
  "category": "ESPACIO",
  "status": "FINALIZADA",
  "start": "2026-09-01T10:00:00-05:00",
  "end": "2026-09-01T12:00:00-05:00",
  "absence": {
    "registeredAt": "2026-09-01T10:11:05-05:00",
    "message": "Se registró una ausencia: no se presentó a esta reserva. La consecuencia, si la hay, la decide la oficina de cumplimiento."
  }
}
```

El `message` no promete ni niega una sanción, porque el Módulo 2 no la decide (FR-003). Una ausencia anulada **no** trae este campo.

---

### 5. Configuración del consumidor

```properties
spring.kafka.consumer.group-id=module2-reservations
spring.kafka.consumer.enable-auto-commit=false
spring.kafka.listener.ack-mode=manual
reservations.kafka.topic.no-show=module3.reservation.no-show.v1
reservations.kafka.topic.no-show-ack=module2.reservation.no-show-ack.v1
reservations.kafka.consumer.concurrency=3
reservations.kafka.consumer.retry.max-attempts=5
reservations.kafka.consumer.retry.initial-interval=1s
```

La concurrencia se iguala al número de particiones del topic, como dice el plan general, para no perder el orden por reserva. El *offset* se confirma **después** del *commit* de la transacción: confirmarlo antes perdería el evento si la transacción falla, y hacerlo después solo puede repetirlo, de lo que ya se encarga la *inbox*.

---

### 6. Fixtures compartidos

```text
src/test/resources/contratos/
├── event-no-show-reported.json
├── event-no-show-voided.json
├── event-no-show-ack-accepted.json
├── event-no-show-ack-too-early.json
├── event-no-show-ack-cancelled.json
└── event-no-show-invalid.json        # sin reservationId: el que debe ir al DLT
```

---

## Phase 1: Setup

- [ ] T001 Añadir a `application.properties` el bloque de consumidor de [Contratos §5](#5-configuración-del-consumidor) y los dos topics nuevos, y extender `ReservationProperties` con el plazo `reservations.no-show-grace=10m`
- [ ] T002 [P] Añadir el topic del acuse a la convención de topics de [plan-arquitectura.md](./plan-arquitectura.md#mensajería-con-kafka), que hasta ahora listaba cuatro

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T003 Escribir `V6__absence.sql` con la tabla de [Contratos §3](#3-la-tabla-absence): `reservation_id` como clave primaria, `source_event_id` único, los campos de anulación y el índice por `reported_at`
- [ ] T004 [P] Crear `KafkaConsumerConfig` con el contenedor de *listeners*, `AckMode.MANUAL`, el `DefaultErrorHandler` con espera creciente para los errores recuperables y el envío al DLT `module2.dlt.<topic>` para los que no lo son, con la causa en las cabeceras
- [ ] T005 [P] Crear `InboxGuard` en `infrastructure/adapter/in/messaging/`: inserta el `eventId` en `inbox_message` dentro de la transacción del caso de uso y distingue "nuevo" de "ya procesado". **Se diseña para compartirlo con UC12**
- [ ] T006 [P] Crear `Absence`, `NoShowPolicy` —con el borde inclusivo de los 10 minutos y el instante de referencia según la categoría— y `NoShowRejection` con sus tres motivos, en `domain/model/compliance/`
- [ ] T007 [P] Definir `ReceiveNoShowPort` y `VoidNoShowPort` en `domain/port/in/`, y `AbsenceRepositoryPort` en `domain/port/out/`
- [ ] T008 [P] Crear la entidad JPA, el repositorio y `AbsencePersistenceAdapter`, con el `INSERT` que falla por clave repetida y la actualización de la anulación

**Checkpoint**: el consumidor y la constancia existen

---

## Phase 3: User Story 1 - Dejar constancia de quien no se presentó (Priority: P2)

**Goal**: Cuando el Módulo 3 reporta que alguien no se presentó, queda la constancia con la persona, el recurso y el tiempo que tenía apartado, el recurso vuelve a la oferta y el Módulo 3 recibe el acuse. Ningún reporte anticipado, repetido o sobre una reserva cancelada produce una ausencia.

**Independent Test**: Con el perfil local y Kafka de Docker Compose, crear una reserva, publicar a mano el evento de no asistencia en el topic y comprobar que se creó la fila de `absence`, que la reserva quedó `FINALIZADA`, que la consulta de UC1 muestra el recurso disponible y que salió el acuse. Repetir el evento y comprobar que no se duplica. Publicarlo antes de los 10 minutos y comprobar el acuse `TOO_EARLY`.

### Tests for User Story 1

- [ ] T009 [P] [US1] Pruebas en `NoShowPolicyTest.java`: se admite a los 10 minutos **exactos** y a los 11, se rechaza a los 9 (edge case **Llega justo en el minuto 10**); en un activo se mide desde la hora de recogida y no desde el vencimiento (edge case **Activo que nadie recoge**); y el plazo se mide contra `verifiedAt`, no contra la hora de llegada del evento (edge case **Reporte que llega tarde**)
- [ ] T010 [P] [US1] Pruebas en `ReceiveNoShowUseCaseTest.java`: el escenario 1 deja la constancia con persona, recurso y tiempo apartado, pone la reserva `FINALIZADA` y produce el acuse aceptado; los escenarios 4 y 5 y la cancelación por mantenimiento producen `RESERVATION_CANCELLED` **sin** crear ausencia (FR-005, FR-006, SC-003); un reporte anticipado produce `TOO_EARLY` con su `acceptedFrom` (FR-011); y un activo libera el periodo completo (FR-004)
- [ ] T011 [P] [US1] Pruebas de los bordes del registro: un reporte repetido responde `accepted: true` con `duplicate: true` y una sola fila (FR-007, SC-002); una reserva de 10:00 a 14:00 genera **una** ausencia (edge case **Reserva de varias horas**); y un reporte que llega con la franja terminada se registra con `resource_released: false` (FR-008, SC-005)
- [ ] T012 [P] [US1] Pruebas en `VoidNoShowUseCaseTest.java`: la anulación marca `voided` con su fecha y su motivo, **no** devuelve la reserva a `CONFIRMADA`, suelta el cierre en `reservation_closure`, y una anulación de una ausencia que no existe no crea nada (FR-012, edge case **Reporte equivocado**)
- [ ] T013 [P] [US1] Prueba `AbsenceIT.java` con Testcontainers: la ausencia, el estado de la reserva, el cierre y el acuse se escriben en una sola transacción; tras registrarla **otra persona puede reservar** la misma franja; una cancelación y un reporte concurrentes dejan exactamente un cierre (UC11 FR-011, SC-006); y un *rollback* no deja ninguna fila
- [ ] T014 [P] [US1] Prueba `NoShowListenerIT.java` con Testcontainers de Kafka, contra los *fixtures*: el evento nuevo se procesa y confirma el *offset*; el repetido lo descarta la *inbox* sin reaplicar; el inválido va al DLT con la causa en las cabeceras; un rechazo de negocio **no** va al DLT y sí produce acuse; y una caída de la base reintenta sin mover el *offset*
- [ ] T015 [P] [US1] Prueba de que el Módulo 2 **no** declara ausencias solo (FR-001, FR-008, escenario 2): sin eventos, una reserva no usada sigue `CONFIRMADA` hasta el fin de su franja y no aparece ninguna fila en `absence`; y no existe ningún `@Scheduled` que mire las reservas pasadas

### Implementation for User Story 1

- [ ] T016 [US1] Implementar `ReceiveNoShowUseCase` con la tabla de las cinco razones: política del plazo, cierre en `reservation_closure`, `INSERT` de la ausencia, paso de la reserva a `FINALIZADA`, y el acuse por `Module3NotifierPort` (depende de T005 a T008)
- [ ] T017 [US1] Implementar `VoidNoShowUseCase` (depende de T016)
- [ ] T018 [US1] Implementar `NoShowListener` con los dos tipos de evento del topic, apoyado en `InboxGuard`, y `NoShowAckEvent` en la *outbox* (depende de T016, T017)
- [ ] T019 [US1] Registrar los beans y las transacciones de UC9 en `UseCasesConfig`, añadir el campo `absence` a `GET /api/reservations/mine` según [Contratos §4](#4-get-apireservationsmine--el-campo-nuevo), y actualizar [modelo-datos-der.md](./modelo-datos-der.md) con la clave primaria nueva de `absence`
- [ ] T020 [P] [US1] Frontend: el tipo `Absence` y la marca en `MyReservationsList.tsx`, con el texto que no promete ni niega sanción (FR-003, FR-010)

**Checkpoint**: UC9 queda funcional de punta a punta

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T021 [P] Verificar SC-001: desde la llegada del evento hasta el recurso liberado, por debajo de 5 minutos, con el consumidor a su concurrencia real
- [ ] T022 [P] Verificar SC-005 con una tanda de reportes atrasados publicados de golpe al "restablecerse" el Módulo 3: todos quedan registrados y los que ya no tienen nada que liberar salen con `resourceReleased: false`
- [ ] T023 [P] Registrar en logs cada reporte recibido con su resultado y su motivo de rechazo, sin el campo `note` ni el `verifiedBy` del Módulo 3
- [ ] T024 Acordar con el Módulo 3 el contrato de los dos eventos de [Contratos §1 y §2](#1-evento-que-consumimos--module3reservationno-showv1), sobre todo el topic de acuse, y llevar a `pendientes-clarificacion.md` lo que quede abierto

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: depende de UC2 (reserva y *outbox*) y de UC11 (`reservation_closure`)
- **Foundational (Phase 2)**: depende de Setup - BLOCKS la user story
- **User Story 1 (Phase 3)**: depende de Foundational
- **Polish (Phase 4)**: depende de la Phase 3

### Dependencias con otros casos de uso

- **UC2 `Reservar recursos`**: es el caso base del `<<extend>>` —sin reserva no hay ausencia— y de ahí sale el plazo de 10 minutos (UC2 FR-010). Aporta la *outbox* por la que sale el acuse.
- **UC11 `Reportar cancelación de reserva`**: creó `reservation_closure`, que es lo que hace cumplir FR-005, FR-006 y SC-003 sin un `if`. UC9 es la otra mitad de UC11 FR-011 y SC-006.
- **UC4 `Cancelar reserva`**: es el cierre contrario. Cancelar a tiempo es la forma de no aparecer aquí, y los dos bordes de 10 minutos —el de cancelar y el de reportar— encajan sin solaparse.
- **UC7 `Actualizar estado de los recursos`**: el `<<include>>` se cumple liberando la ocupación, como en UC2, UC3 y UC4. Una ausencia no le manda nada al Módulo 1 (UC7 FR-012); lo que sí deja es su fila en `status_change` con el motivo `NO_SHOW_REGISTERED`.
- **UC12 `Recibir check-out`**: el otro consumidor de Kafka. `InboxGuard` y `KafkaConsumerConfig` se crean aquí y su plan los reusa; es el único trabajo de este plan que no es de UC9.
- **UC6 `Consultar sanciones`**: el camino de vuelta. Las ausencias que aquí se registran son parte de lo que el Módulo 3 devuelve como cumplimiento, y por eso UC9 no decide nada (FR-003).

### Within User Story 1

- Esquema (T003) → *inbox* y modelo (T005 a T008) → caso de uso (T016) → anulación (T017) → *listener* (T018)
- Beans y API (T019) → frontend (T020)

### Parallel Opportunities

- En Foundational: T004 a T008
- En User Story 1: todas las pruebas (T009 a T015) y el frontend (T020)
- En Polish: T021 a T023

## Notes

- La numeración `T0XX` es propia de este plan
- [P] tasks = different files, no dependencies
- Verify tests pass
- Commit after each task or logical group
- La sección **Contratos** es la única fuente del JSON de UC9
- **Decisiones de los contratos que el spec no fija**: nace el topic de acuse `module2.reservation.no-show-ack.v1`, porque FR-002 y FR-011 piden responderle al Módulo 3 y por Kafka no hay respuesta; un rechazo de negocio **no** va al DLT, porque el evento no está roto; el plazo se mide contra `verifiedAt` y no contra la hora de llegada; el borde de los 10 minutos es inclusivo, igual que el de UC4; un reporte repetido responde `accepted: true` con `duplicate`; y la clave primaria de `absence` pasa a ser `reservation_id`
- **Propuesta para cerrar un pendiente**: una reserva con ausencia queda **`FINALIZADA`**, y la fila de `absence` es la que explica por qué. Así se libera el recurso sin inventar un estado que obligaría a tocar UC1, UC2 y UC11
- **NEEDS CLARIFICATION abiertos en este plan**:
  - **Topic de acuse**: hay que acordarlo con el Módulo 3. Si prefieren un endpoint nuestro, cambia el adaptador y no el caso de uso
  - **FR-010, cómo se informa a la persona**: no hay canal de notificación en ningún spec. Aquí la ausencia se ve en `my-reservations`; si se espera un correo, afecta también a UC3 y UC4
  - **Edge case *Recurso caído durante la franja***: si el recurso se fue a mantenimiento y por eso la persona no pudo usarlo, no debería contar como ausencia suya. Hoy solo se detecta si la reserva ya estaba cancelada por eso, y eso depende de P-20 punto 2
  - **Umbral de ausencias que origina sanción**: lo deja abierto `spec-modulo2.md`. No afecta a este plan, porque la decisión es del Módulo 3
  - **Particiones del topic**: la concurrencia del consumidor se iguala a ellas, y nadie ha fijado cuántas
