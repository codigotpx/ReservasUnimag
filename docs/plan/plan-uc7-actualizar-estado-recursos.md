# Implementation Plan: Actualizar estado de los recursos (UC7)

**Date**: 2026-10-02
**Spec**: [spec-modulo2-uc7-actualizar-estado-recursos.md](../specs/spec-modulo2-uc7-actualizar-estado-recursos.md)
**Plan general**: [plan-arquitectura.md](./plan-arquitectura.md) — incluida la [convención de nombres](./plan-arquitectura.md#convención-de-nombres)
**Planes previos**: lo invocan [UC2](./plan-uc2-reservar-recursos.md), [UC3](./plan-uc3-importar-horarios-semestrales.md), [UC4](./plan-uc4-cancelar-reserva.md), [UC9](./plan-uc9-recibir-reporte-no-asistencia.md) y [UC12](./plan-uc12-recibir-check-out.md), y cada uno de esos planes ya implementó su parte

## Summary

UC7 es el `<<include>>` más invocado del módulo, y el que más ha cambiado con el reparto de estados. Conviene decir desde el principio qué pide hoy, porque este plan se escribió dos veces.

El spec pide tres cosas. **Mantener coherente en el tiempo la ocupación de cada recurso** (FR-001 a FR-003, FR-005, FR-006, FR-010, FR-011), que es nuestra y ya está hecha, repartida entre cinco planes. **Dejar registro de cada cambio** (FR-009), que es lo único que ningún plan anterior podía escribir. Y **avisarle al Módulo 1 cuando el uso de un recurso empieza** (FR-004, FR-007, FR-008), que es el único estado físico que nace de una reserva y por eso es el único que sale de aquí: FR-012 prohíbe enviarle cualquier otro, porque el regreso a `DISPONIBLE` y la entrada a `EN_MANTENIMIENTO` los constata el Módulo 3 y se los reporta él.

**Enfoque técnico:**

1. **La primera parte ya está hecha**, y no por casualidad: el estado de un recurso en el Módulo 2 **no es una columna**, es la consecuencia de qué ocupaciones hay escritas. Este plan lo documenta de una vez y añade las pruebas transversales que ningún plan individual podía escribir.
2. **El registro de cambios** (FR-009) es la tabla `status_change`, que el plan general dejó pendiente. Es nuestra y no depende de nadie.
3. **El aviso al Módulo 1** se implementa, y va por la *outbox* y Kafka como todo lo que sale del módulo, nunca por REST síncrono: FR-007 y SC-005 piden que la operación de negocio se complete aunque el Módulo 1 esté caído y que el aviso se reintente hasta entregarse, que es exactamente lo que la *outbox* de UC2 ya hace para el Módulo 3.
4. **El aviso solo puede llevar `EN_USO`**, y eso se garantiza en el tipo del puerto, no con un `if`: `InventoryStatusNotifierPort` no recibe un estado, recibe un inicio de uso. Así FR-012 no se puede incumplir por descuido.
5. **El uso empieza de dos maneras, y solo una depende de P-02.** Una clase del semestre empieza a su hora y nadie tiene que registrarla: el horario ya es el registro, así que ese aviso se dispara solo (FR-004). Una reserva con titular sí necesita que alguien diga "llegué", y eso es P-02: el plan construye el camino completo desde el inicio de uso hacia dentro y le pone una entrada provisional por la app, igual que UC4 hizo con la cancelación por mantenimiento.
6. **El aviso lleva el tramo y con eso se cierra solo** (FR-004a): en un espacio va la hora de fin y el Módulo 1 lo libera por sí mismo; en un activo no va fin, porque su uso termina con la devolución que le reporta el Módulo 3. Es la misma diferencia de fondo que ya separan FR-010 y FR-011, dicha una vez más para quien recibe el evento.
7. Dos requisitos que parecen trabajo y no lo son: FR-010 —un espacio se libera al terminar su franja— y FR-011 —un activo no se libera solo— son **consecuencias del modelo de datos**, no tareas. Merecen una prueba, no un `@Scheduled`.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 4.1.1, Spring for Apache Kafka, Flyway. **Ninguna nueva.**
**Storage**: PostgreSQL. UC7 **escribe** la tabla nueva `status_change`, la marca de inicio de uso sobre `reservation` y `loan`, y `outbox_message`; las ocupaciones las escriben los casos de uso que lo invocan.
**Testing**: JUnit 5 y AssertJ, Testcontainers con PostgreSQL (coherencia bajo concurrencia, que es SC-003) y con Kafka (entrega del aviso y la caída de 30 minutos de SC-004), WireMock (el Módulo 1 de la comprobación previa), `@WebMvcTest`, ArchUnit. Las pruebas de este plan son casi todas **transversales**: comprueban invariantes que cruzan varios casos de uso.
**Target Platform**: Servidor Linux con JVM 21
**Performance Goals**: Un recurso liberado vuelve a aparecer como disponible en menos de 5 s (SC-002). Se cumple sin esfuerzo porque no hay nada que propagar: UC1 lee el estado en vivo.
**Constraints**:
- Un recurso **nunca** en dos estados para el mismo momento (FR-006, SC-003).
- Una reserva o una cancelación **no** saca a un recurso de `EN_MANTENIMIENTO` (FR-005).
- Al Módulo 1 **solo** le sale el inicio de uso; ningún otro estado (FR-004, FR-012).
- Ninguna operación de negocio falla por un error al avisar al Módulo 1 (SC-005, FR-007).
- Un espacio se libera al terminar su franja; un activo solo con su check-out (FR-010, FR-011).
**Scale/Scope**: Un endpoint de auditoría para Dirección de Programa y uno de inicio de uso para el titular. Sin pantalla propia más allá del botón de "ya llegué".

## Project Structure

### Source Code (repository root)

```text
src/main/java/edu/unimagdalena/reservasunimag/
├── domain/
│   ├── model/resource/
│   │   ├── StatusChange.java                   # nuevo: el registro de FR-009
│   │   └── StatusChangeReason.java             # nuevo: por qué cambió
│   ├── port/
│   │   ├── in/
│   │   │   ├── UpdateResourceStatusPort.java   # (existe, de UC2) se completa aquí
│   │   │   └── RegisterUseStartedPort.java     # nuevo: el disparador de FR-004
│   │   └── out/
│   │       ├── StatusChangeRepositoryPort.java
│   │       └── InventoryStatusNotifierPort.java    # FR-004: solo inicio de uso
│   └── usecase/updatestatus/
│       ├── UpdateResourceStatusUseCase.java    # (existe, de UC2) se completa aquí
│       └── RegisterUseStartedUseCase.java      # nuevo: un escritor, dos disparadores
│
└── infrastructure/
    ├── adapter/
    │   ├── in/
    │   │   ├── web/
    │   │   │   ├── resource/StatusChangeController.java  # GET de auditoría
    │   │   │   └── reservation/StartUseController.java   # POST de inicio de uso (provisional, P-02)
    │   │   └── scheduler/
    │   │       └── AcademicUseStartScheduler.java        # las clases que empiezan a su hora
    │   └── out/
    │       ├── persistence/
    │       │   ├── entity/StatusChangeJpa.java
    │       │   ├── repository/StatusChangeJpaRepository.java
    │       │   └── StatusChangePersistenceAdapter.java
    │       └── module1/
    │           └── ResourceStatusOutboxAdapter.java    # inserta el aviso en la outbox
    └── config/
        └── UseCasesConfig.java

src/main/resources/db/migration/
├── V9__status_change.sql
└── V11__reservation_use_started_at.sql

src/test/java/edu/unimagdalena/reservasunimag/
├── domain/usecase/updatestatus/
│   ├── UpdateResourceStatusUseCaseTest.java
│   └── RegisterUseStartedUseCaseTest.java
└── infrastructure/adapter/
    ├── in/
    │   ├── web/
    │   │   ├── resource/StatusChangeControllerTest.java
    │   │   └── reservation/StartUseControllerTest.java
    │   └── scheduler/AcademicUseStartSchedulerTest.java
    └── out/persistence/
        ├── StatusChangeIT.java
        ├── ResourceStatusCoherenceIT.java      # las pruebas transversales de SC-003
        └── UseStartedNotificationIT.java       # entrega del aviso y caída de 30 min

frontend/src/features/reservations/
└── StartUseButton.tsx                          # "ya llegué" sobre una reserva de hoy
```

**Structure Decision**: `ResourceStatusOutboxAdapter` vive en `out/module1/` aunque escriba en PostgreSQL, porque su destinatario es el Módulo 1 y lo que hace es *mandar*: la *outbox* es el transporte, no el propósito. Es el mismo criterio con el que `Module3NotifierPort` de UC2 tiene su adaptador fuera de `persistence/`.

### Decisiones de diseño de este caso de uso

**El estado de un recurso no se guarda: se deduce.** Es la decisión que ya tomaron los planes anteriores sin enunciarla, y de la que dependen casi todos los requisitos de UC7:

| Lo que UC7 llama | Lo que realmente hay |
|---|---|
| El recurso pasa a `RESERVADO` | Una fila `CONFIRMADA` en `reservation` cuyo rango `occupancy` cubre esa franja |
| El recurso pasa a `BLOQUEO_ACADEMICO` | La misma fila, con `origin = 'ACADEMICO'` |
| El recurso pasa a `EN_USO` | La reserva con `use_started_at` —puesto a mano o por la hora de la clase—, que es lo que le avisamos al Módulo 1 y lo que él nos devuelve como `operationalStatus` |
| El recurso vuelve a `DISPONIBLE` | **Nada**: desaparece la ocupación, o la franja consultada queda fuera de su rango |
| El recurso está `EN_MANTENIMIENTO` | Lo dice el Módulo 1; nosotros no lo guardamos |

De ahí salen tres requisitos sin escribir una línea:

- **FR-006 y SC-003** —un recurso nunca en dos estados para el mismo momento— los garantiza la restricción `reservation_no_overlap`, que impide dos ocupaciones solapadas confirmadas, más la tabla de prioridad de UC1, que convierte en **una sola** etiqueta las que se pueden dar a la vez (un mantenimiento sobre una reserva, por ejemplo). No hay forma de que dos estados coexistan, porque solo hay una fuente para cada uno.
- **FR-010** —un espacio se libera al terminar su franja, salvo que haya otra reserva encima— es lo que hace un rango `[starts_at, ends_at)` por sí solo. Una consulta de las 14:00 ya no cruza una franja que acabó a las 12:00, y si hay otra reserva encima, esa sí cruza y manda. **No hay ninguna tarea programada** que libere franjas, y no debe haberla.
- **FR-011** —un activo no se libera solo— es lo que hace la consulta de ocupaciones de UC1: un préstamo con `picked_up_at` y sin `returned_at` ocupa *desde su inicio y sin fin*, no hasta su vencimiento. Esa cláusula, que parece un detalle de UC1, es justamente FR-011.

**FR-005: una reserva no saca a nadie de mantenimiento.** Sale gratis por la misma razón: el mantenimiento es del Módulo 1 y nosotros no lo escribimos, así que no hay operación nuestra que pueda apagarlo. Hay que probar dos cosas: que la **prioridad** se respeta —un recurso en mantenimiento con una reserva confirmada encima se muestra `EN_MANTENIMIENTO` y no `RESERVADO`, la tabla de UC1— y que un inicio de uso sobre un recurso en mantenimiento **no se le avisa** al Módulo 1, que es la segunda mitad de FR-005. Eso es T011 y T012.

**El registro de cambios** (FR-009). La entidad `StatusChange` pide recurso, franja, estado anterior, estado nuevo, motivo, instante y resultado del aviso. Se escribe en `UpdateResourceStatusUseCase`, que ya existe desde UC2, y **en la misma transacción de quien invoca**, porque un cambio que no ocurrió no debe quedar registrado y uno que ocurrió no puede faltar.

| Quién invoca | Motivo | Estado nuevo | ¿Sale aviso? |
|---|---|---|---|
| UC2, al confirmar | `RESERVATION_CONFIRMED` | `RESERVADO` | no |
| UC3, al crear un bloqueo | `ACADEMIC_BLOCK_CREATED` | `BLOQUEO_ACADEMICO` | no |
| UC7, al empezar el uso: alguien lo registra, o la clase llega a su hora | `USE_STARTED` | `EN_USO` | **sí** |
| UC4, al cancelar | `RESERVATION_CANCELLED` | `DISPONIBLE` | no |
| UC9, al registrar una ausencia | `NO_SHOW_REGISTERED` | `DISPONIBLE` | no |
| UC12, al cerrar un préstamo | `CHECK_OUT_RECEIVED` | `DISPONIBLE` | no |
| UC12, al declarar una pérdida | `LOAN_DECLARED_LOST` | `DISPONIBLE` | no |

La columna "¿sale aviso?" **es** FR-012, y es la razón por la que `InventoryStatusNotifierPort` no tiene un método genérico: el único motivo que puede llamarlo es `USE_STARTED`, y el puerto no acepta ningún otro estado. Una prueba de arquitectura lo fija (T013).

**Decisión: el "estado anterior" se guarda, pero no se calcula consultando el Módulo 1.** Guardar el estado anterior obligaría, en rigor, a saber qué veía un estudiante en esa franja antes del cambio, lo que incluye el estado operativo del Módulo 1 y una llamada HTTP dentro de la transacción. No se hace: se guarda el estado anterior **según los datos del Módulo 2** —`RESERVADO`, `BLOQUEO_ACADEMICO` o `DISPONIBLE`— y la columna se llama `previous_status_module2` para que nadie la lea como la etiqueta completa. Un registro de auditoría no justifica meter una llamada a otro sistema en la transacción de una reserva.

**El inicio de uso se marca en nuestra base antes de avisar.** Un espacio estrena la columna `reservation.use_started_at`; un activo ya tenía su marca, `loan.picked_up_at`, de la que depende FR-011. Se escriben las dos en la misma transacción —en un activo, recoger **es** empezar a usar— y el aviso se inserta en la *outbox* ahí mismo. Tres consecuencias que valen la pena:

- **Es idempotente por construcción** (FR-008): la marca se escribe solo si estaba nula, así que un segundo "ya llegué" no produce un segundo registro ni un segundo aviso. Dos peticiones a la vez las resuelve el `UPDATE ... WHERE use_started_at IS NULL`, que solo una gana.
- **No hace falta preguntarle al Módulo 1 si ya lo tiene `EN_USO`.** Nuestra marca es la fuente del hecho; su estado es la consecuencia.
- **El aviso sobrevive a una caída suya** (FR-007, SC-004), porque ya está en `outbox_message` cuando la transacción termina.

**La comprobación de mantenimiento va antes de la transacción, no dentro.** Antes de marcar el inicio de uso se pide la ficha del recurso al Módulo 1 por `InventoryPort`, igual que hace UC2 antes de confirmar: si vuelve `EN_MANTENIMIENTO`, no se marca nada y no sale aviso (FR-005, y el edge case **Uso que empieza sobre un recurso que ya no está**). La respuesta remite al camino de UC4 FR-010, que es quien cancela las reservas de un recurso que dejó de estar disponible. Si el Módulo 1 **no responde**, el inicio de uso se registra igual y el aviso queda en la *outbox*: la persona ya está en el salón y bloquearla por una caída del inventario sería lo peor de los dos mundos (es la misma lectura conservadora al revés que UC1, y depende de P-14).

**Ninguna tarea programada para liberar; una sola para empezar una clase.** La tentación obvia al leer FR-001 y FR-010 es un `@Scheduled` que recorra las reservas poniéndolas `EN_USO` y liberándolas al terminar. **Para liberar no hay ninguna y no debe haberla**: el rango ya dice la verdad en cada consulta, y el Módulo 1 cierra por sí mismo el tramo que le mandamos.

Para **empezar** sí hace falta una, y solo para los bloqueos académicos: `AcademicUseStartScheduler` corre cada minuto, toma los bloqueos `CONFIRMADA` cuya hora de inicio ya pasó y que no tienen `use_started_at`, y llama al mismo `RegisterUseStartedPort` que llama el endpoint. No deduce ni recalcula nada: registra un hecho —la clase empezó— que ningún otro actor va a registrar, y la *outbox* se encarga de la entrega. Es idempotente por la misma marca condicional que el endpoint, así que dos instancias del backend no producen dos avisos. Si un salón ya está `EN_MANTENIMIENTO` no sale aviso (FR-005), y eso se comprueba **por lote** con `POST /api/v1/resources/operational-status` —el mismo que usan UC3 y UC8—, no recurso por recurso: a las 08:00 arrancan muchas clases a la vez.

Se consideró y se descartó la alternativa de insertar el evento en la *outbox* al importar el horario, con `next_attempt_at` en la hora de la clase: ahorraba la tarea, pero separaba el registro de la entrega —la fila de `status_change` quedaría fechada en el futuro— y obligaba a anular mensajes pendientes cada vez que una clase se cancela o se desplaza. Con la tarea, un bloqueo cancelado simplemente deja de aparecer en la consulta.

Queda desactivable con `reservations.academic.use-start-enabled`, por la misma razón que el umbral de pérdida de UC12: es lo único de este plan que actúa sin que nadie se lo pida. Los otros `@Scheduled` del módulo siguen siendo el publicador de la *outbox* y el de ese umbral.

**El hueco que queda, y por qué no bloquea este plan: quién registra que una persona llegó.** Solo afecta a las reservas con titular; las clases no lo necesitan. El spec dice "queda registrado que la persona se presentó" sin decir por dónde entra ese registro, y eso es P-02: puede ser el propio estudiante desde la app, alguien en el punto de préstamo o un lector de carné. El plan resuelve lo que no depende de la respuesta —el puerto `RegisterUseStartedPort`, el caso de uso, la marca, el registro y el aviso— y deja como **entrada provisional** el `POST` de [Contratos §3](#3-post-apireservationsreservationidstart-use), que el titular llama desde la app. El día que P-02 se cierre, lo que llegue —un evento del Módulo 3, un adaptador del lector de carné— llama al mismo puerto y este endpoint se queda como herramienta de operación. Es la misma decisión que tomó UC4 §2 con la cancelación por mantenimiento.

## Contratos

Se aplican las **convenciones comunes** de [UC1 § Contratos](./plan-uc1-consultar-recursos.md#contratos).

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
| notified_status | varchar | `NOT_SENT`, `PENDING`, `SENT`, `FAILED` |
| outbox_message_id | uuid, nulo | El aviso que salió; nulo en los motivos que no avisan |

```sql
CREATE INDEX status_change_resource ON status_change (resource_id, changed_at);
CREATE INDEX status_change_reservation ON status_change (reservation_id);
```

**`notified_status` nace `PENDING` solo en `USE_STARTED`; en los otros seis motivos nace `NOT_SENT` y se queda así.** La diferencia importa y es FR-012 vista desde la auditoría: `NOT_SENT` dice "no se envió y no se esperaba enviarlo", que es la verdad de una confirmación o de una cancelación, mientras `PENDING` significa que hay un aviso esperando a salir. `outbox_message_id` permite ir del registro al envío y al revés, como ya hacen UC10 y UC11 con sus historiales.

La columna nueva de `reservation`:

```sql
ALTER TABLE reservation ADD COLUMN use_started_at timestamptz;
```

Nula mientras nadie registre el inicio de uso, y se escribe una sola vez. En un activo se escribe junto con `loan.picked_up_at`, que ya existía.

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
      "id": "e4c7b019-2f68-4a35-91db-7a0e5c3f2b91",
      "reservationId": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
      "occupancy": { "start": "2026-09-01T10:00:00-05:00", "end": "2026-09-01T12:00:00-05:00" },
      "previousStatusModule2": "RESERVADO",
      "newStatus": "EN_USO",
      "reason": "USE_STARTED",
      "changedAt": "2026-09-01T10:03:51-05:00",
      "notifiedStatus": "SENT",
      "outboxMessageId": "c9b2f536-8a41-4d07-95ec-3f1a0d7b2e58"
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "total": 2 },
  "note": "El estado operativo del recurso lo lleva el Módulo 1. De los tres que son suyos, el único que sale de este historial es EN_USO; el regreso a DISPONIBLE y el mantenimiento se los reporta el Módulo 3."
}
```

El campo `note` es fijo y va en la respuesta a propósito: sin él, un historial que nunca menciona `EN_MANTENIMIENTO` parece incompleto, y lo que ocurre es que ese estado no es nuestro. Es la única respuesta de la API que lleva una explicación de este tipo, y se justifica porque este endpoint existe para auditar y una auditoría con un hueco sin explicar es peor que no tenerla.

Filtros opcionales: `from`, `to` y `reason`.

**Errores**: `403` si el rol no es Dirección de Programa; `404` si el recurso no existe en el Módulo 1. Si el inventario no responde, la respuesta llega igual sin `resourceName`, como en UC2 y UC11.

---

### 3. `POST /api/reservations/{reservationId}/start-use`

El disparador de FR-004: registra que el uso empezó. **Entrada provisional mientras P-02 siga abierto**, como el endpoint de UC4 §2: lo llama el titular desde la app cuando llega al salón o recoge el activo, y el día que se decida otro medio, ese adaptador llama al mismo `RegisterUseStartedPort`.

Sin cuerpo. El `200` confirma el hecho y dice qué se le avisó al Módulo 1:

```json
{
  "reservationId": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
  "resourceId": "ESP-0107",
  "useStartedAt": "2026-09-01T10:03:51-05:00",
  "newStatus": "EN_USO",
  "inventoryNotice": { "status": "PENDING", "eventId": "c9b2f536-8a41-4d07-95ec-3f1a0d7b2e58" }
}
```

`inventoryNotice.status` es el de la *outbox* en ese instante, casi siempre `PENDING`: el aviso sale después del *commit*. **No se espera a que el Módulo 1 lo reciba para responder**, que es FR-007 y SC-005.

Una segunda llamada sobre la misma reserva responde `200` con el mismo `useStartedAt` y el mismo `eventId`, sin registrar ni enviar nada nuevo (FR-008).

**Errores**:

| Caso | Respuesta |
|---|---|
| Quien pide no es el titular | `403` |
| La reserva no está `CONFIRMADA`, o es un bloqueo académico sin titular | `409` `USO-001` |
| Falta más de 10 minutos para el inicio, o la franja ya terminó | `409` `USO-002` |
| El Módulo 1 reporta el recurso `EN_MANTENIMIENTO` | `409` `RES-004` con `detectedStatus`, como UC2 |

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/use-start-rejected",
  "title": "No se pudo registrar el inicio de uso",
  "status": 409,
  "detail": "Tu reserva empieza a las 10:00. Puedes registrar tu llegada desde las 09:50.",
  "instance": "/api/reservations/9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45/start-use",
  "code": "USO-002",
  "windowOpensAt": "2026-09-01T09:50:00-05:00"
}
```

Los 10 minutos no son nuevos: son la misma ventana con la que UC9 mide la ausencia y UC4 corta la cancelación, para que no haya un tercer umbral que mantener. `USO-001` y `USO-002` son **propuesta de este plan** y hay que añadirlos al [diccionario consolidado](../specs/spec-modulo2.md#diccionario-de-errores-consolidado).

---

### 4. El aviso al Módulo 1 — `module2.resource.status.v1`

Lo único que el Módulo 2 le manda al Módulo 1 (FR-004). Un solo tipo de evento y un solo valor posible de `newStatus`:

```json
{
  "eventId": "c9b2f536-8a41-4d07-95ec-3f1a0d7b2e58",
  "type": "ResourceUseStarted",
  "version": 1,
  "occurredAt": "2026-09-01T10:03:51-05:00",
  "source": "MODULE_2",
  "data": {
    "resourceId": "ESP-0107",
    "resourceCategory": "ESPACIO",
    "reservationId": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
    "occupancy": { "start": "2026-09-01T10:00:00-05:00", "end": "2026-09-01T12:00:00-05:00" },
    "newStatus": "EN_USO",
    "useEndsAt": "2026-09-01T12:00:00-05:00",
    "reason": "USE_STARTED"
  }
}
```

Sale por la *outbox*, con clave de partición `reservation_id` y el envoltorio común del plan general. Lo que el Módulo 1 tiene que saber de su lado:

- **Deduplica por `eventId`**, porque la entrega es *at-least-once*. Es lo mismo que le pedimos al Módulo 3 (UC10 FR-005), y es lo que hace que FR-008 se cumpla de punta a punta.
- **`newStatus` es siempre `EN_USO`.** `occupancy` va para que sepa de qué tramo habla y pueda ordenar, no para que lo guarde como una reserva: `RESERVADO` y `BLOQUEO_ACADEMICO` no viajan nunca (FR-012).
- **`useEndsAt` es la instrucción de cierre, y es lo que evita un segundo evento** (FR-004a). En un **espacio** trae la hora de fin, y a partir de ahí el Módulo 1 deja de tenerlo `EN_USO` por su cuenta. En un **activo** llega **nulo**: su uso no termina a una hora sino con la devolución, que se la reporta el Módulo 3, y por eso no coincide con `occupancy.end`, que es el vencimiento del plazo. Un activo vencido y no devuelto sigue `EN_USO`, que es FR-011 visto desde su lado.
- **No le avisamos del regreso a `DISPONIBLE`.** Con `useEndsAt` no hace falta para un espacio, y para un activo el hecho lo constata el Módulo 3. No hay un `ResourceUseEnded`, y añadirlo sería cambiar el spec, no el contrato.
- **Una clase emite el mismo evento.** Cuando un bloqueo académico llega a su hora de inicio sale este evento igual, con `useEndsAt` en la hora de fin de la clase y sin titular —que de todas formas no viaja—. Para el Módulo 1 es indistinguible de una reserva estudiantil, y así tiene que ser: a su inventario solo le importa si el salón está en uso.
- `reason` viaja aunque hoy solo pueda valer `USE_STARTED`, para que añadir un motivo algún día no rompa el contrato.
- `resourceCategory` vale `ESPACIO` o `ACTIVO`, y `source` es fijo: están porque al Módulo 1 le sirven para enrutar, no porque los necesite el cambio de estado.
- **No viaja quién usa el recurso.** Para poner un recurso `EN_USO` no hace falta la identidad de la persona, y el nombre y el correo son datos personales que el inventario no necesita; con el `reservationId` puede preguntar lo que le falte.
- **Tampoco viaja quién entregó el activo ni en qué estado se entregó.** Eso lo constata el Módulo 3 y se lo reporta directamente, igual que los daños.

**El interruptor.** `reservations.module1.notify-use-started` llega **encendido**. Apagarlo deja de insertar el aviso en la *outbox* y los registros nacen `NOT_SENT`, sin tocar nada más: es la válvula para la demo o para el día en que el Módulo 1 todavía no tenga el topic creado, no un estado de diseño.

---

### 5. Fixtures compartidos

```text
src/test/resources/contratos/
├── api-status-changes.json
├── api-start-use.json
└── event-resource-use-started.json
```

---

## Phase 1: Setup

- [ ] T001 Añadir `reservations.module1.notify-use-started=true` y `reservations.academic.use-start-enabled=true` a `application.properties` y a `ReservationProperties`, y dejar documentado que son válvulas de operación y no pendientes de diseño

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T002 Escribir `V9__status_change.sql` con la tabla de [Contratos §1](#1-la-tabla-status_change) y sus dos índices, y `V11__reservation_use_started_at.sql` con la columna nueva
- [ ] T003 [P] Crear `StatusChange` y `StatusChangeReason` —con los siete motivos de la tabla de decisiones— en `domain/model/resource/`
- [ ] T004 [P] Definir `StatusChangeRepositoryPort`, `InventoryStatusNotifierPort` —cuyo único método recibe un inicio de uso, no un estado— y `RegisterUseStartedPort`, y completar `UpdateResourceStatusPort` con el motivo y la ocupación
- [ ] T005 [P] Crear la entidad JPA, el repositorio y `StatusChangePersistenceAdapter`, con la consulta paginada por recurso
- [ ] T006 [P] Crear `ResourceStatusOutboxAdapter`: inserta el evento de [Contratos §4](#4-el-aviso-al-módulo-1--module2resourcestatusv1) en `outbox_message` —con `useEndsAt` en un espacio y nulo en un activo— y devuelve su id para guardarlo en el registro; registra el topic en el publicador existente

**Checkpoint**: existe dónde registrar los cambios y por dónde sale el aviso

---

## Phase 3: User Story 1 - Mantener al día la ocupación de cada recurso (Priority: P1)

**Goal**: Cada cambio de ocupación queda registrado con su motivo y su franja; el inicio de uso se le avisa al Módulo 1 —lo registre una persona o lo traiga la hora de una clase— y nada más se le avisa; ningún recurso puede estar en dos estados para el mismo momento; un espacio se libera al terminar su franja y un activo solo con su check-out; y ninguna operación de negocio falla por el estado ni por el aviso.

**Independent Test**: No se prueba sola, y eso es parte de su naturaleza: se prueba comprobando que los casos de uso que la invocan dejan el registro y que los invariantes se sostienen. Con el perfil local: reservar, registrar la llegada, cancelar, cargar un horario con una clase que empiece en dos minutos, registrar una ausencia y cerrar un préstamo, y comprobar que `GET /api/resources/{id}/status-changes` cuenta la historia completa de ese recurso y que por Kafka salieron **dos** eventos y no más: la llegada y el inicio de la clase.

### Tests for User Story 1

- [ ] T007 [P] [US1] Pruebas en `UpdateResourceStatusUseCaseTest.java`: los siete motivos producen su registro con la franja y los dos estados correctos; el registro se escribe en la transacción de quien invoca; `previous_status_module2` nunca vale `EN_MANTENIMIENTO` (la decisión de diseño); y solo `USE_STARTED` nace `PENDING` con su `outbox_message_id` (FR-012)
- [ ] T008 [P] [US1] Pruebas en `RegisterUseStartedUseCaseTest.java`: marca `use_started_at` y, en un activo, también `picked_up_at`; una segunda llamada no vuelve a marcar, registrar ni avisar (FR-008); fuera de la ventana de 10 minutos y sobre una reserva no `CONFIRMADA` se rechaza; con el recurso `EN_MANTENIMIENTO` no se marca ni se avisa (FR-005); con el Módulo 1 sin responder sí se marca y el aviso queda encolado; y el `useEndsAt` del aviso es la hora de fin en un espacio y nulo en un activo (FR-004a)
- [ ] T008b [P] [US1] Pruebas en `AcademicUseStartSchedulerTest.java` con reloj inyectado: un bloqueo que llega a su hora produce **un** aviso con el tramo de la clase (escenario 7); antes de su hora no produce ninguno (escenario 6); un bloqueo cancelado o desplazado antes de empezar no produce ninguno; dos corridas seguidas tampoco lo repiten; y un salón que el Módulo 1 reporta `EN_MANTENIMIENTO` queda fuera del lote sin frenar a los demás
- [ ] T009 [P] [US1] Prueba `StatusChangeIT.java`: cada caso de uso que invoca UC7 deja **exactamente un** registro por ocupación afectada —y una carga de UC3 con 50 bloqueos deja 50—; un *rollback* de la operación no deja ninguno ni deja un aviso en la *outbox* (FR-009)
- [ ] T010 [P] [US1] Prueba `ResourceStatusCoherenceIT.java` para FR-006 y SC-003: con N hilos reservando, cancelando y cargando horarios sobre el mismo recurso, **nunca** quedan dos ocupaciones confirmadas solapadas, y la etiqueta que calcula UC1 es siempre una sola para cada instante; con N hilos registrando el mismo inicio de uso, sale **un** aviso
- [ ] T011 [P] [US1] Prueba de FR-010: una franja que termina deja el recurso disponible en la consulta siguiente **sin que ninguna tarea haya corrido**, y si hay otra reserva encima, esa manda; y de FR-011: un préstamo entregado y vencido sigue ocupando y **no** se libera por el paso del tiempo
- [ ] T012 [P] [US1] Prueba de FR-005: un recurso que el Módulo 1 reporta `EN_MANTENIMIENTO` con una reserva confirmada encima se muestra `EN_MANTENIMIENTO`; cancelar esa reserva **no** lo saca de mantenimiento; ninguna operación nuestra escribe ese estado; y el inicio de uso sobre él se rechaza con `RES-004`
- [ ] T013 [P] [US1] Prueba de arquitectura de FR-012: `InventoryStatusNotifierPort` no tiene ningún método que acepte un estado arbitrario, y el único motivo que lo invoca es `USE_STARTED`. Es la prueba que impide que un caso de uso futuro le empiece a mandar `RESERVADO` sin que nadie lo note
- [ ] T014 [P] [US1] Prueba `UseStartedNotificationIT.java` con Testcontainers de Kafka: el evento llega con el contenido del *fixture*; el Módulo 1 caído **30 minutos** no pierde el aviso y lo recibe al volver (SC-004); `notified_status` pasa de `PENDING` a `SENT`; al agotar los intentos queda `FAILED`; y con una reserva confirmada, una cancelación y un check-out **no sale ningún evento** hacia ese topic (FR-012)
- [ ] T015 [P] [US1] Prueba de SC-005: con el adaptador del aviso lanzando una excepción a propósito, una reserva, una cancelación y un registro de inicio de uso **se completan igual**
- [ ] T016 [P] [US1] Pruebas `StatusChangeControllerTest.java` y `StartUseControllerTest.java` contra los *fixtures*: el historial paginado y ordenado, el campo `note` presente, el filtrado por motivo, el `403` por rol y la respuesta sin `resourceName` cuando el Módulo 1 no contesta; y en el `POST`, el `200` con su `inventoryNotice`, la repetición idempotente, el `403` del no titular y los dos códigos nuevos

### Implementation for User Story 1

- [ ] T017 [US1] Completar `UpdateResourceStatusUseCase`: recibe el motivo y la ocupación, calcula el estado anterior con los datos del Módulo 2, escribe el registro y, solo con `USE_STARTED`, llama a `InventoryStatusNotifierPort` y guarda el `outbox_message_id` (depende de T003 a T006)
- [ ] T018 [US1] Implementar `RegisterUseStartedUseCase`: comprueba titular, estado de la reserva y ventana; pide la ficha al Módulo 1 **antes** de abrir la transacción; marca `use_started_at` —y `picked_up_at` en un activo— con el `UPDATE` condicional que da la idempotencia; calcula el `useEndsAt` según la categoría; e invoca UC7 con el motivo `USE_STARTED` (depende de T017)
- [ ] T018b [US1] Implementar `AcademicUseStartScheduler`: cada minuto, los bloqueos `CONFIRMADA` con la hora de inicio pasada y sin `use_started_at`, comprobados contra el estado operativo **por lote**, y uno a uno por `RegisterUseStartedPort` sin titular ni ventana (depende de T018)
- [ ] T019 [US1] Pasar el motivo desde los invocadores: `ReserveResourcesUseCase`, `ApplyScheduleUseCase`, `CancelReservationUseCase`, `ReceiveNoShowUseCase`, `ReceiveCheckOutUseCase` y `DeclareLoanLostUseCase` (depende de T017)
- [ ] T020 [US1] Implementar `StatusChangeController` y `StartUseController` según [Contratos §2](#2-get-apiresourcesresourceidstatus-changes) y [§3](#3-post-apireservationsreservationidstart-use), y añadir `USO-001` y `USO-002` al manejador de errores
- [ ] T021 [US1] Añadir `StartUseButton.tsx` en la lista de mis reservas: visible solo en una reserva de hoy dentro de su ventana, y después de pulsarlo la reserva se muestra "en uso"
- [ ] T022 [US1] Registrar los beans de UC7 en `UseCasesConfig` y actualizar [modelo-datos-der.md](./modelo-datos-der.md) con la tabla `status_change` y la columna `use_started_at`, que el plan general había dejado pendientes

**Checkpoint**: UC7 queda cerrado salvo por quién llama al endpoint de inicio de uso

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T023 [P] Verificar SC-001 con una auditoría sobre datos de prueba: para cada franja revisada, la etiqueta que muestra UC1 coincide con lo que cuenta el historial de `status_change`
- [ ] T024 [P] Verificar SC-002: un recurso liberado aparece disponible en menos de 5 s, con los tres motivos de liberación (cancelación, ausencia y check-out)
- [ ] T025 [P] Documentar en el README que el estado del recurso no es una columna sino la consecuencia de las ocupaciones, y que el único estado que le mandamos al Módulo 1 es el inicio de uso
- [ ] T026 Llevar a `pendientes-clarificacion.md` lo que este plan deja abierto: el medio definitivo del registro de llegada de una persona (P-02) y el contrato del topic con el equipo del Módulo 1, incluido que ellos cierren el uso con `useEndsAt`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: depende de UC2, UC3, UC4, UC9 y UC12, que son los que invocan este caso de uso. UC7 **no se puede hacer antes** que ellos, aunque sea P1: no hay nada que registrar hasta que existan los cambios
- **Foundational (Phase 2)**: depende de Setup - BLOCKS la user story
- **User Story 1 (Phase 3)**: depende de Foundational
- **Polish (Phase 4)**: depende de la Phase 3

### Dependencias con otros casos de uso

- **UC3 `Importar horarios semestrales`**: es el único invocador que deja trabajo pendiente para después. Sus bloqueos no avisan nada al crearse, pero cada uno dispara su aviso al llegar su hora, y de eso se encarga la tarea de este plan, no UC3.
- **UC2, UC3, UC4, UC9 y UC12**: son los que invocan UC7, y cada uno ya implementó su parte —dejar la ocupación escrita o liberada—. Lo que este plan les añade es una línea: pasar el motivo. Si alguno se salta la llamada, FR-001 se incumple sin que nada falle, y por eso la prueba de T009 los recorre.
- **UC1 `Consultar recursos`**: es quien **lee** el resultado de UC7. Su tabla de prioridad es la que convierte en una etiqueta lo que podrían ser varios estados a la vez, y el `EN_USO` que muestra es el que el Módulo 1 devuelve después de nuestro aviso. No hay que tocarlo.
- **UC8 `Consultar disponibilidad`**: la consulta de ocupaciones que UC1 y UC2 usan es lo que hace cumplir FR-010 y FR-011. Tampoco hay que tocarlo.
- **UC4 `Cancelar reserva`**: recibe el rebote del edge case de mantenimiento. Un inicio de uso rechazado por `EN_MANTENIMIENTO` es, de hecho, la señal de que ese recurso tiene reservas que hay que cancelar por UC4 FR-010.
- **UC10 y UC11**: de ellos se reusan la *outbox*, el publicador y la forma del `delivery`. El aviso de UC7 es el tercer productor de eventos del módulo y el primero cuyo destinatario es el Módulo 1.
- **Módulo 1**: recibe un solo evento, `ResourceUseStarted`, y nada más (FR-012).

### Within User Story 1

- Esquema (T002) → modelo (T003) → puertos (T004) → persistencia y adaptador del aviso (T005, T006) → `UpdateResourceStatusUseCase` (T017) → `RegisterUseStartedUseCase` (T018) → la tarea de las clases (T018b) → invocadores (T019)
- Controladores (T020), frontend (T021) y beans (T022) al final

### Parallel Opportunities

- En Foundational: T003 a T006
- En User Story 1: todas las pruebas (T007 a T016, incluida T008b)
- En Polish: T023 a T025

## Notes

- La numeración `T0XX` es propia de este plan
- [P] tasks = different files, no dependencies
- Verify tests pass
- Commit after each task or logical group
- **Lo que ya estaba hecho y aquí solo se documenta**: FR-002 (UC2 y UC12), FR-006 y SC-003 (la restricción de exclusión), FR-010 y FR-011 (la consulta de ocupaciones de UC1), FR-005 en su mitad de escritura (el mantenimiento no es nuestro). Lo nuevo es el registro de FR-009 y el aviso de FR-004
- **Decisiones de los contratos que el spec no fija**: el estado anterior se guarda solo con los datos del Módulo 2 y la columna se llama `previous_status_module2` para no confundirlo con la etiqueta completa; `notified_status` nace `PENDING` solo en el motivo que avisa; el historial lleva un campo `note` fijo que explica qué no está ahí; el aviso va por la *outbox* y no por REST síncrono (FR-007, SC-005); la ventana para registrar la llegada es la misma de 10 minutos de UC9 y UC4; y `USO-001` y `USO-002` son códigos nuevos que hay que añadir al diccionario
- **Decisión explícita de no hacer**: ninguna tarea programada que libere ocupaciones ni recalcule estados. La única que hay empieza el uso de las clases, porque ese hecho no lo registra nadie más, y no toca nada que el modelo ya sepa deducir
