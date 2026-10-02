# Implementation Plan: Cancelar reserva (UC4)

**Date**: 2026-10-02
**Spec**: [spec-modulo2-uc4-cancelar-reserva.md](../specs/spec-modulo2-uc4-cancelar-reserva.md)
**Plan general**: [plan-arquitectura.md](./plan-arquitectura.md) — incluida la [convención de nombres](./plan-arquitectura.md#convención-de-nombres)
**Planes previos**: [UC1](./plan-uc1-consultar-recursos.md) (base compartida), [UC2](./plan-uc2-reservar-recursos.md) (la reserva que aquí se cancela) y [UC3](./plan-uc3-importar-horarios-semestrales.md), que ya dejó montada **la rama académica** de este caso de uso

## Summary

UC4 cierra el ciclo de vida de la reserva. Es la principal fuente de liberación de disponibilidad: sin él el sistema funciona, pero la ocupación real se degrada con reservas que nadie llegó a usar.

El plan de UC3 ya implementó `CancelReservationUseCase` para la rama que no pide nadie —la prioridad académica—. Este plan **completa las otras dos**: la cancelación que hace el titular, con su plazo y su código de error, y la que dispara el mantenimiento de un recurso.

**Enfoque técnico:**

1. Las tres ramas entran por el mismo caso de uso con un `CancellationOrigin` distinto (`HOLDER`, `ACADEMIC_PRIORITY`, `RESOURCE_UNAVAILABLE`), porque lo que hacen después es idéntico: liberar la ocupación, soltar el cupo, dejar auditoría y encolar el reporte al Módulo 3 (FR-002, FR-006, FR-009).
2. Lo único propio de la rama del titular son **dos comprobaciones**: que quien pide sea el titular y que la reserva siga siendo cancelable, es decir que falten al menos 10 minutos para el inicio —o para la hora de recogida si es un activo— (FR-001, FR-003, FR-007).
3. Un **activo ya entregado no se cancela** (FR-008): lo que corresponde es devolverlo, y ese cierre entra por UC12. Es la única regla que mira `loan.picked_up_at`.
4. Cancelar es **liberar la ocupación entera**, no un trozo: la franja completa de un espacio y el periodo completo de un préstamo, desde la recogida prevista hasta el vencimiento (FR-002, escenario 2).
5. La cancelación es **idempotente**: una segunda petición sobre una reserva ya cancelada responde `200` con el estado que ya tenía y no libera el cupo dos veces (edge case **Doble cancelación**).
6. El mantenimiento de un recurso cancela sus reservas futuras con `CANCELADA_POR_RECURSO_NO_DISPONIBLE` y sin penalizar a nadie (FR-010). Hoy **nadie nos avisa** de ese cambio de estado: esa es la mitad de P-20 que sigue abierta, así que el plan implementa la operación y la deja disparable a mano hasta que exista el aviso.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript con Next.js y React (frontend)
**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Validation, RestClient), Spring for Apache Kafka, Flyway, springdoc-openapi. **Ninguna nueva.**
**Storage**: PostgreSQL. UC4 **escribe** `reservation` (estado, motivo y marca de cancelación) y `outbox_message`, y lee `loan`, `app_user` y `academic_block`.
**Testing**: JUnit 5 y AssertJ (reglas y caso de uso con puertos falsos), `@WebMvcTest` (controlador), Testcontainers con PostgreSQL (idempotencia y liberación de la ocupación bajo concurrencia), ArchUnit
**Target Platform**: Servidor Linux con JVM 21; navegador web para el frontend
**Performance Goals**: Una franja cancelada vuelve a aparecer como `DISPONIBLE` en la consulta de UC1 en menos de 5 s (SC-001). Como UC1 lee el estado en vivo de la base, eso se cumple si la transacción de cancelación confirma; no hay caché que invalidar.
**Constraints**:
- Antelación mínima de **10 minutos**, parametrizable (FR-007).
- Un activo ya entregado no se puede cancelar (FR-008).
- Las cancelaciones que no son del titular **no penalizan** (FR-005, SC-003).
- Toda cancelación ejecutada se reporta al Módulo 3, sin excepción (FR-009).
- Los intentos rechazados **no** se reportan (UC11 FR-009).
- Zona horaria `America/Bogota`.
**Scale/Scope**: Mismo orden que UC2. Una pantalla: el botón de cancelar sobre la lista `my-reservations` que UC2 creó.

## Project Structure

### Documentation (this feature)

