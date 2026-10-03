# Implementation Plan: Recibir check-out (UC12)

**Date**: 2026-10-02
**Spec**: [spec-modulo2-uc12-recibir-check-out.md](../specs/spec-modulo2-uc12-recibir-check-out.md)
**Plan general**: [plan-arquitectura.md](./plan-arquitectura.md) — incluida la [convención de nombres](./plan-arquitectura.md#convención-de-nombres)
**Planes previos**: [UC2](./plan-uc2-reservar-recursos.md) (el préstamo y su vencimiento), [UC9](./plan-uc9-recibir-reporte-no-asistencia.md) (el consumidor de Kafka, `InboxGuard` y el patrón del acuse) y [UC11](./plan-uc11-reportar-cancelacion-reserva.md) (`reservation_closure`)

## Summary

UC12 es **lo único que cierra un préstamo**. Sin él ningún activo prestado vuelve nunca a estar disponible: la ocupación de un préstamo no caduca al vencer (UC2 FR-015), así que el recurso se queda apartado para siempre y el cupo de su titular también.

El Módulo 3 recibe el activo, comprueba en qué estado vuelve y nos lo reporta; nosotros cerramos el préstamo, liberamos el periodo y el cupo. También nos reporta la **revisión de un espacio** después de usarlo, que no libera nada —un espacio se libera solo al terminar su franja— pero deja el dictamen registrado.

**Enfoque técnico:**

1. Dos check-out muy distintos por el mismo topic: el de un **activo**, que cierra y libera, y el de un **espacio**, que solo registra un dictamen (FR-012, FR-013). Lo que comparten es la validación, la idempotencia y el acuse.
2. El consumidor reusa entero lo que montó UC9: `KafkaConsumerConfig`, `InboxGuard` y el patrón de acuse por la *outbox*. UC12 no inventa infraestructura de mensajería.
3. **Nunca se cierra un préstamo por iniciativa propia** (FR-006, SC-004), con **una excepción** que el propio spec introduce: el umbral de pérdida de 7 días. Es la única contradicción interna del spec y se resuelve abajo.
4. Lo que el Módulo 2 **no** hace: no calcula mora, no cobra daños, no recibe la descripción del daño y no fija el estado físico del recurso (FR-005, FR-011, FR-004).
5. Dos listas de pendientes que hoy no existen: los préstamos vencidos sin check-out (FR-009, SC-005) y las reservas de espacio terminadas sin revisar (FR-015).

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 4.1.1, Spring for Apache Kafka, Flyway, springdoc-openapi. **Ninguna nueva.**
**Storage**: PostgreSQL. UC12 **escribe** `check_out`, `loan` (la devolución real), `reservation` (estado), `reservation_closure`, `inbox_message` y `outbox_message`, y lee `app_user`.
**Testing**: JUnit 5 y AssertJ, Testcontainers con Kafka (los tres caminos del consumidor) y con PostgreSQL (el cierre, la liberación y la idempotencia), `@WebMvcTest` (las dos listas de pendientes), ArchUnit
**Target Platform**: Servidor Linux con JVM 21
**Performance Goals**: Un activo con check-out procesado vuelve a poder reservarse **en menos de 5 segundos** (SC-002), salvo que el Módulo 1 lo tenga en `EN_MANTENIMIENTO`. Es una transacción local: el presupuesto sobra.
**Constraints**:
- El Módulo 2 no cierra préstamos por su cuenta (FR-006, SC-004), salvo el umbral de pérdida.
- Un check-out no se procesa dos veces (FR-007, SC-003).
- No se calcula mora ni cobro (FR-005); no se guarda la descripción del daño (FR-011); no se fija el estado físico (FR-004).
- El check-out de un espacio **no** cambia la disponibilidad de ninguna franja (FR-013, SC-006).
- Hay que confirmarle al Módulo 3 el resultado, acepte o rechace (FR-008).
**Scale/Scope**: Un check-out por préstamo y uno por reserva de espacio terminada —que son muchas más—. Dos pantallas de Dirección de Programa con las listas de pendientes.

## Project Structure

### Source Code (repository root)

```text
src/main/java/edu/unimagdalena/reservasunimag/
├── domain/
│   ├── model/
│   │   ├── compliance/
│   │   │   ├── CheckOut.java                   # nuevo
│   │   │   ├── CheckOutKind.java               # nuevo: ASSET_RETURN, SPACE_REVIEW
│   │   │   ├── Verdict.java                    # nuevo: SIN_NOVEDAD, REQUIERE_MANTENIMIENTO
│   │   │   └── CheckOutRejection.java          # nuevo: los motivos de FR-008 y FR-014
│   │   └── reservation/
│   │       └── ReservationStatus.java          # (existe) + NO_DEVUELTO_PERDIDO
│   ├── port/
│   │   ├── in/
│   │   │   ├── ReceiveCheckOutPort.java
│   │   │   └── DeclareLoanLostPort.java        # el umbral de 7 días
│   │   └── out/
│   │       ├── CheckOutRepositoryPort.java
│   │       └── LoanRepositoryPort.java         # pendientes y cierre
│   └── usecase/receivecheckout/
│       ├── ReceiveCheckOutUseCase.java
│       ├── CheckOutValidator.java              # las dos tablas de rechazo
│       └── DeclareLoanLostUseCase.java
│
└── infrastructure/
    ├── adapter/
    │   ├── in/
    │   │   ├── messaging/
    │   │   │   ├── CheckOutListener.java       # @KafkaListener
    │   │   │   ├── InboxGuard.java             # (existe, de UC9) se reusa
    │   │   │   └── evento/CheckOutRegisteredEvent.java
    │   │   └── web/loan/
    │   │       ├── PendingReturnsController.java    # FR-009 y FR-015
    │   │       └── PendingResponse.java
    │   └── out/
    │       ├── persistence/
    │       │   ├── entity/CheckOutJpa.java
    │       │   ├── repository/CheckOutJpaRepository.java
    │       │   └── CheckOutPersistenceAdapter.java
    │       └── messaging/
    │           └── evento/CheckOutAckEvent.java
    └── config/
        └── UseCasesConfig.java

src/main/resources/db/migration/
└── V8__check_out.sql

src/test/java/edu/unimagdalena/reservasunimag/
├── domain/usecase/receivecheckout/
│   ├── CheckOutValidatorTest.java
│   ├── ReceiveCheckOutUseCaseTest.java
│   └── DeclareLoanLostUseCaseTest.java
└── infrastructure/adapter/
    ├── in/messaging/CheckOutListenerIT.java
    ├── in/web/loan/PendingReturnsControllerTest.java
    └── out/persistence/CheckOutIT.java

frontend/src/
├── app/(direccion)/pendientes/page.tsx         # las dos listas
└── features/checkout/
    ├── PendingReturns.tsx
    ├── PendingReviews.tsx
    ├── api.ts
    └── types.ts
```

**Structure Decision**: la única carpeta nueva es `features/checkout` en el frontend, con las dos listas de pendientes. Del lado del backend UC12 vive sobre la infraestructura de mensajería que creó UC9: `InboxGuard` y `KafkaConsumerConfig` se reusan tal cual, que es para lo que UC9 los dejó compartidos.

### Decisiones de diseño de este caso de uso

**Dos tipos de check-out, un solo camino.** El `kind` lo determina la categoría del recurso de la reserva, no el evento: así el Módulo 3 no puede equivocarse al etiquetarlo.

| | `ASSET_RETURN` (activo) | `SPACE_REVIEW` (espacio) |
|---|---|---|
| Qué trae | La fecha y hora reales de la devolución | La fecha y hora de la revisión y un dictamen |
| Qué cierra | El préstamo: `returned_at` y la reserva a `FINALIZADA` | Nada que cerrar |
| Libera | El periodo completo del préstamo y el cupo (FR-003) | **Nada** (FR-013) |
| Dictamen | **No** lleva: los daños van del Módulo 3 al Módulo 1 (FR-011) | `SIN_NOVEDAD` o `REQUIERE_MANTENIMIENTO` (FR-012) |
| Umbral de pérdida | Sí, 7 días | **No** se le aplica |

**Por qué el check-out de un espacio no libera nada.** Un espacio se libera solo al cumplirse la hora de fin de su franja, llegue o no la revisión. Si el check-out liberase algo, estaría liberando una franja que ya pasó, lo que no cambia nada, o adelantando una liberación, lo que sería un error. Por eso SC-006 dice "ninguno cambia la disponibilidad de una franja", y la prueba de T011 lo comprueba de la única forma que vale: mirando que la consulta de UC1 dé **lo mismo** antes y después del check-out.

**La validación: ocho motivos de rechazo** (FR-008, FR-014). Un consumidor de Kafka no puede devolver un error, así que cada rechazo produce un acuse, igual que en UC9:

| `rejection` | Cuándo | FR |
|---|---|---|
| `LOAN_NOT_OPEN` | El préstamo ya tiene `returned_at` | FR-008, edge case **Check-out repetido** |
| `RESERVATION_CANCELLED` | La reserva se canceló antes de la entrega | FR-008, edge case |
| `ASSET_NOT_PICKED_UP` | El activo nunca se recogió: `picked_up_at` nulo. No hay devolución posible | decisión de este plan |
| `LOAN_DECLARED_LOST` | El préstamo ya se cerró por el umbral de 7 días | decisión de este plan |
| `SLOT_NOT_FINISHED` | Revisión de un espacio cuya franja no ha terminado | FR-014, edge case |
| `RESERVATION_NOT_USED` | Revisión de un espacio cancelado o con ausencia | FR-014, edge case |
| `VERDICT_REQUIRED` | Revisión de espacio sin dictamen, o activo **con** dictamen | FR-012, FR-011 |
| `ALREADY_REVIEWED` | Ya hay un check-out de esa reserva | FR-007 |

`ALREADY_REVIEWED` y `LOAN_NOT_OPEN` responden `accepted: true` con `duplicate: true`, igual que en UC9: el estado que el Módulo 3 buscaba ya existe. Los otros seis responden `accepted: false`.

**Decisión: un dictamen en un activo es un rechazo, no un dato que se ignora.** FR-011 prohíbe recibir y guardar la descripción del daño de un activo. Lo más fácil sería aceptar el evento y descartar el campo, pero entonces el Módulo 3 nunca sabría que nos está mandando algo que no debe, y el día que lo miren creerán que lo guardamos. Se rechaza con `VERDICT_REQUIRED` —el mismo código, en su variante inversa— y el `detail` lo explica.

**El umbral de pérdida de 7 días: la excepción a FR-006.** El spec dice en FR-006 que el sistema **no debe cerrar un préstamo por su cuenta**, y en su edge case **Devolución que nunca llega** dice que a los 7 días el préstamo se cierra solo con `NO_DEVUELTO_PERDIDO`, el recurso se da de baja y el caso se escala al Módulo 3. Las dos cosas no pueden ser verdad a la vez.

Se lee así: **FR-006 vale mientras el plazo de pérdida no se haya cumplido.** El edge case es más específico y más reciente, y la razón de FR-006 —que el Módulo 2 no puede saber si alguien devolvió algo— deja de aplicar cuando han pasado 7 días del vencimiento: ahí lo que se afirma no es "lo devolvió" sino "esto no va a volver".

Pero el edge case pide **tres cosas que el plan general no soporta**:

| Lo que pide | Problema | Qué hace este plan |
|---|---|---|
| Cerrar con `NO_DEVUELTO_PERDIDO` | Es un estado de reserva que UC2 no tiene | Se añade a `ReservationStatus`, y queda fuera de las `CONFIRMADA`, así que libera la ocupación y el cupo |
| Dar de baja el recurso y **avisarle al Módulo 1** para que lo ponga `DADO_DE_BAJA` | El reparto de estados dice que el Módulo 2 **no le envía nada** al Módulo 1 (P-20 punto 1), y `DADO_DE_BAJA` no es ninguno de sus tres estados | **No se implementa el aviso.** Se registra la baja de nuestro lado y se deja el pendiente escrito |
| Escalar al Módulo 3 con el expediente | Es un evento más, pero no está en los cuatro topics del plan general | Se emite por la *outbox* como `LoanDeclaredLost`, a confirmar con ellos |

**Decisión: el umbral se implementa con un `@Scheduled` diario, no con un reloj por préstamo.** Corre una vez al día, busca los préstamos abiertos cuyo vencimiento tiene más de 7 días y los declara perdidos en una transacción por préstamo. Un trabajo diario es suficiente para un plazo de 7 días, es fácil de probar con un reloj inyectado y no deja temporizadores vivos. Queda desactivable con `reservations.loan.loss-threshold-enabled`, porque es la única parte de UC12 que actúa sin que nadie se lo pida y conviene poder apagarla mientras P-20 esté abierto.

**La diferencia entre lo que pasó y cuándo nos enteramos** (FR-010). Se guardan **dos instantes**: `occurred_at`, que es cuando el activo volvió o el espacio se revisó, y `received_at`, que es cuando nos llegó el evento. Todo lo que es negocio —el retraso, el orden, el edge case del check-out a las 22:00 en punto— se mide contra `occurred_at`. `received_at` existe solo para auditar el desfase. Es la misma separación que UC9 hace con `verified_at` y `reported_at`.

**El check-out a las 22:00 en punto** (edge case). El criterio lo aplica el Módulo 3, no nosotros: lo único que este plan garantiza es que el dato permita distinguirlo sin ambigüedad. Por eso `occurred_at` se guarda como `timestamptz` con la precisión que llegue, **sin redondear a minutos ni a días**, y el acuse lo devuelve tal cual. Una devolución a las 22:00:00 y otra a las 22:00:01 son distinguibles, y quién llega tarde lo decide quien calcula la mora.

**Las dos listas de pendientes** (FR-009, FR-015). Son consultas, no tablas: un préstamo pendiente es uno `CONFIRMADA` con `picked_up_at` y sin `returned_at` cuyo vencimiento ya pasó; una reserva de espacio pendiente de revisión es una `FINALIZADA` sin fila en `check_out` cuya franja ya terminó. Nada que mantener, nada que pueda desincronizarse. La segunda lista va a ser **grande** —cada clase y cada reserva de espacio del semestre aparece ahí si nadie revisa—, así que se pagina y se ordena por antigüedad, y la pantalla avisa de que la revisión de espacios es opcional.

## Contratos

Se aplican las **convenciones comunes** de [UC1 § Contratos](./plan-uc1-consultar-recursos.md#contratos).

---

### 1. Evento que consumimos — `module3.reservation.check-out.v1`

**Devolución de un activo** (FR-001):

```json
{
  "eventId": "7b3e9c41-5a28-4f60-93d7-1e8a0c2f5b64",
  "type": "CheckOutRegistered",
  "version": 1,
  "occurredAt": "2026-09-09T16:42:11-05:00",
  "data": {
    "reservationId": "4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3",
    "occurredAt": "2026-09-09T16:40:00-05:00"
  }
}
```

**Revisión de un espacio** (FR-012):

```json
{
  "eventId": "a5f2d738-9b61-4c05-87ea-3d0c1b9f4e26",
  "type": "CheckOutRegistered",
  "version": 1,
  "occurredAt": "2026-09-01T12:15:40-05:00",
  "data": {
    "reservationId": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
    "occurredAt": "2026-09-01T12:10:00-05:00",
    "verdict": "REQUIERE_MANTENIMIENTO"
  }
}
```

| Campo | Obligatorio | Nota |
|---|---|---|
| `reservationId` | sí | Si no existe, el evento va al DLT. |
| `data.occurredAt` | sí | **Cuándo volvió el activo o se revisó el espacio.** Es el dato de negocio, distinto del `occurredAt` del envoltorio, que es cuando el Módulo 3 emitió el evento. |
| `verdict` | solo en espacios | `SIN_NOVEDAD` o `REQUIERE_MANTENIMIENTO`. En un activo, su presencia es un rechazo (FR-011). |

**No se acepta ningún campo de daños.** Si el evento trae una descripción del daño de un activo, se rechaza: esa información va del Módulo 3 al Módulo 1 y no pasa por aquí (FR-011).

---

### 2. Evento que publicamos — el acuse

Por el topic de acuse que creó UC9, `module2.reservation.no-show-ack.v1`, no: **los acuses de check-out van por su propio topic**, porque son de otro hecho y el Módulo 3 puede querer consumirlos por separado.

| Topic | Evento | Quién |
|---|---|---|
| `module2.reservation.check-out-ack.v1` | `CheckOutAcknowledged` | publicamos |

**Aceptado, activo**:

```json
{
  "eventId": "e1c8b350-7d24-4a96-b0f3-5c2e9a1d7b48",
  "type": "CheckOutAcknowledged",
  "version": 1,
  "occurredAt": "2026-09-09T16:42:14-05:00",
  "data": {
    "reservationId": "4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3",
    "sourceEventId": "7b3e9c41-5a28-4f60-93d7-1e8a0c2f5b64",
    "kind": "ASSET_RETURN",
    "accepted": true,
    "occurredAt": "2026-09-09T16:40:00-05:00",
    "receivedAt": "2026-09-09T16:42:13-05:00",
    "dueAt": "2026-09-10T22:00:00-05:00",
    "loanClosed": true,
    "quotaReleased": true
  }
}
```

`dueAt` va en el acuse **a propósito**, aunque el Módulo 3 ya lo recibió en la ficha de UC10: es la fecha contra la que va a medir la mora, y devolverla con la devolución le deja los dos datos juntos sin tener que cruzarlos. Si el préstamo se renovó, es el vencimiento vigente.

**Aceptado, espacio**:

```json
{
  "data": {
    "reservationId": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
    "sourceEventId": "a5f2d738-9b61-4c05-87ea-3d0c1b9f4e26",
    "kind": "SPACE_REVIEW",
    "accepted": true,
    "occurredAt": "2026-09-01T12:10:00-05:00",
    "receivedAt": "2026-09-01T12:15:42-05:00",
    "verdict": "REQUIERE_MANTENIMIENTO",
    "verdictRecorded": true,
    "slotReleased": false
  }
}
```

`slotReleased` es **siempre `false`** en un espacio, y está en el contrato para que quede dicho: la franja no se libera con la revisión (FR-013, SC-006).

**Rechazado**:

```json
{
  "data": {
    "reservationId": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
    "sourceEventId": "c4b1a826-3e57-4d09-96fa-8d2e0b7c1f35",
    "accepted": false,
    "rejection": "SLOT_NOT_FINISHED",
    "detail": "La franja de esta reserva termina a las 12:00. La revisión se admite después de esa hora.",
    "acceptedFrom": "2026-09-01T12:00:00-05:00"
  }
}
```

Los ocho `rejection` son los de la tabla de decisiones. `acceptedFrom` solo aparece en `SLOT_NOT_FINISHED`, por la misma razón que en UC9: que el Módulo 3 no tenga que replicar la fórmula.

**Pérdida declarada** — el tercer evento, por el topic de la ficha:

```json
{
  "eventId": "f6a3c914-8b50-4e27-83dc-1f9b0d2e5a76",
  "type": "LoanDeclaredLost",
  "version": 1,
  "occurredAt": "2026-09-17T03:00:00-05:00",
  "data": {
    "reservationId": "4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3",
    "status": "NO_DEVUELTO_PERDIDO",
    "dueAt": "2026-09-10T22:00:00-05:00",
    "declaredAt": "2026-09-17T03:00:00-05:00",
    "overdueDays": 7,
    "resource": { "id": "ACT-004512", "assetTag": "ACT-004512", "category": "ACTIVO" },
    "holder": { "userId": "5f1b9c2d-7a34-4e81-b0f6-3c8d1e9a4b72", "code": "2019114045", "name": "Nombre del estudiante" }
  }
}
```

Es el expediente que pide el edge case: persona, recurso, placa y días de mora. No lleva valoración económica ni propone sanción: eso lo decide el Módulo 3 (FR-005). (NEEDS CLARIFICATION: este evento no está en los cuatro topics del plan general y hay que acordarlo.)

---

### 3. `GET /api/loans/pending-returns`

FR-009 y SC-005. Rol `DIRECCION_PROGRAMA`.

```json
{
  "loans": [
    {
      "reservationId": "4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3",
      "resourceId": "ACT-004512",
      "resourceName": "Libro de Cálculo I",
      "holder": { "code": "2019114045", "name": "Nombre del estudiante" },
      "pickup": "2026-09-01T14:30:00-05:00",
      "dueAt": "2026-09-10T22:00:00-05:00",
      "overdueDays": 3,
      "renewed": false,
      "lossThresholdAt": "2026-09-17T22:00:00-05:00"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "total": 1 },
  "summary": { "total": 1, "overdue": 1, "nearLossThreshold": 0 }
}
```

`lossThresholdAt` es cuándo este préstamo se declararía perdido, y `summary.nearLossThreshold` cuenta los que están a menos de 48 horas. Es lo que convierte la lista en algo accionable en vez de un inventario de problemas.

Orden: por `dueAt` ascendente, los más atrasados primero.

---

### 4. `GET /api/reservations/pending-reviews`

FR-015. Rol `DIRECCION_PROGRAMA`. Las reservas de espacio terminadas sin check-out.

```json
{
  "reservations": [
    {
      "reservationId": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
      "resourceId": "ESP-0107",
      "resourceName": "Sala de Estudio 3",
      "origin": "ESTUDIANTIL",
      "holder": { "code": "2019114045", "name": "Nombre del estudiante" },
      "start": "2026-09-01T10:00:00-05:00",
      "end": "2026-09-01T12:00:00-05:00",
      "finishedDaysAgo": 2
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 134, "total": 2671 },
  "summary": { "total": 2671, "student": 2104, "academic": 567 }
}
```

El `total` de 2671 del ejemplo no es un error: **esta lista crece con cada reserva que termina**, y si nadie revisa espacios, crece sin parar. Por eso se pagina siempre y el `summary` separa las estudiantiles de las académicas. La pantalla deja claro que la revisión de espacios es opcional y que la franja ya se liberó sola.

Filtros opcionales: `from`, `to`, `origin` y `resourceId`.

---

### 5. La tabla `check_out`

Extiende la del plan general con lo que FR-010 y FR-012 piden.

| Columna | Tipo | Nota |
|---|---|---|
| reservation_id | uuid **PK**, FK | Era `id` con un único; pasa a ser la clave primaria: un check-out por reserva (FR-007, SC-003) |
| kind | varchar | `ASSET_RETURN`, `SPACE_REVIEW` |
| source_event_id | uuid, único | El `eventId` del Módulo 3 |
| occurred_at | timestamptz | Cuándo volvió el activo o se revisó el espacio |
| received_at | timestamptz | Cuándo nos llegó (FR-010) |
| verdict | varchar, nulo | Solo en `SPACE_REVIEW` (FR-012) |
| due_at_snapshot | timestamptz, nulo | El vencimiento vigente al cerrar; solo en activos |

```sql
ALTER TABLE check_out ADD CONSTRAINT check_out_verdict_by_kind
  CHECK ((kind = 'SPACE_REVIEW') = (verdict IS NOT NULL));
CREATE INDEX check_out_received ON check_out (received_at);
```

El `CHECK` es lo que hace imposible guardar un dictamen en un activo (FR-011) y un espacio sin dictamen (FR-012), en vez de confiarlo a la validación. `due_at_snapshot` guarda contra qué fecha se cerró: si más adelante alguien cambia el vencimiento, la auditoría conserva la que valía.

**No hay ninguna columna de daños**, y el `CHECK` del dictamen limita los valores a los dos de FR-012. Es la forma de que FR-011 no dependa de que nadie añada un campo por descuido.

**Cambio respecto al plan general**: `check_out.id` desaparece y la clave primaria pasa a ser `reservation_id`; se añaden `kind`, `source_event_id` y `due_at_snapshot`. Hay que actualizar [modelo-datos-der.md](./modelo-datos-der.md) (T020).

---

### 6. Fixtures compartidos

```text
src/test/resources/contratos/
├── event-check-out-asset.json
├── event-check-out-space.json
├── event-check-out-asset-with-verdict.json    # el que debe rechazarse (FR-011)
├── event-check-out-ack-asset.json
├── event-check-out-ack-space.json
├── event-check-out-ack-rejected.json
├── event-loan-declared-lost.json
├── api-pending-returns.json
└── api-pending-reviews.json
```

---

## Phase 1: Setup

- [ ] T001 Añadir a `application.properties` el topic `reservations.kafka.topic.check-out-ack=module2.reservation.check-out-ack.v1`, el umbral `reservations.loan.loss-threshold=7d`, su interruptor `reservations.loan.loss-threshold-enabled=true` y la hora del trabajo diario, y extenderlo todo en `ReservationProperties`
- [ ] T002 [P] Añadir los dos eventos nuevos —el acuse de check-out y `LoanDeclaredLost`— a la tabla de topics de [plan-arquitectura.md](./plan-arquitectura.md#mensajería-con-kafka)

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T003 Escribir `V8__check_out.sql` con la tabla de [Contratos §5](#5-la-tabla-check_out): `reservation_id` como clave primaria, `kind`, `source_event_id` único, los dos instantes, el `CHECK` del dictamen por tipo y el índice
- [ ] T004 [P] Añadir `NO_DEVUELTO_PERDIDO` a `ReservationStatus`, fuera del conjunto que la restricción de exclusión considera ocupado, y documentar la transición desde `CONFIRMADA`
- [ ] T005 [P] Crear `CheckOut`, `CheckOutKind`, `Verdict` y `CheckOutRejection` en `domain/model/compliance/`
- [ ] T006 [P] Definir `ReceiveCheckOutPort` y `DeclareLoanLostPort` en `domain/port/in/`, y `CheckOutRepositoryPort` y `LoanRepositoryPort` en `domain/port/out/`, con las dos consultas de pendientes
- [ ] T007 [P] Crear la entidad JPA, el repositorio y `CheckOutPersistenceAdapter`, con el `INSERT` que falla por clave repetida y las dos consultas paginadas

**Checkpoint**: existe dónde registrar el check-out

---

## Phase 3: User Story 1 - Cerrar el préstamo con el check-out del Módulo 3 (Priority: P3)

**Goal**: Cuando el Módulo 3 reporta que un activo volvió, el préstamo se cierra, el periodo y el cupo se liberan y el activo vuelve a poder reservarse; cuando reporta la revisión de un espacio, el dictamen queda registrado sin tocar ninguna franja. Lo que no es válido se rechaza con su motivo, y en los dos casos el Módulo 3 recibe el acuse.

**Independent Test**: Con el perfil local y Kafka, crear un préstamo, marcarlo como entregado, publicar el evento de check-out y comprobar que la reserva quedó `FINALIZADA`, que el activo aparece disponible en UC1 y que el cupo bajó; publicar la revisión de un espacio terminado y comprobar que quedó el dictamen y que la consulta de UC1 **no cambió**; publicarla antes del fin de la franja y comprobar el rechazo.

### Tests for User Story 1

- [ ] T008 [P] [US1] Pruebas en `CheckOutValidatorTest.java`: un caso por cada uno de los ocho `rejection` de la tabla; que `ALREADY_REVIEWED` y `LOAN_NOT_OPEN` responden `accepted: true` con `duplicate`; y que un activo **con** dictamen se rechaza en vez de ignorarse el campo (FR-011)
- [ ] T009 [P] [US1] Pruebas en `ReceiveCheckOutUseCaseTest.java` para un activo: cierra el préstamo con `returned_at`, pasa la reserva a `FINALIZADA`, libera el cupo (FR-003), guarda los dos instantes y el `due_at_snapshot`, y el acuse lleva `dueAt`, `loanClosed` y `quotaReleased`; el check-out el mismo día de la entrega se procesa igual (edge case); y **no** se calcula ninguna mora (FR-005)
- [ ] T010 [P] [US1] Pruebas para un espacio: registra el dictamen, el acuse lleva `slotReleased: false`, y los rechazos de FR-014 —franja sin terminar, reserva cancelada y reserva con ausencia— con su motivo
- [ ] T011 [P] [US1] Prueba de SC-006: la consulta de UC1 sobre la franja de un espacio devuelve **exactamente lo mismo** antes y después de su check-out, con los dos dictámenes
- [ ] T012 [P] [US1] Prueba `CheckOutIT.java` con Testcontainers: tras el check-out de un activo **otra persona puede reservarlo** (SC-001, SC-002); un check-out repetido no cierra dos veces ni libera dos veces el cupo (FR-007, SC-003, edge case); el `CHECK` del dictamen rechaza un activo con dictamen y un espacio sin él; y un *rollback* no deja ninguna fila
- [ ] T013 [P] [US1] Prueba `CheckOutListenerIT.java` con Testcontainers de Kafka, contra los *fixtures*: el evento nuevo se procesa; el repetido lo descarta la *inbox*; el que no tiene `reservationId` va al DLT; un rechazo de negocio **no** va al DLT y produce acuse; y el `data.occurredAt` se usa para el negocio y el del envoltorio solo para el log
- [ ] T014 [P] [US1] Prueba de FR-006 y SC-004: sin evento de check-out, un préstamo vencido sigue abierto, sigue ocupando su periodo y sigue contando cupo, por mucho que pase el tiempo —con el umbral de pérdida desactivado
- [ ] T015 [P] [US1] Prueba `PendingReturnsControllerTest.java`: la lista de préstamos pendientes incluye los vencidos sin check-out y **no** los devueltos ni los nunca recogidos (FR-009, SC-005), con su `overdueDays` y su `lossThresholdAt`; la de revisiones pendientes incluye las reservas de espacio terminadas sin check-out y separa origen en el `summary` (FR-015); y las dos exigen rol `DIRECCION_PROGRAMA`

### Implementation for User Story 1

- [ ] T016 [US1] Implementar `CheckOutValidator` con las dos tablas de rechazo, resolviendo el `kind` desde la categoría del recurso y no desde el evento (depende de T005)
- [ ] T017 [US1] Implementar `ReceiveCheckOutUseCase`: validación, `InboxGuard`, `INSERT` del check-out, cierre del préstamo y de la reserva en los activos, registro del dictamen en los espacios, y el acuse por la *outbox* (depende de T007, T016)
- [ ] T018 [US1] Implementar `CheckOutListener` reusando `KafkaConsumerConfig` e `InboxGuard` de UC9, y `CheckOutAckEvent` (depende de T017)
- [ ] T019 [US1] Implementar `PendingReturnsController` con los dos endpoints de [Contratos §3 y §4](#3-get-apiloanspending-returns), paginados y con el nombre del recurso pedido al Módulo 1
- [ ] T020 [US1] Registrar los beans y las transacciones de UC12 en `UseCasesConfig`, y actualizar [modelo-datos-der.md](./modelo-datos-der.md) con la clave primaria y las columnas nuevas de `check_out`
- [ ] T021 [P] [US1] Frontend: `features/checkout/types.ts` y `api.ts`, `PendingReturns.tsx` con el aviso de los que están cerca del umbral, y `PendingReviews.tsx` que deja claro que la revisión es opcional
- [ ] T022 [US1] Frontend: `app/(direccion)/pendientes/page.tsx` con las dos listas en pestañas

**Checkpoint**: el check-out cierra préstamos y registra revisiones; UC12 es demostrable

---

## Phase 4: Umbral de pérdida de 7 días

**Purpose**: La excepción a FR-006 que introduce el edge case **Devolución que nunca llega**. Va en su propia fase porque es lo único de UC12 que actúa sin que nadie lo pida, y porque depende de pendientes abiertos.

- [ ] T023 [P] Pruebas en `DeclareLoanLostUseCaseTest.java` con reloj inyectado: un préstamo con 7 días de mora se declara perdido y uno con 6 no; el estado queda `NO_DEVUELTO_PERDIDO`; la ocupación y el cupo se liberan; se emite `LoanDeclaredLost` con la placa y los días de mora; y un check-out que llegue **después** se rechaza con `LOAN_DECLARED_LOST`
- [ ] T024 Implementar `DeclareLoanLostUseCase` y el trabajo `@Scheduled` diario, con una transacción por préstamo para que uno que falle no detenga a los demás, y con el interruptor de `reservations.loan.loss-threshold-enabled`
- [ ] T025 Registrar la baja lógica del recurso de nuestro lado —que deje de ofrecerse en UC1— **sin** avisar al Módulo 1, y dejar el pendiente escrito en el plan y en `pendientes-clarificacion.md`

**Checkpoint**: el spec de UC12 queda cubierto, con sus pendientes explícitos

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T026 [P] Verificar SC-002: desde el check-out hasta que el activo se puede reservar, por debajo de 5 segundos
- [ ] T027 [P] Verificar SC-004 y SC-005 de punta a punta: ningún préstamo cerrado sin su check-out, y todos los vencidos sin check-out presentes en la lista de pendientes
- [ ] T028 [P] Registrar en logs cada check-out con su tipo, su resultado y el desfase entre `occurred_at` y `received_at`, sin el dictamen de los espacios en claro
- [ ] T029 Acordar con el Módulo 3 los contratos de [Contratos §1 y §2](#1-evento-que-consumimos--module3reservationcheck-outv1), incluidos el topic de acuse y `LoanDeclaredLost`, y llevar a `pendientes-clarificacion.md` lo que quede abierto

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: depende de UC2 (préstamo y vencimiento), UC9 (`KafkaConsumerConfig` e `InboxGuard`) y UC11 (`reservation_closure`)
- **Foundational (Phase 2)**: depende de Setup - BLOCKS la user story
- **User Story 1 (Phase 3)**: depende de Foundational
- **Umbral de pérdida (Phase 4)**: depende de la Phase 3 y se puede dejar para después de la demo
- **Polish (Phase 5)**: depende de las Phases 3 y 4

### Dependencias con otros casos de uso

- **UC2 `Reservar recursos`**: aporta el préstamo, su vencimiento y el cupo. UC12 es lo que cierra el ciclo que UC2 abre: sin él, UC2 FR-015 deja los activos ocupados para siempre.
- **UC9 `Recibir reporte de no asistencia`**: montó el consumidor de Kafka, `InboxGuard` y el patrón de acuse, y UC12 los reusa tal cual. Además una reserva con ausencia **no** admite revisión de espacio (FR-014), y eso se comprueba con `reservation_closure`.
- **UC11 `Reportar cancelación de reserva`**: una reserva cancelada no admite check-out, ni de activo ni de espacio (FR-008, FR-014).
- **UC10 `Reportar información de la reserva`**: informa el vencimiento contra el que el Módulo 3 mide la mora. Si su FR-003 no se implementa, un préstamo renovado se mide contra la fecha vieja y el acuse de UC12 sería el único sitio donde llega la correcta.
- **UC4 `Cancelar reserva`**: su FR-008 se niega a cancelar un activo ya entregado y manda a la persona a devolverlo, es decir aquí.
- **UC1 `Consultar recursos`**: es donde se ve el efecto. No hay que tocarlo, salvo que la baja lógica de un recurso perdido (T025) tiene que dejar de ofrecerlo.
- **UC7 `Actualizar estado de los recursos`**: el `<<include>>` de FR-004 se cumple liberando el periodo. UC12 **no** fija el estado físico: eso es del Módulo 1.

### Within User Story 1

- Esquema (T003) → estado y modelo (T004, T005) → puertos (T006) → persistencia (T007) → validador (T016) → caso de uso (T017) → *listener* (T018)
- Endpoints (T019) y beans (T020) → frontend (T021, T022)

### Parallel Opportunities

- En Foundational: T004 a T007
- En User Story 1: todas las pruebas (T008 a T015) y el frontend de tipos (T021)
- En Polish: T026 a T028

## Notes

- La numeración `T0XX` es propia de este plan
- [P] tasks = different files, no dependencies
- Verify tests pass
- Commit after each task or logical group
- La sección **Contratos** es la única fuente del JSON de UC12
- **Decisiones de los contratos que el spec no fija**: el `kind` lo decide la categoría del recurso y no el evento; un dictamen en un activo se **rechaza** en vez de ignorarse, para que el Módulo 3 sepa que no debe mandarlo; los acuses de check-out van por su propio topic y no por el de UC9; el acuse devuelve `dueAt` para que la mora se mida con los dos datos juntos; `slotReleased: false` va explícito en los espacios; el `CHECK` de la tabla es lo que hace imposible guardar un dictamen donde no toca; y el umbral de pérdida corre con un trabajo diario, apagable
- **La contradicción del spec**: FR-006 dice que el sistema no cierra préstamos por su cuenta y el edge case **Devolución que nunca llega** dice que a los 7 días sí. Se resuelve leyendo FR-006 como válido hasta que ese plazo se cumple, y la Phase 4 queda aislada y desactivable
- **NEEDS CLARIFICATION abiertos en este plan**:
  - **Aviso al Módulo 1 por un recurso perdido**: el edge case pide que le avisemos para que lo ponga `DADO_DE_BAJA`, pero el reparto de estados dice que no le enviamos nada y ese estado no es ninguno de sus tres (P-20 punto 1). **No se implementa el aviso**; la baja queda solo de nuestro lado
  - **`LoanDeclaredLost` y el topic de acuse**: ninguno de los dos está en los cuatro topics del plan general y hay que acordarlos con el Módulo 3
  - **El dictamen `REQUIERE_MANTENIMIENTO` no hace nada**: queda registrado, pero quién pone el espacio en mantenimiento y qué pasa con sus reservas siguientes sigue siendo P-20 punto 2. Es el `NEEDS CLARIFICATION` que el propio spec marca en su último edge case
  - **El umbral de 7 días es calendario**: el edge case dice "7 días calendario", así que no usa `BusinessCalendar`. Conviene confirmarlo, porque el plazo del préstamo sí es en días hábiles
  - **La revisión de espacios es opcional**: nadie ha dicho que el Módulo 3 vaya a revisar todos los espacios, y la lista de FR-015 crecerá sin límite si no lo hace. Hay que acordar si es un proceso real o solo una posibilidad
