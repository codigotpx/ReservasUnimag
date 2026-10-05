# Implementation Plan: Reportar cancelación de reserva (UC11)

**Date**: 2026-10-02
**Spec**: [spec-modulo2-uc11-reportar-cancelacion-reserva.md](../specs/spec-modulo2-uc11-reportar-cancelacion-reserva.md)
**Plan general**: [plan-arquitectura.md](./plan-arquitectura.md) — incluida la [convención de nombres](./plan-arquitectura.md#convención-de-nombres)
**Planes previos**: [UC2](./plan-uc2-reservar-recursos.md) montó la *outbox* y el publicador, [UC3](./plan-uc3-importar-horarios-semestrales.md) creó el evento `ReservationCancelled` y [UC4](./plan-uc4-cancelar-reserva.md) le añadió `noticeMinutes` y los tres orígenes

## Summary

UC11 es un `<<include>>` que no se ve por fuera: le cuenta al Módulo 3 que una reserva se deshizo **y por culpa de quién**. Sin él, el Módulo 3 ve una franja que quedó vacía y no puede distinguir a quien avisó a tiempo de quien no apareció; en el peor caso sanciona a quien hizo bien las cosas.

Los tres planes anteriores ya dejaron funcionando el camino feliz. Lo que falta, y es lo que hace este plan, son **las garantías** que el spec pide y que nadie ha implementado todavía:

**Enfoque técnico:**

1. **El historial de envíos** (FR-010): hoy el resultado de cada envío solo vive en `outbox_message`, que es una tabla de trabajo que se puede purgar. La entidad `CancellationReport` del spec pide una constancia propia y consultable, con el resultado de cada intento.
2. **Que una cancelación y una ausencia no coexistan** (FR-011, SC-006): es una regla entre dos casos de uso y hoy no la comprueba nadie. Se implementa como una restricción en la base, no como un `if`.
3. **El orden de los reportes** (edge case **Orden de los reportes**): dos cancelaciones seguidas sobre el mismo recurso tienen que llegar en el orden en que ocurrieron. La clave de partición por reserva no lo garantiza, porque son **reservas distintas**.
4. **Que ningún reporte se pierda ni se duplique** (FR-007, FR-008, SC-003, SC-005): la *outbox* ya lo da, y este plan lo verifica con las pruebas que lo demuestran, incluida la caída de 30 minutos de SC-005.
5. **Un endpoint de auditoría** para responder la pregunta que FR-010 hace posible: qué se le reportó al Módulo 3 sobre una reserva, y cómo acabó cada intento.

No se toca el formato del evento: el contrato quedó cerrado en UC3 y UC4, y aquí solo se documenta entero en un sitio.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 4.1.1, Spring for Apache Kafka, Flyway, springdoc-openapi. **Ninguna nueva.**
**Storage**: PostgreSQL. UC11 **escribe** la tabla nueva `cancellation_report` y lee `reservation`, `outbox_message` y `absence`.
**Testing**: JUnit 5 y AssertJ, Testcontainers con PostgreSQL (la restricción de exclusión mutua y el historial) y con Kafka (orden, reintentos y la caída prolongada), `@WebMvcTest` (el endpoint de auditoría), ArchUnit
**Target Platform**: Servidor Linux con JVM 21
**Performance Goals**: El reporte llega al Módulo 3 **dentro de los 5 minutos** siguientes a la cancelación (SC-002). Con `reservations.outbox.interval=5s` y la espera creciente del plan general, el primer intento sale en segundos y el presupuesto de 5 minutos aguanta siete reintentos.
**Constraints**:
- Toda cancelación ejecutada se reporta; ningún intento rechazado se reporta (FR-001, FR-009).
- Una misma cancelación no se reporta dos veces, aunque el envío se reintente (FR-007, SC-003).
- El Módulo 2 **no** decide consecuencias (FR-005).
- Cero reportes perdidos ante una caída del Módulo 3 de hasta 30 minutos (SC-005).
- Cero reservas con reporte de cancelación y de ausencia a la vez (FR-011, SC-006).
**Scale/Scope**: Una carga de horarios puede producir decenas o cientos de reportes de una vez (edge case **Cancelación masiva**). Sin frontend: este caso de uso no tiene pantalla, y su único endpoint es de auditoría.

## Project Structure

### Documentation (this feature)

```text
docs/
├── specs/
│   ├── spec-modulo2-uc11-reportar-cancelacion-reserva.md   # spec de este plan
│   ├── spec-modulo2-uc4-cancelar-reserva.md                # quien lo dispara
│   └── spec-modulo2-uc9-recibir-reporte-no-asistencia.md   # el cierre contrario (FR-011)
└── plan/
    ├── plan-uc2-reservar-recursos.md                       # outbox y publicador
    ├── plan-uc3-importar-horarios-semestrales.md           # el evento
    ├── plan-uc4-cancelar-reserva.md                        # los tres orígenes
    └── plan-uc11-reportar-cancelacion-reserva.md           # este archivo
```

### Source Code (repository root)

```text
src/main/java/edu/unimagdalena/reservasunimag/
├── domain/
│   ├── model/
│   │   └── reservation/
│   │       ├── CancellationReport.java         # nuevo: la constancia de FR-010
│   │       └── DeliveryStatus.java             # nuevo: PENDING, SENT, FAILED
│   ├── port/
│   │   ├── in/
│   │   │   └── ReportCancellationPort.java     # el <<include>> explícito
│   │   └── out/
│   │       ├── CancellationReportRepositoryPort.java
│   │       └── Module3NotifierPort.java         # (existe) sin cambios
│   └── usecase/
│       └── reportcancellation/
│           └── ReportCancellationUseCase.java   # arma el reporte y lo encola
│
└── infrastructure/
    ├── adapter/
    │   ├── in/web/reservation/
    │   │   └── CancellationReportController.java   # GET de auditoría
    │   └── out/
    │       ├── persistence/
    │       │   ├── entity/CancellationReportJpa.java
    │       │   ├── repository/CancellationReportJpaRepository.java
    │       │   └── CancellationReportPersistenceAdapter.java
    │       └── messaging/
    │           ├── OutboxPublisher.java            # (existe) + marcar el reporte
    │           └── evento/ReservationCancelledEvent.java   # (existe) sin cambios
    └── config/
        └── UseCasesConfig.java                     # (existe) + el bean de UC11

src/main/resources/db/migration/
└── V5__cancellation_report.sql                     # la tabla y las dos restricciones

src/test/java/edu/unimagdalena/reservasunimag/
├── domain/usecase/reportcancellation/ReportCancellationUseCaseTest.java
└── infrastructure/adapter/
    ├── in/web/reservation/CancellationReportControllerTest.java
    └── out/
        ├── persistence/CancellationReportIT.java
        └── messaging/CancellationDeliveryIT.java
```

**Structure Decision**: aparece `usecase/reportcancellation` aunque el trabajo real lo haga la *outbox*, por la misma razón que UC2 creó `UpdateResourceStatusUseCase`: el `<<include>>` del diagrama tiene que existir en el código, con su puerto de entrada, para que se vea que `CancelReservationUseCase` no puede saltárselo. Sin frontend.

### Decisiones de diseño de este caso de uso

**Dos tablas, dos trabajos distintos.** Es la decisión central del plan, y conviene justificarla porque a primera vista parecen lo mismo:

| | `outbox_message` (UC2) | `cancellation_report` (este plan) |
|---|---|---|
| Para qué | Que el evento salga aunque Kafka esté caído | Que quede constancia de lo que se reportó y cómo acabó |
| Vida | Se puede purgar cuando está `SENT` | Permanente: es la auditoría de FR-010 |
| Qué guarda | El JSON serializado y los intentos | Los datos del negocio: reserva, persona, recurso, origen, antelación |
| Quién la consulta | El publicador | Las personas, por el endpoint de auditoría |

Guardar solo la *outbox* bastaría para que el evento llegue, pero no para responder "qué le reportamos al Módulo 3 sobre esta reserva" seis meses después, que es exactamente lo que FR-010 pide. Y guardar solo el reporte perdería la garantía de entrega. Se escriben **las dos en la misma transacción** que la cancelación, y el `outbox_message.id` queda en el reporte como `event_id`, de modo que se puede ir de uno al otro.

**La exclusión mutua con la ausencia, en la base** (FR-011, SC-006). Una reserva no puede tener a la vez un reporte de cancelación y una ausencia. Ponerlo como una comprobación en el código deja la puerta abierta a que dos caminos concurrentes —una cancelación y un reporte del Módulo 3 que llegan a la vez— pasen los dos. Se resuelve con una restricción que la base sostiene:

```sql
-- la reserva cancelada no admite ausencia, y la ausente no admite cancelación
ALTER TABLE absence ADD CONSTRAINT absence_no_cancellation_report
  CHECK (NOT EXISTS (SELECT 1 FROM cancellation_report WHERE reservation_id = absence.reservation_id));
```

PostgreSQL no admite subconsultas en un `CHECK`, así que la forma real es una **clave única compartida**: las dos tablas apuntan a `reservation_id` y se añade una tercera, `reservation_closure`, con `reservation_id` como clave primaria y el tipo de cierre:

```sql
CREATE TABLE reservation_closure (
  reservation_id uuid PRIMARY KEY REFERENCES reservation (id),
  closure_type   varchar NOT NULL CHECK (closure_type IN ('CANCELLATION', 'ABSENCE')),
  created_at     timestamptz NOT NULL DEFAULT now()
);
```

Quien reporta una cancelación inserta `('CANCELLATION')` y quien registra una ausencia inserta `('ABSENCE')`, dentro de su propia transacción. El segundo choca con la clave primaria y sabe que el cierre ya está tomado. Es el mismo truco de la *inbox* del plan general: una clave primaria haciendo de candado, en vez de un `if` que se puede colar. (Es una decisión de este plan; el spec dice que son excluyentes pero no cómo garantizarlo.)

**El orden de los reportes** (edge case **Orden de los reportes**). El plan general parte las particiones por `reservation_id`, lo que garantiza el orden **de los eventos de una misma reserva** —que la ficha llegue antes que su cancelación—. Pero el edge case habla de otra cosa: dos cancelaciones seguidas **sobre el mismo recurso**, que son dos reservas distintas y por tanto dos particiones distintas, donde Kafka no promete nada.

Lo que sí se puede garantizar, y es lo que importa, es que **cada reporte lleve el instante en que ocurrió** (`cancelledAt`) y que el publicador los saque en orden de `created_at` (`ORDER BY created_at` del plan general). El Módulo 3 ordena por el instante del hecho, no por el de llegada. Ponerlos todos en una sola partición por recurso sería atar nuestro reparto de particiones a una necesidad ajena y perder el orden por reserva, que sí es nuestro.

**La antelación de una cancelación automática no se envía** (edge case **Antelación en la cancelación automática**). `noticeMinutes` **se omite** cuando el origen no es `HOLDER`, en vez de mandar un `0` o el valor calculado. Un número ahí invita a leerlo como mérito o como falta, y el edge case pide justamente que no se pueda. Lo que viaja en su lugar es `attributableToPerson: false`, que es una afirmación y no un número que haya que interpretar. Ya quedó así en [UC4 § Contratos §3](./plan-uc4-cancelar-reserva.md#3-evento-hacia-el-módulo-3); aquí se prueba.

**Un reporte por reserva, también en la cancelación masiva** (edge case **Cancelación masiva**). Una carga de horarios que desplaza cincuenta reservas produce cincuenta eventos y cincuenta filas en `cancellation_report`, no un lote. Agruparlos perdería a quién le tocó, que es el único dato que el Módulo 3 necesita para no sancionar a la persona equivocada. El coste es aceptable: son inserciones en la misma transacción que ya está abierta.

**Los intentos rechazados no dejan rastro aquí** (FR-009, escenario 5). Un `CAN-001` no crea reporte ni evento. Sí queda en el log y, cuando el rechazo es una denegación de reserva, en la tabla `denial` de UC2 —pero esa es otra cosa—. La prueba que lo garantiza comprueba que tras un `409` la *outbox* y `cancellation_report` siguen vacías.

**El resultado del envío se escribe dos veces.** Cuando el publicador logra enviar, marca `outbox_message` como `SENT` y actualiza `cancellation_report.delivery_status`, su `delivered_at` y su contador de intentos. Es una escritura de más por evento, y se acepta porque es lo que convierte el historial en algo útil: sin eso, FR-010 —"el resultado de cada envío"— se quedaría en la tabla que se purga.

## Contratos

Se aplican las **convenciones comunes** de [UC1 § Contratos](./plan-uc1-consultar-recursos.md#contratos).

---

### 1. El evento `ReservationCancelled`

**No se redefine aquí.** El contrato está en [UC3 § Contratos §7](./plan-uc3-importar-horarios-semestrales.md#7-evento-de-cancelación--module2reservationcancellationv1) y lo completó [UC4 § Contratos §3](./plan-uc4-cancelar-reserva.md#3-evento-hacia-el-módulo-3). Lo que fija este plan es la **tabla de las tres formas**, que es lo que UC11 FR-003 exige distinguir y lo que un lector del Módulo 3 necesita en un solo sitio:

| Campo | `HOLDER` | `ACADEMIC_PRIORITY` | `RESOURCE_UNAVAILABLE` |
|---|---|---|---|
| `status` | `CANCELADA` | `CANCELADA_POR_PRIORIDAD_ACADEMICA` | `CANCELADA_POR_RECURSO_NO_DISPONIBLE` |
| `attributableToPerson` | `true` | `false` | `false` |
| `noticeMinutes` | el valor real | **se omite** | **se omite** |
| `reason` | `Cancelada por el titular.` | nombra la clase que desplazó | el motivo del mantenimiento |
| `holder` | siempre | siempre, aunque no sea culpa suya | siempre |
| `releasedTime` | la franja o el periodo completo | igual | igual |

Las tres llevan `eventId`, `cancelledAt` y `resource`. Ninguna lleva consecuencia, sanción ni puntuación: eso lo decide el Módulo 3 (FR-005).

> Si `Cancelar reserva` añade un cuarto origen —por ejemplo el de la sanción retroactiva que `spec-modulo2.md` deja abierto—, tiene que aparecer en esta tabla. Es la nota de trazabilidad del spec.

---

### 2. `GET /api/reservations/{reservationId}/cancellation-report`

La cara consultable de FR-010. Rol `DIRECCION_PROGRAMA`, o el propio titular de la reserva.

**`200 OK`**

```json
{
  "reservationId": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
  "eventId": "d4c9e2f1-7a83-4b60-91c5-2e8d0f3a1b57",
  "cancellationOrigin": "HOLDER",
  "status": "CANCELADA",
  "reason": "Cancelada por el titular.",
  "attributableToPerson": true,
  "noticeMinutes": 1486,
  "cancelledAt": "2026-08-31T09:14:22-05:00",
  "resource": { "id": "ESP-0107", "name": "Sala de Estudio 3", "category": "ESPACIO" },
  "holder": { "code": "2019114045", "name": "Nombre del estudiante" },
  "releasedTime": { "start": "2026-09-01T10:00:00-05:00", "end": "2026-09-01T12:00:00-05:00" },
  "delivery": {
    "status": "SENT",
    "attempts": 2,
    "firstAttemptAt": "2026-08-31T09:14:27-05:00",
    "deliveredAt": "2026-08-31T09:14:58-05:00",
    "lastError": null
  }
}
```

**Decisión: `delivery.lastError` es el único `null` de toda la API.** La convención dice que lo que no aplica se omite, y aquí se rompe a propósito: un `lastError` ausente podría leerse como "no se consultó", y en una pantalla de auditoría la diferencia entre "no hubo error" y "no se sabe" importa. Se documenta para que nadie lo tome como un descuido.

Mientras el reporte no ha salido:

```json
"delivery": {
  "status": "PENDING",
  "attempts": 3,
  "firstAttemptAt": "2026-08-31T09:14:27-05:00",
  "nextAttemptAt": "2026-08-31T09:16:27-05:00",
  "lastError": "Connection refused"
}
```

**Errores**: `404` si la reserva no existe, no está cancelada o no es del titular que pregunta —los tres con el mismo cuerpo, por la misma razón que en UC4—; `403` si quien pregunta no es ni el titular ni Dirección de Programa.

---

### 3. `GET /api/cancellation-reports`

El listado para auditar, solo `DIRECCION_PROGRAMA`. Es lo que permite contestar SC-001 y SC-003 con datos en vez de con fe.

```http
GET /api/cancellation-reports?origin=ACADEMIC_PRIORITY&delivery=FAILED&from=2026-09-01&to=2026-09-30&page=1
```

```json
{
  "reports": [
    {
      "reservationId": "a1b2c3d4-5e6f-4789-9abc-def012345678",
      "eventId": "f2b8c4d1-6e39-4a70-95c8-3d1e7a0f2b56",
      "cancellationOrigin": "ACADEMIC_PRIORITY",
      "status": "CANCELADA_POR_PRIORIDAD_ACADEMICA",
      "attributableToPerson": false,
      "cancelledAt": "2026-09-10T15:47:35-05:00",
      "resource": { "id": "ESP-0301", "name": "Auditorio Menor", "category": "ESPACIO" },
      "holder": { "code": "2019114045", "name": "Nombre del estudiante" },
      "delivery": { "status": "FAILED", "attempts": 10, "lastError": "Connection refused" }
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "total": 1 },
  "summary": { "total": 1, "sent": 0, "pending": 0, "failed": 1 }
}
```

`summary` cuenta **sobre el filtro completo**, no sobre la página, porque la pregunta que se le hace a esta pantalla es "¿se entregó todo?" y la respuesta no puede depender de en qué página esté quien mira. Los filtros `origin`, `delivery`, `from` y `to` son todos opcionales.

---

### 4. La tabla `cancellation_report`

| Columna | Tipo | Nota |
|---|---|---|
| reservation_id | uuid PK, FK | Una cancelación por reserva: la PK es lo que garantiza FR-007 y SC-003 |
| event_id | uuid, único | El `outbox_message.id`; une el historial con la entrega |
| cancellation_origin | varchar | `HOLDER`, `ACADEMIC_PRIORITY`, `RESOURCE_UNAVAILABLE` |
| reservation_status | varchar | El estado final de la reserva |
| reason | varchar | |
| attributable_to_person | boolean | |
| notice_minutes | int, nulo | Nulo salvo en `HOLDER` |
| cancelled_at | timestamptz | El instante del hecho; es por el que se ordena |
| resource_id | varchar | |
| resource_category | varchar | |
| holder_user_id | uuid FK, nulo | Nulo si la reserva era académica y no tenía titular |
| released_starts_at | timestamptz | |
| released_ends_at | timestamptz | |
| delivery_status | varchar | `PENDING`, `SENT`, `FAILED` |
| attempts | int | |
| first_attempt_at | timestamptz, nulo | |
| delivered_at | timestamptz, nulo | |
| last_error | varchar, nulo | |

```sql
CREATE INDEX cancellation_report_delivery
  ON cancellation_report (delivery_status, cancelled_at);
CREATE INDEX cancellation_report_origin
  ON cancellation_report (cancellation_origin, cancelled_at);
```

**La clave primaria es `reservation_id`, no un id propio.** Así la base impide dos reportes de la misma cancelación, que es FR-007 y SC-003, en vez de confiarlo a que nadie llame dos veces. Es el mismo criterio que la *inbox* del plan general.

El nombre del recurso **no se guarda**: no es nuestro (UC1 FR-006). El endpoint de auditoría lo pide al Módulo 1 al responder, y si el inventario no contesta devuelve el `resource.id` sin `name`, como ya hace `GET /api/reservations/mine` en UC2.

---

### 5. Fixtures compartidos

```text
src/test/resources/contratos/
├── api-cancellation-report-sent.json
├── api-cancellation-report-pending.json
├── api-cancellation-reports-list.json
└── event-reservation-cancelled-maintenance.json   # el tercer origen, que faltaba
```

Con este último quedan los tres orígenes como *fixture*: el del titular lo creó UC4, el académico UC3.

---

## Phase 1: Setup

**Purpose**: UC11 no añade dependencias ni parámetros nuevos. El único ajuste es de observabilidad.

- [ ] T001 Añadir al bloque de la *outbox* de `application.properties` el umbral de alerta `reservations.outbox.alert-after=25m`, por debajo de los 30 minutos de SC-005, para que un reporte atascado se note antes de incumplirlo

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T002 Escribir `V5__cancellation_report.sql` con la tabla de [Contratos §4](#4-la-tabla-cancellation_report), sus dos índices, y la tabla `reservation_closure` con su clave primaria y su `CHECK` de tipo
- [ ] T003 [P] Crear `CancellationReport` y `DeliveryStatus` en `domain/model/reservation/`, con los invariantes del spec: `notice_minutes` solo con origen `HOLDER`, y `attributable_to_person` en `false` para los otros dos
- [ ] T004 [P] Definir `ReportCancellationPort` en `domain/port/in/` y `CancellationReportRepositoryPort` en `domain/port/out/`, incluido el registro del cierre en `reservation_closure`
- [ ] T005 [P] Crear la entidad JPA, el repositorio Spring Data y `CancellationReportPersistenceAdapter`, con el `INSERT` que falla por clave primaria repetida y la actualización del resultado del envío

**Checkpoint**: existe dónde guardar la constancia

---

## Phase 3: User Story 1 - Reportar la cancelación con su origen (Priority: P2)

**Goal**: Toda cancelación ejecutada llega al Módulo 3 con la persona, el recurso, el tiempo liberado, su origen y —solo si la hizo el titular— la antelación; ninguna llega dos veces, ninguna se pierde aunque el Módulo 3 esté caído media hora, y ninguna reserva acaba con cancelación y ausencia a la vez.

**Independent Test**: Cancelar una reserva y comprobar contra un simulador del Módulo 3 que llegó el reporte con los cinco datos y el origen correcto; tirar el simulador 30 minutos, cancelar otra, levantarlo y comprobar que el reporte llega; intentar registrar una ausencia sobre una reserva ya cancelada y comprobar que se rechaza.

### Tests for User Story 1

- [ ] T006 [P] [US1] Pruebas en `ReportCancellationUseCaseTest.java`: los escenarios 1 a 3 producen un reporte con su origen, su `status` y su `attributableToPerson`; el escenario 1 lleva la antelación de 90 minutos y los otros dos **no llevan** `noticeMinutes` (edge case **Antelación en la cancelación automática**); y una reserva académica sin titular deja `holder_user_id` nulo sin romper nada
- [ ] T007 [P] [US1] Prueba de que **nada** se reporta cuando la cancelación se rechaza (escenario 5, FR-009): tras un `409` de UC4, `outbox_message` y `cancellation_report` siguen vacías
- [ ] T008 [P] [US1] Prueba `CancellationReportIT.java` con Testcontainers: el reporte y el `outbox_message` se escriben en la misma transacción que la cancelación; un segundo intento de reportar la misma cancelación choca con la clave primaria y no crea una segunda fila (FR-007, SC-003, edge case **Doble cancelación**); y un *rollback* no deja ninguna de las tres filas
- [ ] T009 [P] [US1] Prueba de la exclusión mutua (FR-011, SC-006): registrar una ausencia sobre una reserva ya cancelada falla por `reservation_closure`, y al revés también; y las dos operaciones lanzadas en paralelo dejan exactamente un cierre
- [ ] T010 [P] [US1] Prueba `CancellationDeliveryIT.java` con Testcontainers de Kafka: el evento llega con el contenido del *fixture*; el Módulo 3 caído **30 minutos** no pierde el reporte y se entrega al volver (escenario 4, SC-005); `delivery_status` pasa de `PENDING` a `SENT` con su `delivered_at` y sus `attempts`; y al agotar los intentos queda `FAILED` con su `last_error`
- [ ] T011 [P] [US1] Prueba de la cancelación masiva (edge case): una carga de UC3 que desplaza 50 reservas produce 50 reportes y 50 eventos, uno por titular, sin agrupar; y todos salen ordenados por `cancelled_at`
- [ ] T012 [P] [US1] Prueba `CancellationReportControllerTest.java`: el `200` de los dos estados de entrega contra los *fixtures*, el filtrado y el `summary` del listado, el `404` de una reserva no cancelada, y que el titular ve el suyo pero no el de otra persona

### Implementation for User Story 1

- [ ] T013 [US1] Implementar `ReportCancellationUseCase`: arma el `CancellationReport` desde la cancelación, lo guarda, registra el cierre en `reservation_closure` y encola el evento por `Module3NotifierPort` (depende de T003 a T005)
- [ ] T014 [US1] Hacer que `CancelReservationUseCase` llame a `ReportCancellationPort` dentro de su transacción, de modo que el `<<include>>` no se pueda omitir (FR-001), y comprobarlo con la prueba de T008
- [ ] T015 [US1] Extender `OutboxPublisher` para que, al enviar o al fallar, actualice también `delivery_status`, `attempts`, `first_attempt_at`, `delivered_at` y `last_error` del reporte (FR-010)
- [ ] T016 [US1] Implementar `CancellationReportController` con los dos endpoints de [Contratos §2 y §3](#2-get-apireservationsreservationidcancellation-report), con la autorización por titular o Dirección de Programa y el nombre del recurso pedido al Módulo 1
- [ ] T017 [US1] Registrar el bean y la transacción de UC11 en `UseCasesConfig`

**Checkpoint**: las cinco garantías del spec quedan demostradas por pruebas

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T018 [P] Verificar SC-002 midiendo el tiempo entre la cancelación y la entrega con el Módulo 3 respondiendo con su latencia prometida, y comprobar que queda bajo 5 minutos
- [ ] T019 [P] Registrar en logs cada reporte con su origen y el resultado de cada intento, y emitir una advertencia cuando un `PENDING` pase de `alert-after`
- [ ] T020 [P] Documentar en el README cómo consultar los reportes `FAILED` y reencolarlos a mano
- [ ] T021 Llevarle al Módulo 3 el contrato de la tabla de [Contratos §1](#1-el-evento-reservationcancelled) y la petición de que ordene por `cancelledAt`, y anotar en `pendientes-clarificacion.md` lo que quede abierto

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: depende de UC2 (*outbox*), UC3 (el evento) y UC4 (los tres orígenes y `noticeMinutes`)
- **Foundational (Phase 2)**: depende de Setup - BLOCKS la user story
- **User Story 1 (Phase 3)**: depende de Foundational
- **Polish (Phase 4)**: depende de la Phase 3

### Dependencias con otros casos de uso

- **UC4 `Cancelar reserva`**: es quien lo dispara, y la llamada queda dentro de su transacción (T014). Si UC4 añade un cuarto origen, hay que añadirlo a la tabla de [Contratos §1](#1-el-evento-reservationcancelled) y al `CHECK` de la columna.
- **UC3 `Importar horarios semestrales`**: produce la cancelación masiva del edge case. No hay que tocarlo: ya llama a `CancelReservationPort`.
- **UC2 `Reservar recursos`**: aporta la *outbox* y el publicador. UC11 le añade la actualización del resultado del envío, que es el único cambio en código de UC2.
- **UC9 `Recibir reporte de no asistencia`**: es la otra mitad de FR-011. La tabla `reservation_closure` que se crea aquí es la que su plan tiene que usar al registrar una ausencia; sin eso, SC-006 no se sostiene.
- **UC10 `Reportar información de la reserva`**: el hermano de este caso de uso. Comparten la *outbox* y el patrón; su plan puede reusar `DeliveryStatus` y la idea del historial.
- **UC6 `Consultar sanciones`**: el camino de vuelta. Lo que aquí se reporta es parte de lo que el Módulo 3 devuelve después, y por eso este plan no decide ninguna consecuencia (FR-005).

### Within User Story 1

- Esquema (T002) → modelo (T003) → puertos (T004) → persistencia (T005) → caso de uso (T013) → enganche con UC4 (T014)
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
- Este plan **no redefine el evento**: su contrato vive en UC3 y UC4, y aquí solo se documenta la tabla de las tres formas
- **Decisiones de los contratos que el spec no fija**: `cancellation_report` es una tabla aparte de la *outbox*, con `reservation_id` como clave primaria para que la base garantice FR-007; la exclusión mutua con la ausencia se sostiene con la tabla `reservation_closure` y no con un `if`; `noticeMinutes` se **omite** en las cancelaciones automáticas en vez de enviarse en cero; `delivery.lastError` es el único campo de la API que viaja como `null`; y el `summary` del listado cuenta sobre el filtro y no sobre la página