```text
docs/
├── specs/
│   ├── spec-modulo2-uc4-cancelar-reserva.md                  # spec de este plan
│   ├── spec-modulo2-uc11-reportar-cancelacion-reserva.md     # <<include>>: su plan lo completa
│   └── spec-modulo2-uc7-actualizar-estado-recursos.md        # <<include>>: libera la ocupación
└── plan/
    ├── plan-arquitectura.md
    ├── plan-uc2-reservar-recursos.md       # crea la reserva y la pantalla my-reservations
    ├── plan-uc3-importar-horarios-semestrales.md   # dejó hecha la rama académica
    └── plan-uc4-cancelar-reserva.md        # este archivo
```

### Source Code (repository root)

```text
src/main/java/edu/unimagdalena/reservasunimag/
├── domain/
│   ├── model/
│   │   ├── reservation/
│   │   │   ├── CancellationOrigin.java         # nuevo: HOLDER, ACADEMIC_PRIORITY,
│   │   │   │                                   #        RESOURCE_UNAVAILABLE
│   │   │   ├── CancellationPolicy.java         # nuevo: la regla de los 10 minutos
│   │   │   ├── Cancellation.java               # nuevo: origen, motivo, antelación, instante
│   │   │   ├── Reservation.java                # (existe) + transiciones de cancelación
│   │   │   └── CancellationReason.java         # (existe, de UC3)
│   │   └── error/
│   │       ├── DenialCode.java                 # (existe) + CAN-001 y CAN-002
│   │       └── CancellationDeniedException.java
│   ├── port/
│   │   ├── in/
│   │   │   ├── CancelReservationPort.java      # (existe, de UC3) + la rama del titular
│   │   │   └── CancelForMaintenancePort.java   # FR-010
│   │   └── out/
│   │       └── ReservationRepositoryPort.java  # (existe) + cancelar con bloqueo de fila
│   └── usecase/
│       └── cancelreservation/
│           ├── CancelReservationUseCase.java   # (existe, de UC3) se completa aquí
│           └── CancelForMaintenanceUseCase.java
│
└── infrastructure/
    ├── adapter/
    │   ├── in/web/reservation/
    │   │   ├── ReservationController.java      # (existe) + DELETE /api/reservations/{id}
    │   │   └── CancellationResponse.java
    │   └── out/persistence/
    │       └── ReservationPersistenceAdapter.java   # (existe) + la transacción de cancelar
    └── config/
        ├── ReservationProperties.java          # (existe) + min-cancellation-notice
        └── UseCasesConfig.java                 # (existe) + los beans de UC4

src/test/java/edu/unimagdalena/reservasunimag/
├── domain/model/reservation/CancellationPolicyTest.java
├── domain/usecase/cancelreservation/
│   ├── CancelReservationUseCaseTest.java       # (existe, de UC3) + las ramas nuevas
│   └── CancelForMaintenanceUseCaseTest.java
└── infrastructure/adapter/
    ├── in/web/reservation/CancelReservationControllerTest.java
    └── out/persistence/CancellationIT.java

frontend/src/features/reservations/
├── CancelReservation.tsx                       # botón y confirmación
├── MyReservationsList.tsx                      # (existe) + el botón y el estado no cancelable
└── types.ts                                    # (existe) + los tipos de cancelación
```

**Structure Decision**: no aparece ninguna carpeta nueva. UC4 es el primer caso de uso que no agrega infraestructura: reusa la persistencia de UC2, la *outbox* de UC2 y el evento de UC3. Lo único que crece es el modelo de dominio, que es donde vive la regla del plazo.

### Decisiones de diseño de este caso de uso

**Tres orígenes, un solo camino.** `CancelReservationUseCase` recibe el `CancellationOrigin` y solo cambia lo que debe cambiar:

| Origen | Quién lo dispara | Comprueba titular y plazo | Estado final | ¿Penaliza? |
|---|---|---|---|---|
| `HOLDER` | El Estudiante o Monitor, por HTTP | **Sí** | `CANCELADA` | Lo decide el Módulo 3 con la antelación que le mandamos |
| `ACADEMIC_PRIORITY` | UC3, por código | No | `CANCELADA_POR_PRIORIDAD_ACADEMICA` | No (FR-005) |
| `RESOURCE_UNAVAILABLE` | El mantenimiento del recurso (FR-010) | No | `CANCELADA_POR_RECURSO_NO_DISPONIBLE` | No |

Lo común —liberar la ocupación, soltar el cupo, auditar y encolar el reporte— se escribe una vez. Por eso UC3 pudo usar este caso de uso sin que existiera todavía la rama del titular.

**La regla del plazo, y su borde.** `CancellationPolicy` responde si una reserva es cancelable comparando `now` con el instante de referencia menos la antelación mínima:

| Categoría | Instante de referencia | Cancelable mientras |
|---|---|---|
| `ESPACIO` | `reservation.starts_at` | `now <= starts_at - 10min` |
| `ACTIVO` | `loan.pickup`, que es el mismo `reservation.starts_at` | `now <= pickup - 10min` y `loan.picked_up_at` es nulo |

**Decisión: el borde es inclusivo.** Una petición que llega exactamente en `starts_at - 10min` **se acepta**. El edge case pide un criterio explícito y determinista y no dice cuál; se elige el que favorece a quien libera el recurso, que es justo lo que este caso de uso quiere fomentar. Queda en una sola línea de `CancellationPolicy` y en una prueba con ese instante exacto.

**Qué código de error se devuelve.** Aquí hay una **contradicción entre dos specs** que este plan no puede resolver solo:

| Fuente | Qué dice |
|---|---|
| **UC4 FR-003** | "impedir que un usuario cancele reservas fuera del plazo (`CAN-001`)" |
| **UC4**, su tabla de errores | `CAN-001` — La reserva ya no es cancelable, por estar fuera de plazo |
| **UC4**, escenario 3 | "rechaza la operación con `CAN-001` — La reserva ya no es cancelable" |
| **UC11**, escenario 5 | "lo rechaza con `CAN-001`" para una cancelación fuera de plazo |
| [spec-modulo2.md](../specs/spec-modulo2.md), diccionario consolidado | `CAN-001` — No autorizado (no es el titular); `CAN-002` — ya no es cancelable |

Se implementa **`CAN-001` = la reserva ya no es cancelable**, que es lo que dicen el FR, la tabla y el escenario del spec de UC4 y también el escenario 5 de UC11: cuatro menciones coincidentes frente a una. El consolidado es el que hay que corregir.

`CAN-002` queda **sin emitir**: el caso que el consolidado le asigna —no ser el titular— se responde `404` sin código, por la razón de abajo. (NEEDS CLARIFICATION: hay que arreglar el diccionario de `spec-modulo2.md`; mientras no se haga, la prueba de T007 deja fijado que el código es `CAN-001`.)

**No se filtra si una reserva existe.** Cancelar una reserva de otra persona responde `404`, no `403` con `CAN-001`. Un `403` confirmaría que ese identificador existe y es de alguien, y el identificador es un uuid que nadie debería poder sondear. `CAN-001` queda definido en el dominio y se usa en la auditoría, pero **no viaja al cliente**. (Es una decisión de este plan; el spec no habla de este caso.)

**La transacción de cancelar.** Es corta y no llama a nadie por HTTP:

1. `SELECT ... FOR UPDATE` de la reserva. Es lo que hace idempotente la doble cancelación: la segunda petición ve el estado ya cancelado y sale sin tocar nada.
2. Si ya está cancelada → se responde con lo que hay, sin reescribir `cancelled_at` ni encolar otro evento (edge case **Doble cancelación**, UC11 SC-003).
3. Si es un activo, leer `loan` y rechazar si `picked_up_at` no es nulo (FR-008).
4. Comprobar titular y plazo, solo en la rama `HOLDER`.
5. `UPDATE` del estado, el `cancellation_reason` y `cancelled_at`.
6. `INSERT` del evento en `outbox_message` (UC11).

La ocupación se libera **sin borrar nada**: la restricción `reservation_no_overlap` solo mira las filas `CONFIRMADA`, así que cambiar el estado basta para que el recurso vuelva a estar libre, y la fila queda para la auditoría. Esa es la razón de que el índice de exclusión llevara el `WHERE` desde el plan general.

**El cupo no se guarda en ninguna parte.** No hay contador que decrementar: el cupo se cuenta con la consulta de UC2 sobre las reservas vigentes, y una `CANCELADA` deja de contar por sí sola (UC2 FR-008). Por eso "descuenta el préstamo de su cupo vigente" no es un paso del algoritmo, y la prueba de que ocurre es consultar el cupo después.

**Cancelar por mantenimiento (FR-010).** `CancelForMaintenanceUseCase` recibe un `resourceId` y cancela todas sus reservas `CONFIRMADA` cuyo `starts_at` sea futuro, en una transacción, con un evento por cada una. Un activo ya entregado se salta, igual que en la rama del titular: no se le puede quitar de las manos a nadie.

Falta quién lo dispara. Hoy nadie nos avisa de que un recurso entró en mantenimiento: el daño va del Módulo 3 al Módulo 1 y a nosotros no nos llega (P-20 punto 2). Mientras eso siga abierto, este plan deja el caso de uso implementado y probado, con **un endpoint de Dirección de Programa** para ejecutarlo a mano, y no inventa un sondeo periódico del catálogo. (NEEDS CLARIFICATION: P-20 punto 2. Las opciones son que el Módulo 1 nos publique el cambio, que el check-out del Módulo 3 traiga la marca, o que sigamos a mano.)

**La no presentación no es una cancelación.** Cuando el Módulo 3 reporta que alguien no llegó, el recurso se libera por UC9 y **no** se emite un reporte de cancelación: la reserva no se canceló, se incumplió (UC11 FR-011, SC-006). UC4 no participa en eso, y la prueba que lo garantiza vive en el plan de UC9.

## Contratos

Se aplican las **convenciones comunes** de [UC1 § Contratos](./plan-uc1-consultar-recursos.md#contratos). UC4 agrega un solo endpoint para el titular, uno de Dirección de Programa, y reusa sin cambios el evento de cancelación que definió [UC3 § Contratos §7](./plan-uc3-importar-horarios-semestrales.md#7-evento-de-cancelación--module2reservationcancellationv1).

---

### 1. `DELETE /api/reservations/{reservationId}`

La cancelación del titular. Es `DELETE` y no `POST .../cancellation` porque lo que la persona pide es deshacer su reserva, y el recurso que desaparece de su lista es la reserva; la fila sigue en la base, pero eso es un detalle nuestro.

Sin cuerpo. El titular sale del JWT.

**`200 OK`**

```json
{
  "reservationId": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
  "status": "CANCELADA",
  "cancellationOrigin": "HOLDER",
  "cancelledAt": "2026-08-31T09:14:22-05:00",
  "noticeMinutes": 1486,
  "releasedTime": {
    "start": "2026-09-01T10:00:00-05:00",
    "end": "2026-09-01T12:00:00-05:00"
  },
  "quota": { "used": 1, "max": 3 }
}
```

| Campo | Nota |
|---|---|
| `status` | `CANCELADA` en esta rama. Las otras dos no entran por HTTP. |
| `noticeMinutes` | Minutos de antelación respecto al inicio o a la recogida. Es lo que UC11 FR-004 manda al Módulo 3, y se devuelve para que la pantalla pueda decir "cancelaste con 1 día de antelación". |
| `releasedTime` | La ocupación liberada completa: la franja de un espacio, o el periodo entero del préstamo (FR-002, escenario 2). |
| `quota` | Cómo quedó el cupo, para que la lista no tenga que volver a preguntar. |

La respuesta de un activo trae además el periodo que se liberó en forma de préstamo:

```json
{
  "reservationId": "4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3",
  "status": "CANCELADA",
  "cancellationOrigin": "HOLDER",
  "cancelledAt": "2026-09-02T18:05:00-05:00",
  "noticeMinutes": 1225,
  "releasedTime": {
    "start": "2026-09-03T14:30:00-05:00",
    "end": "2026-09-14T22:00:00-05:00"
  },
  "quota": { "used": 1, "max": 3 }
}
```

**Idempotencia.** Una segunda llamada responde `200` con el mismo cuerpo que la primera —el `cancelledAt` original, no uno nuevo— y añade `alreadyCancelled: true`. No es un error: el estado final que el cliente quería es el que hay (edge case **Doble cancelación**).

```json
{
  "reservationId": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
  "status": "CANCELADA",
  "cancellationOrigin": "HOLDER",
  "cancelledAt": "2026-08-31T09:14:22-05:00",
  "alreadyCancelled": true,
  "quota": { "used": 1, "max": 3 }
}
```

**Errores**

`409` — fuera de plazo (escenario 3)

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/cancellation-denied",
  "title": "La reserva ya no es cancelable",
  "status": 409,
  "detail": "Esta reserva empieza en menos de 10 minutos. El plazo mínimo para cancelar es de 10 minutos de antelación.",
  "instance": "/api/reservations/9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
  "codigo": "CAN-001",
  "minNoticeMinutes": 10,
  "referenceAt": "2026-09-01T10:00:00-05:00"
}
```

`referenceAt` es el instante contra el que se midió: el inicio de la franja o la hora de recogida. Así la persona entiende por qué se le negó sin tener que adivinar qué se comparó.

`409` — el activo ya se recogió (FR-008)

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/cancellation-denied",
  "title": "La reserva ya no es cancelable",
  "status": 409,
  "detail": "Ya tienes este activo en tu poder, así que no se cancela: hay que devolverlo. El préstamo se cierra cuando se registre la devolución.",
  "instance": "/api/reservations/4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3",
  "codigo": "CAN-001",
  "reason": "ASSET_ALREADY_PICKED_UP",
  "pickedUpAt": "2026-09-03T14:35:00-05:00"
}
```

Mismo `codigo` y distinto `reason`, porque para el diccionario de errores las dos son "ya no es cancelable" pero lo que la persona tiene que hacer es muy distinto.

| Resto | Cuándo |
|---|---|
| `401` | Sin sesión. |
| `404` | La reserva no existe **o no es de quien pide** (ver la decisión de arriba). |
| `503` | No se da en este endpoint: cancelar no llama al Módulo 1 ni al Módulo 3. Si Kafka está caído, la cancelación se confirma igual y el reporte espera en la *outbox* (UC11 FR-008, SC-005). |

---

### 2. `POST /api/resources/{resourceId}/cancel-reservations`

La cancelación en masa por mantenimiento (FR-010). Solo rol `DIRECCION_PROGRAMA`, y existe porque **nadie nos avisa** del cambio de estado del recurso (P-20 punto 2). El día que llegue ese aviso, el adaptador que lo reciba llamará al mismo `CancelForMaintenancePort` y este endpoint podrá quedarse como herramienta de operación.

```json
{ "reason": "El salón quedó fuera de servicio por una gotera.", "confirm": false }
```

Con `confirm: false` muestra qué se cancelaría, igual que UC3:

```json
{
  "applied": false,
  "resourceId": "ESP-0220",
  "resourceName": "Laboratorio de Química",
  "reservationsToCancel": [
    {
      "reservationId": "c7e1a930-8b24-4d16-95af-0e3d7c1b2f84",
      "holder": { "code": "2019114045", "name": "Nombre del estudiante" },
      "start": "2026-09-05T08:00:00-05:00",
      "end": "2026-09-05T10:00:00-05:00",
      "category": "ESPACIO"
    }
  ],
  "skipped": [
    {
      "reservationId": "a3f8d215-6c47-4e09-b8d1-5f2e9a0c7b63",
      "reason": "ASSET_ALREADY_PICKED_UP"
    }
  ]
}
```

Con `confirm: true` responde `200` con lo aplicado:

```json
{
  "applied": true,
  "resourceId": "ESP-0220",
  "reservationsCancelled": 4,
  "skipped": 1,
  "eventsQueued": 4,
  "appliedAt": "2026-10-02T16:20:08-05:00"
}
```

`skipped` son las que no se pueden cancelar porque el activo ya está en manos de alguien. Se informan en vez de callarlas: alguien tiene que ir a pedirlas.

**Errores**: `403` si el rol no es Dirección de Programa, `404` si el recurso no tiene reservas futuras ni existe en el Módulo 1, y `400` si falta `reason` —que es obligatorio, porque va al Módulo 3 y a la auditoría.

---

### 3. Evento hacia el Módulo 3

UC4 **no define evento nuevo**: emite el mismo `ReservationCancelled` de [UC3 § Contratos §7](./plan-uc3-importar-horarios-semestrales.md#7-evento-de-cancelación--module2reservationcancellationv1), cambiando los campos que dependen del origen. Lo que UC4 agrega al contrato es el campo que UC3 dejó pendiente:

```json
{
  "eventId": "d4c9e2f1-7a83-4b60-91c5-2e8d0f3a1b57",
  "type": "ReservationCancelled",
  "version": 1,
  "occurredAt": "2026-08-31T09:14:22-05:00",
  "data": {
    "reservationId": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
    "status": "CANCELADA",
    "cancellationOrigin": "HOLDER",
    "reason": "Cancelada por el titular.",
    "attributableToPerson": true,
    "noticeMinutes": 1486,
    "cancelledAt": "2026-08-31T09:14:22-05:00",
    "resource": { "id": "ESP-0107", "name": "Sala de Estudio 3", "category": "ESPACIO" },
    "holder": {
      "userId": "5f1b9c2d-7a34-4e81-b0f6-3c8d1e9a4b72",
      "code": "2019114045",
      "name": "Nombre del estudiante",
      "role": "ESTUDIANTE"
    },
    "releasedTime": {
      "start": "2026-09-01T10:00:00-05:00",
      "end": "2026-09-01T12:00:00-05:00"
    }
  }
}
```

| Campo | Qué cambia según el origen | FR |
|---|---|---|
| `noticeMinutes` | **Solo** en `HOLDER`. Es el campo que UC11 FR-004 pide y que UC3 no envía, porque en una cancelación que la persona no pidió no significa nada. | UC11 FR-004 |
| `attributableToPerson` | `true` en `HOLDER`, `false` en las otras dos. Es de lo que depende que el Módulo 3 no penalice (FR-005, UC11 FR-006, SC-004). | UC4 FR-005 |
| `status` y `cancellationOrigin` | Los tres pares posibles, y nada más. | UC11 FR-003 |
| `reason` | Texto legible. En `HOLDER` no lo escribe la persona: lo pone el sistema. | UC11 FR-002 |

**Decisión: el titular no escribe el motivo.** El endpoint no acepta un texto libre. UC11 FR-002 pide "el motivo", y en una cancelación voluntaria el motivo es precisamente que la persona la canceló; un campo libre invitaría a mandarle al Módulo 3 texto que nadie va a leer y que podría llevar datos personales. En las otras dos ramas el motivo sí lo pone quien dispara: la clase que desplaza, o la razón del mantenimiento.

---

### 4. Tipos del frontend

Se suman a `frontend/src/features/reservations/types.ts`, que ya existe de UC2.

```ts
export type CancellationOrigin = "HOLDER" | "ACADEMIC_PRIORITY" | "RESOURCE_UNAVAILABLE";

export interface CancellationResponse {
  reservationId: string;
  status: "CANCELADA" | "CANCELADA_POR_PRIORIDAD_ACADEMICA" | "CANCELADA_POR_RECURSO_NO_DISPONIBLE";
  cancellationOrigin: CancellationOrigin;
  cancelledAt: string;
  noticeMinutes?: number;
  releasedTime?: { start: string; end: string };
  quota: Quota;
  alreadyCancelled?: boolean;
}

export interface CancellationError extends ErrorApi {
  codigo?: "CAN-001";
  reason?: "ASSET_ALREADY_PICKED_UP";
  minNoticeMinutes?: number;
  referenceAt?: string;
  pickedUpAt?: string;
}
```

`MyReservationsList.tsx` decide si muestra el botón con la misma regla del backend, pero **solo para no ofrecer lo imposible**: la decisión es del servidor, y un `409` se muestra tal cual. Para eso cada reserva de la lista gana un `cancellable: boolean` calculado en el backend, que se añade a `GET /api/reservations/mine` de [UC2 § Contratos §4](./plan-uc2-reservar-recursos.md#4-get-apireservationsmine).

---

### 5. Fixtures compartidos

```text
src/test/resources/contratos/
├── api-cancellation-200.json                # espacio
├── api-cancellation-200-asset.json          # activo, con el periodo liberado
├── api-cancellation-200-idempotent.json     # alreadyCancelled
├── api-cancellation-409-late.json           # CAN-001 por plazo
├── api-cancellation-409-picked-up.json      # CAN-001 por activo entregado
├── api-cancel-by-maintenance-preview.json
├── api-cancel-by-maintenance-applied.json
└── event-reservation-cancelled-holder.json  # con noticeMinutes
```

El de UC3, `event-reservation-cancelled.json`, se renombra a `event-reservation-cancelled-academic.json` para que los dos orígenes queden visibles lado a lado.

---

## Phase 1: Setup

**Purpose**: Un solo parámetro nuevo. UC4 no añade dependencias.

- [ ] T001 Agregar `reservations.min-cancellation-notice=10m` a `application.properties` y a `ReservationProperties`, validándolo al arrancar (> 0)

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T002 [P] Crear `CancellationOrigin` en `domain/model/reservation/` con los tres orígenes, y el par estado–origen que le corresponde a cada uno
- [ ] T003 [P] Crear `CancellationPolicy` en `domain/model/reservation/`: decide si una reserva es cancelable según su categoría, el instante de referencia, la antelación mínima y si el activo ya se recogió; y calcula `noticeMinutes`
- [ ] T004 [P] Crear `Cancellation` (origen, motivo, antelación, instante) y extender `Reservation` con las transiciones a los tres estados de cancelación, rechazando las que salgan de un estado ya final
- [ ] T005 [P] Extender `DenialCode` con `CAN-001` —y `CAN-002`, que queda declarado pero sin emitir— crear `CancellationDeniedException` y traducirla a `409` en `GlobalErrorHandler` con `codigo`, `reason`, `minNoticeMinutes` y `referenceAt`
- [ ] T006 Extender `ReservationRepositoryPort` y `ReservationPersistenceAdapter` con `cancelar(...)`: bloqueo de la fila, salida temprana si ya está cancelada, lectura del préstamo y `UPDATE` del estado, el motivo y la marca

**Checkpoint**: la regla y la transición existen; se puede empezar la historia

---

## Phase 3: User Story 1 - Cancelar reserva (Priority: P2)

**Goal**: El titular cancela una reserva que no va a usar y el recurso vuelve a estar disponible en esa franja; si llega tarde, se le explica el plazo. Las cancelaciones que no son suyas no lo penalizan.

**Independent Test**: Con el perfil local, crear una reserva, cancelarla y comprobar en la consulta de UC1 que la franja vuelve a aparecer `DISPONIBLE` y que el cupo bajó; intentar cancelar una que empieza en 5 minutos y comprobar el `CAN-002`; cancelar dos veces y comprobar que el cupo solo se libera una vez.

### Tests for User Story 1

- [ ] T007 [P] [US1] Pruebas en `CancellationPolicyTest.java`: cancelable con 11 minutos, no cancelable con 9, **cancelable exactamente a los 10** (edge case **Cancelación en el límite del plazo**), un espacio ya iniciado, un activo sin recoger cancelable hasta 10 minutos antes de la recogida, un activo ya recogido nunca cancelable (FR-008), y que el código usado es `CAN-001`, como dicen UC4 FR-003 y el escenario 5 de UC11
- [ ] T008 [P] [US1] Pruebas en `CancelReservationUseCaseTest.java` para la rama `HOLDER`: el escenario 1 deja `CANCELADA` y libera la franja; el escenario 2 libera el periodo **completo** del préstamo; quien no es titular recibe `404`; el `noticeMinutes` sale bien en los dos casos; y se encola **un** evento con `attributableToPerson: true`
- [ ] T009 [P] [US1] Pruebas de las otras dos ramas: `ACADEMIC_PRIORITY` no comprueba titular ni plazo y deja `CANCELADA_POR_PRIORIDAD_ACADEMICA` sin `noticeMinutes` y con `attributableToPerson: false` (escenario 4, FR-005); `RESOURCE_UNAVAILABLE` hace lo mismo con su estado (FR-010); y ninguna de las dos penaliza (SC-003)
- [ ] T010 [P] [US1] Pruebas en `CancelForMaintenanceUseCaseTest.java`: cancela solo las `CONFIRMADA` futuras del recurso, deja fuera las pasadas y las ya canceladas, **se salta** el activo ya entregado informándolo en `skipped`, y emite un evento por cada una
- [ ] T011 [P] [US1] Prueba `CancellationIT.java` con Testcontainers: tras cancelar, otra reserva **sí** puede ocupar la misma franja —la restricción de exclusión deja de verla—, la fila sigue existiendo para la auditoría, dos cancelaciones concurrentes de la misma reserva dejan un solo evento en `outbox_message` (UC11 SC-003), y un *rollback* no deja ni el estado cambiado ni el evento
- [ ] T012 [P] [US1] Prueba `CancelReservationControllerTest.java` con `@WebMvcTest`, contra los *fixtures*: `200` de espacio y de activo, `200` idempotente con `alreadyCancelled`, los dos `409` con `codigo: "CAN-001"` y su `reason`, `401` sin sesión, `404` de una reserva de otra persona —comprobando que el cuerpo **no** revela que existe—, y el `200` y la previsualización del endpoint de mantenimiento

### Implementation for User Story 1

- [ ] T013 [US1] Completar `CancelReservationUseCase` con la rama `HOLDER`: comprobación de titularidad, `CancellationPolicy`, cálculo de `noticeMinutes` y el resultado idempotente cuando ya estaba cancelada (depende de T002 a T006)
- [ ] T014 [US1] Extender el evento `ReservationCancelled` con `noticeMinutes` y `attributableToPerson` según el origen, en `OutboxNotifierAdapter` (depende de T013)
- [ ] T015 [US1] Definir `CancelForMaintenancePort` e implementar `CancelForMaintenanceUseCase`, con previsualización y aplicación, reusando el mismo camino de cancelación (depende de T013)
- [ ] T016 [US1] Implementar `DELETE /api/reservations/{reservationId}` en `ReservationController` según [Contratos §1](#1-delete-apireservationsreservationid), y añadir `cancellable` a `GET /api/reservations/mine`
- [ ] T017 [US1] Implementar `POST /api/resources/{resourceId}/cancel-reservations` según [Contratos §2](#2-post-apiresourcesresourceidcancel-reservations), restringido a `DIRECCION_PROGRAMA`
- [ ] T018 [US1] Registrar los beans y las transacciones de UC4 en `UseCasesConfig`
- [ ] T019 [P] [US1] Frontend: `CancelReservation.tsx` con la confirmación —que dice qué se libera y, en un activo, que el periodo entero queda libre— y los tipos de [Contratos §4](#4-tipos-del-frontend)
- [ ] T020 [US1] Frontend: enganchar el botón en `MyReservationsList.tsx`, oculto cuando `cancellable` es `false`, con el motivo visible y refrescando el cupo tras cancelar

**Checkpoint**: UC4 queda funcional de punta a punta

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T021 [P] Verificar SC-001 de punta a punta: cancelar y comprobar que la consulta de UC1 muestra la franja `DISPONIBLE` en menos de 5 s
- [ ] T022 [P] Verificar SC-002: el 100 % de los intentos fuera de plazo se rechazan con su código, con una prueba que recorra los dos motivos
- [ ] T023 [P] Registrar en logs cada cancelación con su origen, su antelación y su resultado, sin datos personales más allá del identificador
- [ ] T024 Llevar a `pendientes-clarificacion.md` la contradicción de `CAN-001`/`CAN-002` entre el diccionario consolidado y el spec de UC4, y el estado de P-20 punto 2

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: depende de UC1, UC2 y UC3; sin UC3 no existe `CancelReservationUseCase` ni el evento de cancelación
- **Foundational (Phase 2)**: depende de Setup - BLOCKS la user story
- **User Story 1 (Phase 3)**: depende de Foundational
- **Polish (Phase 4)**: depende de la Phase 3

### Dependencias con otros casos de uso

- **UC3 `Importar horarios semestrales`**: dejó hecha la rama `ACADEMIC_PRIORITY`, el caso de uso y el evento. UC4 no la reescribe: la completa con las otras dos.
- **UC2 `Reservar recursos`**: aporta la reserva, el préstamo, la *outbox* y la pantalla `my-reservations`. El cupo que UC4 "libera" es la misma consulta de UC2 FR-008, que deja de contar una `CANCELADA` sin que nadie decremente nada.
- **UC1 `Consultar recursos`**: es donde se ve el efecto. No hay que tocarlo: la consulta de ocupaciones ya filtra por `CONFIRMADA`.
- **UC11 `Reportar cancelación de reserva`**: UC4 emite el evento con los campos que faltaban (`noticeMinutes`). Su plan añade el historial de envíos (UC11 FR-010) y la garantía de que una ausencia y una cancelación no coexistan (UC11 FR-011).
- **UC7 `Actualizar estado de los recursos`**: el `<<include>>` de FR-002 se cumple dejando la ocupación liberada, igual que en UC2 y UC3, mientras P-20 siga abierto.
- **UC9 `Recibir reporte de no asistencia`**: es el camino **alternativo** de liberación y no pasa por aquí. Una ausencia no es una cancelación.
- **UC12 `Recibir check-out`**: es lo que cierra un activo ya entregado, que UC4 se niega a cancelar (FR-008).

### Within User Story 1

- Modelo y regla (T002 a T005) → persistencia (T006) → caso de uso (T013) → evento (T014) → mantenimiento (T015)
- Controladores (T016, T017) → beans (T018) → frontend (T019, T020)

### Parallel Opportunities

- En Foundational: T002 a T005
- En User Story 1: todas las pruebas (T007 a T012) y el frontend de tipos (T019)
- En Polish: T021 a T023

## Notes

- La numeración `T0XX` es propia de este plan
- [P] tasks = different files, no dependencies
- Verify tests pass
- Commit after each task or logical group
- La sección **Contratos** es la única fuente del JSON de UC4
- **Decisiones de los contratos que el spec no fija**: el borde del plazo es inclusivo; cancelar la reserva de otra persona responde `404` y no `403`, para no confirmar que existe; la doble cancelación es `200` con `alreadyCancelled` y no un `409`; el titular no escribe un motivo libre; y el endpoint de mantenimiento existe solo mientras nadie nos avise del cambio de estado
- **NEEDS CLARIFICATION abiertos en este plan**:
  - **Contradicción `CAN-001` / `CAN-002`**: el diccionario consolidado de `spec-modulo2.md` los define al revés de como los usan UC4 (FR-003, su tabla y su escenario 3) y UC11 (escenario 5). Se siguen los specs de los casos de uso y hay que corregir el consolidado; `CAN-002` queda sin emitir
  - **P-20 punto 2**: nadie nos avisa de que un recurso pasó a `EN_MANTENIMIENTO`, así que FR-010 no tiene disparador automático
  - **Sanción retroactiva**: el punto transversal de `spec-modulo2.md` pregunta si una sanción nueva cancela las reservas ya confirmadas. Si la respuesta es sí, nace un cuarto origen de cancelación y este plan es donde encaja
  - **P-13**: si el Módulo 3 premia la cancelación a tiempo, `noticeMinutes` pasa a ser un dato con consecuencia y habría que fijar su precisión
