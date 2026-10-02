# Implementation Plan: Reservar recursos (UC2)

**Date**: 2026-09-28
**Spec**: [spec-modulo2-uc2-reservar-recursos.md](../specs/spec-modulo2-uc2-reservar-recursos.md)
**Plan general**: [plan-arquitectura.md](./plan-arquitectura.md)
**Plan previo**: [plan-uc1-consultar-recursos.md](./plan-uc1-consultar-recursos.md) — deja montada la base compartida (esquema, seguridad, reloj, clientes HTTP, frontend) que este plan da por hecha

## Summary

UC2 es para lo que existe el módulo: que el propio estudiante aparte un recurso sin pedírselo a nadie. Se llega desde la lista de UC1 y, antes de confirmar, el sistema revisa las reglas que deciden si puede apartarlo. Cuando la respuesta es no, nunca se dice "no se pudo": cada negativa sale con un código del diccionario de errores (`RES-001` a `RES-004`).

**Enfoque técnico:**

1. `ReserveResourcesUseCase` valida primero la **forma** de la solicitud: franja dentro de 06:00–22:00, del mismo día y de máximo 2 horas para un espacio (FR-009). Una franja mal formada es un `400`, no una denegación con código.
2. Pide la ficha del recurso al Módulo 1 por `InventoryPort` (categoría, y el **plazo máximo de préstamo** si es un activo, FR-012). Si el Módulo 1 no responde, no se confirma nada a ciegas (UC8 FR-007).
3. Aplica las reglas **en el orden de FR-002**: sanción (`RES-003`), cupo de 3 vigentes (`RES-002`) y conflicto académico u ocupación (`RES-001` / `RES-004`). Devuelve un único código, el primero que falla (edge case **Múltiples causas de denegación simultáneas**).
4. Para un **activo**, la persona solo elige la hora de recogida: el vencimiento lo calcula el sistema sumando el plazo en **días hábiles** y fijándolo a las **22:00** de ese día (FR-013, FR-014), y se lo informa antes de confirmar.
5. Escribe la reserva en una sola transacción, donde la **restricción de exclusión** de PostgreSQL es la que garantiza que dos ocupaciones solapadas no puedan coexistir aunque dos personas den clic a la vez (FR-004). Si la restricción salta, la respuesta es `RES-004`.
6. En esa misma transacción inserta el evento de la ficha en la *outbox* (`<<include>>` a UC10, FR-018) y deja la ocupación escrita, que es lo que hoy significa `Actualizar estado de los recursos` (`<<include>>` a UC7, FR-011).
7. Toda denegación queda registrada con su código, usuario, recurso y marca de tiempo (FR-005), en su propia transacción para que sobreviva al *rollback*.

Además de la reserva, este plan implementa la **renovación única** del préstamo (FR-016, FR-017) y la parte de UC8 que UC2 necesita —la consulta de un solo recurso, con el "hasta cuándo" de UC8 FR-012—, que el plan de UC1 dejó pendiente.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript con Next.js y React (frontend)
**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Validation, RestClient), **Spring for Apache Kafka** (`spring-kafka`, nuevo en este plan), Flyway, springdoc-openapi
**Storage**: PostgreSQL con `btree_gist`. UC2 **escribe** `reservation`, `loan`, `denial` y `outbox_message`, y lee `academic_block` y `app_user`.
**Testing**: JUnit 5 y AssertJ (dominio y casos de uso con puertos falsos), `@WebMvcTest` (controladores), Testcontainers con PostgreSQL (persistencia, restricción de exclusión y concurrencia) y con Kafka (publicador de la *outbox*), WireMock (Módulos 1 y 3), ArchUnit
**Target Platform**: Servidor Linux con JVM 21; navegador web para el frontend
**Project Type**: Web: backend en la raíz del repositorio y frontend en `frontend/`
**Performance Goals**: La confirmación responde dentro del presupuesto de sus dos llamadas síncronas: hasta 5 s del Módulo 1 (ficha del recurso) y hasta 2 s del Módulo 3 (sanciones, UC6 SC-002). La escritura en PostgreSQL no debe añadir un tiempo apreciable. SC-001 mide el flujo completo consultar + reservar en menos de 2 minutos, que es tiempo de la persona, no del sistema.
**Constraints**:
- Cupo de **3 reservas vigentes** por persona, único para espacios y activos, parametrizable (FR-008).
- Máximo **2 horas continuas** por franja de espacio, parametrizable (FR-009).
- Vencimiento de un préstamo siempre a las **22:00** del día hábil que toca (FR-013), y el plazo lo manda el Módulo 1, nunca una tabla nuestra (FR-012).
- Un activo prestado sigue ocupado hasta su check-out, incluso vencido (FR-015).
- Cero ocupaciones solapadas confirmadas, también bajo concurrencia (FR-004, SC-004).
- Zona horaria `America/Bogota` y ventana de 06:00 a 22:00 del mismo día.
**Scale/Scope**: Mismo orden que UC1 (hasta 500 usuarios concurrentes en pico). Dos pantallas nuevas en el frontend: la confirmación sobre la lista de UC1 y una lista mínima de "mis reservas" que sirve de entrada a la renovación.

## Project Structure

### Documentation (this feature)

```text
docs/
├── specs/
│   ├── spec-modulo2-uc2-reservar-recursos.md                   # spec de este plan
│   ├── spec-modulo2-uc8-consultar-disponibilidad-recursos.md   # <<include>>: consulta de un recurso
│   ├── spec-modulo2-uc6-consultar-sanciones.md                 # <<include>>: denegación RES-003
│   ├── spec-modulo2-uc7-actualizar-estado-recursos.md          # <<include>>: deja la ocupación escrita
│   └── spec-modulo2-uc10-reportar-informacion-reserva.md       # <<include>>: ficha hacia el Módulo 3
└── plan/
    ├── plan-arquitectura.md             # plan general: capas, carpetas, base de datos y Kafka
    ├── plan-uc1-consultar-recursos.md   # base compartida que este plan da por hecha
    └── plan-uc2-reservar-recursos.md    # este archivo
```

### Source Code (repository root)

Solo los archivos que crea o toca este plan; los que ya existen vienen del plan de UC1 y van marcados. La organización completa está en [plan-arquitectura.md](./plan-arquitectura.md#organización-de-carpetas).

```text
build.gradle                                        # + spring-kafka y Testcontainers de Kafka (T001)
docker-compose.yml                                  # PostgreSQL + Kafka para desarrollo (T002)

src/main/java/edu/unimagdalena/reservasunimag/
├── domain/
│   ├── model/
│   │   ├── reservation/
│   │   │   ├── Reservation.java                        # nueva: titular, recurso, ocupación, estado, origen
│   │   │   ├── ReservationStatus.java                  # CONFIRMADA, CANCELADA, CANCELADA_POR_*, FINALIZADA
│   │   │   ├── ReservationOrigin.java                  # ESTUDIANTIL, ACADEMICO
│   │   │   ├── Loan.java                       # plazo aplicado, renovado, entrega y devolución
│   │   │   ├── LoanPeriod.java              # recogida → vencimiento (puede durar días)
│   │   │   ├── TimeSlot.java                  # (existe) + tope de 2 horas de FR-009
│   │   │   └── Occupancy.java                      # (existe)
│   │   ├── resource/
│   │   │   └── ResourceDetails.java                   # ficha de un recurso con su plazo de préstamo
│   │   └── error/
│   │       ├── DenialCode.java               # RES-001 a RES-004
│   │       ├── ReservationDeniedException.java       # código + datos del mensaje
│   │       ├── DurationExceededException.java      # FR-009: franja mal formada, no denegación
│   │       └── RenewalDeniedException.java    # motivo de FR-017
│   ├── port/
│   │   ├── in/
│   │   │   ├── ReserveResourcesPort.java
│   │   │   ├── RenewLoanPort.java
│   │   │   ├── UpdateResourceStatusPort.java   # UC7 (la parte vigente mientras P-20 esté abierto)
│   │   │   └── ReportReservationPort.java # UC10
│   │   └── out/
│   │       ├── ReservationRepositoryPort.java          # confirmar, vigentes, buscar préstamo, renovar
│   │       ├── DenialRepositoryPort.java       # FR-005
│   │       ├── Module3NotifierPort.java         # outbox: ficha y (más adelante) cancelación
│   │       ├── BusinessCalendar.java                # días hábiles de FR-014
│   │       └── InventoryPort.java                 # (existe) + ficha(resourceId) con el plazo
│   └── usecase/
│       ├── reserveresources/
│       │   ├── ReserveResourcesUseCase.java
│       │   ├── ReservationRequest.java             # comando: recurso, franja o recogida, origen
│       │   ├── ReservationResult.java
│       │   └── DueDateCalculator.java         # plazo hábil + 22:00 (FR-013, FR-014)
│       ├── renewloan/
│       │   └── RenewLoanUseCase.java
│       ├── updatestatus/
│       │   └── UpdateResourceStatusUseCase.java
│       ├── reportreservation/
│       │   └── ReportReservationUseCase.java
│       └── checkavailability/
│           └── CheckAvailabilityUseCase.java # (existe) + un recurso y "hasta cuándo" (UC8 FR-012)
│
└── infrastructure/
    ├── adapter/
    │   ├── in/web/
    │   │   ├── reservation/
    │   │   │   ├── ReservationController.java          # POST /api/reservations, GET /api/reservations/mias
    │   │   │   ├── LoanController.java         # POST .../renovacion, GET vencimiento previsto
    │   │   │   ├── CreateReservationRequest.java
    │   │   │   ├── ReservationResponse.java
    │   │   │   └── MyReservationsResponse.java
    │   │   └── error/
    │   │       └── GlobalErrorHandler.java     # (existe) + 409 con el código de denegación
    │   └── out/
    │       ├── persistence/
    │       │   ├── entity/                         # + LoanJpa, DenialJpa, OutboxMessageJpa
    │       │   ├── repository/                     # + ReservationJpaRepository, DenialJpaRepository,
    │       │   │                                   #   OutboxMessageJpaRepository
    │       │   ├── ReservationPersistenceAdapter.java  # la transacción de confirmación
    │       │   └── DenialPersistenceAdapter.java
    │       └── messaging/
    │           ├── OutboxNotifierAdapter.java   # → Module3NotifierPort
    │           ├── OutboxPublisher.java           # @Scheduled: outbox_message → Kafka
    │           └── evento/ReservationRecordEvent.java
    └── config/
        ├── ReservationProperties.java                # (existe) + cupo, tope de franja, festivos
        ├── KafkaConfig.java                        # productor idempotente, acks=all
        └── UseCasesConfig.java                   # (existe) + los beans y transacciones de UC2

src/main/resources/
├── application.properties                          # + cupo, tope, festivos y bloque de Kafka
└── db/migration/
    └── V3__denial_and_outbox.sql                 # tablas denegacion y outbox_message

src/test/java/edu/unimagdalena/reservasunimag/
├── domain/model/reservation/FranjaHorariaTest.java     # (existe) + tope de 2 horas
├── domain/usecase/reserveresources/
│   ├── ReservarRecursosUseCaseTest.java
│   └── CalculadoraVencimientoTest.java
├── domain/usecase/renewloan/RenovarPrestamoUseCaseTest.java
└── infrastructure/adapter/
    ├── in/web/reservation/ReservaControllerTest.java
    ├── out/persistence/ReservaPersistenceAdapterIT.java
    ├── out/persistence/ReservationConcurrencyIT.java
    └── out/mensajeria/PublicadorOutboxIT.java

src/test/resources/contratos/                       # + los JSON de la sección Contratos (T020)

frontend/src/
├── app/(app)/resources/page.tsx                     # (existe) + botón reservar sobre los seleccionables
├── app/(app)/my-reservations/page.tsx                 # lista mínima: entrada a la renovación
└── features/reservations/
    ├── ConfirmReservation.tsx                        # franja o recogida + vencimiento previsto
    ├── DenialMessage.tsx                       # traduce RES-001 a RES-004
    ├── MyReservationsList.tsx
    ├── RenewLoan.tsx
    ├── api.ts
    └── types.ts
```

**Structure Decision**: se mantiene la estructura del plan general y del plan de UC1. Dos añadidos: la carpeta `infrastructure/adapter/out/messaging`, que aparece aquí por primera vez porque UC10 es el primer `<<include>>` que publica a Kafka, y la pantalla `(app)/my-reservations`, que se crea mínima —solo listar y renovar— porque la renovación de FR-016 necesita una entrada en la interfaz; `Cancelar reserva` (UC4) la completa con su propio plan.

### Decisiones de diseño de este caso de uso

**Qué se valida y en qué orden.** El spec pide dos cosas distintas que conviene no mezclar: una franja mal formada se rechaza **antes** de todo lo demás y no lleva código (FR-009, edge case **Reserva más larga que el tope**), y las reglas de negocio se evalúan en el orden de FR-002 devolviendo un solo código.

| Paso | Qué comprueba | Si falla |
|---|---|---|
| 0 | Franja dentro de 06:00–22:00, del mismo día, `inicio < fin`, y máximo 2 horas si es un espacio | `400` con el tope y la sugerencia de ajustar la franja |
| 0 | Ficha del recurso en el Módulo 1; un activo sin **plazo máximo de préstamo** no se puede prestar (FR-012, P-16) | `404` si no existe, `503` si el Módulo 1 no responde |
| 1 | Sanción vigente del titular, contra el Módulo 3 (UC6) | `RES-003` con motivo y fecha de fin |
| 2 | Cupo de 3 reservas vigentes (FR-008) | `RES-002` con "3 de 3" y cuándo se libera la próxima |
| 3 | Conflicto académico u ocupación, contra UC8 y contra la propia base (FR-006) | `RES-001` o `RES-004` |
| 4 | La restricción de exclusión al insertar (FR-004) | `RES-004` |

**De qué motivo sale cada código.** UC8 responde con un motivo, y este caso de uso lo traduce:

| Motivo que devuelve UC8 | Código | Por qué |
|---|---|---|
| `BLOQUEO_ACADEMICO` | `RES-001` | Es literalmente el conflicto académico del diccionario; cubre el edge case **Solapamiento parcial** (09:30–10:30 contra una clase de 08:00–10:00). |
| `RESERVADO` o `EN_USO` de otra persona | `RES-004` | Desde la lista aparecía libre y ya no lo está; es "el recurso acaba de ser tomado". No se revela quién lo tiene (UC8 FR-005). |
| `EN_MANTENIMIENTO` | `RES-004` provisional | NEEDS CLARIFICATION: el diccionario no tiene código para mantenimiento. Se responde con `RES-004` y un mensaje propio, y se propone al equipo un `RES-005`. |

**Sanción que no se puede comprobar.** Si el Módulo 3 no responde, la reserva **no** se confirma: se responde `503` con "no se pudo comprobar tu situación". Es la opción conservadora del edge case de UC6 y de su FR-006, y la diferencia con UC1, donde la consulta sigue funcionando con un aviso. (NEEDS CLARIFICATION: P-10 sigue abierto; si la universidad elige seguir operando, cambia solo esta rama.) El **alcance** de la sanción (espacios, activos o ambos) tampoco está cerrado (P-11): hoy cualquier sanción vigente bloquea cualquier reserva, y el alcance se lee del reporte si viene.

**Cómo se cuenta el cupo (FR-008).** Cuenta como vigente toda reserva `CONFIRMADA` de un espacio cuya franja no ha terminado, y todo préstamo de un activo mientras no tenga check-out, aunque esté vencido:

```sql
SELECT count(*)
FROM reservation r
LEFT JOIN loan p ON p.reservation_id = r.id
WHERE r.user_id = :userId
  AND r.status = 'CONFIRMADA'
  AND ( (r.resource_category = 'ESPACIO' AND r.ends_at > :ahora)
     OR (r.resource_category = 'ACTIVO'  AND p.returned_at IS NULL) );
```

Para el mensaje de `RES-002` se toma el `end` más próximo entre las vigentes; si la más próxima es un préstamo ya vencido y sin devolver, el mensaje dice que ese cupo se libera cuando se registre la devolución, no una fecha.

**Cómo se garantiza que no haya cruces (FR-004, SC-004).** La `EXCLUDE USING gist` sobre `(resource_id, ocupacion)` con `WHERE (estado = 'CONFIRMADA')` es la única garantía real: dos peticiones simultáneas pasan las dos la comprobación previa y solo una sobrevive al `INSERT`. El adaptador de persistencia traduce esa violación a `RES-004`, y esto vale igual para dos franjas de un espacio, dos periodos de préstamo de un activo y la mezcla con un bloqueo académico. La comprobación previa de UC8 no se quita: sirve para dar un código exacto (`RES-001` frente a `RES-004`) en el caso normal.

**Dónde empieza y acaba la transacción.** Las llamadas HTTP a los Módulos 1 y 3 se hacen **fuera** de la transacción de escritura, para no tener una transacción abierta esperando a otro sistema. La transacción hace solo lo que debe ser atómico, y es un método del adaptador de persistencia:

1. `SELECT ... FOR UPDATE` de la fila del titular en `app_user`, que serializa las reservas de una misma persona y evita que dos peticiones suyas se cuelen las dos en el cupo.
2. Contar el cupo vigente (la comprobación que decide; la del paso 2 de la tabla es la que da el mensaje temprano).
3. Volver a comprobar la ocupación en nuestra base, que cuesta milisegundos.
4. `INSERT` de `reservation`, del `loan` si es un activo, y del evento en `outbox_message`.

El puerto `ReservationRepositoryPort.confirmar(...)` devuelve un resultado de dominio (`Confirmada`, `CupoAgotado`, `RecursoTomado`) y el caso de uso lo traduce al código. Así la regla sigue leyéndose en el dominio y la atomicidad vive donde puede garantizarse. Los casos de uso siguen sin `@Service` ni `@Transactional`: eso se configura en `infrastructure/config` (plan general).

**Préstamo vencido y no devuelto (FR-015).** La ocupación de un activo es `[pickup, dueAt)`, así que la restricción de exclusión deja de proteger justo cuando el plazo vence, y el activo sigue en manos de alguien. Por eso la comprobación de disponibilidad no se limita al rango: un préstamo con `picked_up_at` y sin `returned_at` ocupa desde su inicio y sin fin, igual que ya hace la consulta de UC1. Dentro de la transacción esos préstamos abiertos del recurso se leen con `FOR UPDATE`, para que un *check-out* concurrente no se cruce con una reserva nueva. Queda una ventana estrecha —dos peticiones nuevas sobre un activo vencido y no devuelto— que se cierra con esa misma comprobación; se documenta porque depende de P-08, que sigue abierto.

**Cálculo del vencimiento (FR-012, FR-013, FR-014).** `DueDateCalculator` suma el plazo en días hábiles a partir del **día siguiente** a la recogida y fija la hora a las 22:00 de `America/Bogota`. Con el ejemplo del spec: recogida el martes 2026-09-01 a las 14:30 con plazo 7 → se saltan el sábado 5 y el domingo 6 → **jueves 2026-09-10 a las 22:00**. Un plazo `0` vence a las 22:00 del mismo día de la recogida (uso en sitio). Los días no hábiles salen del puerto `BusinessCalendar`, cuya primera implementación excluye sábados y domingos más una lista de festivos en `application.properties`. (NEEDS CLARIFICATION: FR-014 habla de "días en que la universidad no abre" y nadie nos da ese calendario; mientras no exista, la lista se mantiene a mano.)

**Renovación (FR-016, FR-017).** Es el mismo préstamo: no consume cupo nuevo, `loan.renewed` pasa a `true` y `reservation.ends_at` se mueve al nuevo vencimiento, que se calcula **desde el vencimiento vigente** y no desde hoy. Los cinco rechazos de FR-017 se devuelven como `409` con un motivo enumerado, porque el diccionario de errores de UC2 no cubre la renovación:

| Motivo | Cuándo |
|---|---|
| `ALREADY_RENEWED` | `loan.renewed` ya es `true` |
| `LOAN_OVERDUE` | `ahora > reservation.ends_at`; el corte son las 22:00 del día del vencimiento (edge case **Renovación pedida el mismo día del vencimiento**) |
| `ACTIVE_SANCTION` | el Módulo 3 reporta sanción vigente; si no responde, `503` |
| `OVERLAPS_ANOTHER_RESERVATION` | el nuevo rango choca con otra ocupación; lo detecta la restricción de exclusión al actualizar `reservation.ends_at` |
| `NO_EXTENSION` | el activo es de plazo `0` |

Al renovar sale una ficha nueva hacia el Módulo 3 con el vencimiento actualizado (UC10 FR-003).

**`Actualizar estado de los recursos` dentro de UC2 (FR-011).** Mientras P-20 siga abierto, el Módulo 2 **no le envía nada** al Módulo 1: el estado que este caso de uso "actualiza" es la ocupación de nuestra propia base, que es justamente la fila de `reservation` que se acaba de insertar. Se llama igual al puerto `UpdateResourceStatusPort` para que el `<<include>>` exista en el código y quede el registro de auditoría; la tabla `StatusChange` y el aviso al Módulo 1 se diseñan cuando se responda P-20.

**`Reportar información de la reserva` dentro de UC2 (FR-018).** El evento `ReservationRecordCreated` se inserta en `outbox_message` en la misma transacción que la reserva, y un publicador `@Scheduled` lo lleva al topic `module2.reservation.record.v1` con la clave de partición `reservation_id`, según el plan general. Si Kafka o el Módulo 3 están caídos, la reserva se confirma igual y el evento sale cuando vuelvan (UC10 FR-005).

**Registro de denegaciones (FR-005).** Se escribe en `denial` en una transacción aparte (`REQUIRES_NEW`), porque una denegación por `RES-004` ocurre cuando la transacción de la reserva ya está condenada por la violación de la restricción. Las denegaciones alimentan la reportería y no llevan datos del titular más allá de su `user_id`.

**Reservas de origen académico.** `ReservationRequest` lleva el `origin` desde ya, para que UC3 pueda reusar este caso de uso sin refactor. Con `origen = ACADEMICO` se saltan la sanción, el cupo y el tope de 2 horas, y no hay titular. (NEEDS CLARIFICATION: P-19 pregunta exactamente esto; lo de aquí es el supuesto de partida.)

## Contratos

Aquí queda definido todo el JSON de UC2: los cuatro endpoints que consume el frontend, la ficha que le pedimos al Módulo 1, lo que cambia respecto a UC1 en la consulta de sanciones y el evento que sale hacia el Módulo 3. Los ejemplos son el contrato: las pruebas de T019, T020 y T038 se escriben contra ellos y los mismos JSON viven como *fixtures* en `src/test/resources/contratos/`.

Se aplican sin repetirlas las **convenciones comunes** de [plan-uc1-consultar-recursos.md § Contratos](./plan-uc1-consultar-recursos.md#contratos): `camelCase`, enumeraciones en mayúsculas, **nunca `null`** (lo que no aplica se omite), `date` como `yyyy-MM-dd` y horas como `HH:mm` en `America/Bogota`, instantes ISO-8601 con `-05:00`, franjas `[inicio, fin)`, `resourceId` como cadena y errores RFC 9457 con `type` bajo `https://reservasunimag.unimagdalena.edu.co/errors/`.

Tres reglas propias de este caso de uso:

| Regla | Detalle |
|---|---|
| Horas de entrada, instantes de salida | La persona manda `date` + `HH:mm`, igual que en UC1. Las respuestas devuelven **instantes completos**, porque un préstamo cruza días y `"fin": "22:00"` no diría de qué día. |
| Un código por respuesta | Una denegación lleva **un solo** `code`, el primero que falla en el orden de FR-002 (edge case **Múltiples causas de denegación simultáneas**). Nunca una lista. |
| Forma mal puesta es `400` | Una franja mal formada o un cuerpo que no corresponde a la categoría del recurso es `400` **sin** `code`: no es una regla de negocio (FR-009). Los `RES-00X` van siempre en `409`. |

---

### 1. `POST /api/reservations`

**Petición de un espacio** (FR-001): la persona elige la franja completa.

```json
{ "resourceId": "ESP-0107", "date": "2026-09-01", "start": "10:00", "end": "12:00" }
```

**Petición de un activo** (FR-001): la persona elige **solo cuándo lo recoge**; el vencimiento lo calcula el sistema.

```json
{ "resourceId": "ACT-004512", "date": "2026-09-01", "pickup": "14:30" }
```

| Campo | Tipo | Forma | Validación |
|---|---|---|---|
| `resourceId` | cadena | las dos | obligatorio |
| `date` | `yyyy-MM-dd` | las dos | obligatorio |
| `start`, `end` | `HH:mm` | espacio | obligatorios; dentro de 06:00–22:00, `fin > inicio`, máximo 2 horas (FR-009) |
| `pickup` | `HH:mm` | activo | obligatorio; dentro de 06:00–22:00 |

**Decisión: el cuerpo no lleva `category`.** La categoría la manda el Módulo 1 en la ficha (paso 0 de la tabla de decisiones), no el cliente, así que nadie puede forzar la forma equivocada. El backend compara: si llega `start`/`end` para un activo, o `pickup` para un espacio, responde `400` con `codigo: "SHAPE_DOES_NOT_MATCH_CATEGORY"`. El `origin` tampoco viaja en el cuerpo: `POST /api/reservations` siempre crea una reserva `ESTUDIANTIL` con el titular del JWT. Las reservas `ACADEMICO` entran por UC3 llamando al caso de uso, nunca por este endpoint; por eso `ReservationRequest` tiene el campo y el `CreateReservationRequest` no.

**`201 Created`** — espacio (escenario 1)

```http
HTTP/1.1 201 Created
Location: /api/reservations/9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45
```

```json
{
  "id": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
  "resourceId": "ESP-0107",
  "name": "Sala de Estudio 3",
  "category": "ESPACIO",
  "status": "CONFIRMADA",
  "origin": "ESTUDIANTIL",
  "start": "2026-09-01T10:00:00-05:00",
  "end": "2026-09-01T12:00:00-05:00",
  "createdAt": "2026-08-30T09:14:22-05:00",
  "quota": { "used": 2, "max": 3 }
}
```

**`201 Created`** — activo (escenario 2: martes 2026-09-01 14:30, plazo 7 → jueves 2026-09-10 22:00)

```json
{
  "id": "4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3",
  "resourceId": "ACT-004512",
  "name": "Libro de Cálculo I",
  "category": "ACTIVO",
  "status": "CONFIRMADA",
  "origin": "ESTUDIANTIL",
  "start": "2026-09-01T14:30:00-05:00",
  "end": "2026-09-10T22:00:00-05:00",
  "loan": {
    "pickup": "2026-09-01T14:30:00-05:00",
    "dueAt": "2026-09-10T22:00:00-05:00",
    "termBusinessDays": 7,
    "renewed": false,
    "renewable": true,
    "pickedUp": false
  },
  "createdAt": "2026-08-30T09:14:22-05:00",
  "quota": { "used": 2, "max": 3 }
}
```

| Campo | Tipo | Nota |
|---|---|---|
| `id` | uuid | Identificador de la reserva (escenario 1: "devuelve el identificador"). |
| `name` | cadena | Del Módulo 1, de la ficha que ya se pidió; no lo guardamos (UC1 FR-006). |
| `status` | enumeración | `CONFIRMADA`, `CANCELADA`, `CANCELADA_POR_PRIORIDAD_ACADEMICA`, `CANCELADA_POR_RECURSO_NO_DISPONIBLE`, `FINALIZADA`. Al crear siempre es `CONFIRMADA`. |
| `start`, `end` | instantes | **La ocupación**, en las dos categorías: es la misma `[inicio, fin)` de la tabla `reservation` y lo que protege la restricción de exclusión. En un activo coincide con `pickup` y `dueAt`. |
| `loan` | objeto | Solo en activos. Repite la ocupación con nombres de negocio, que es lo que la persona entiende y lo que necesita la pantalla. |
| `prestamo.renovable` | booleano | `false` si `renewed` ya es `true` o si `termBusinessDays` es `0` (FR-017, `NO_EXTENSION`). |
| `prestamo.entregado` | booleano | `true` cuando el activo ya se recogió (`prestamo.picked_up_at`). Al crear siempre `false`. |
| `quota` | objeto | Cómo quedó el cupo **después** de confirmar, para que la pantalla no tenga que volver a preguntar (FR-008). |

El estado `RESERVADO` o `EN_USO` del recurso **no** aparece aquí: es la etiqueta que calcula UC1 al consultar, no un campo de la reserva. Lo que la confirmación deja escrito es la ocupación, y eso es todo lo que hoy significa el `<<include>>` a UC7 (FR-011).

#### Errores de `POST /api/reservations`

**`400`** — franja más larga que el tope (FR-009, edge case **Reserva más larga que el tope**)

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/invalid-time-slot",
  "title": "Franja horaria inválida",
  "status": 400,
  "detail": "Una reserva de espacio puede durar como máximo 2 horas. Ajusta la timeSlot.",
  "instance": "/api/reservations",
  "code": "DURATION_EXCEEDED",
  "limitMinutes": 120,
  "requestedMinutes": 180
}
```

El `code` de los `400` toma uno de `TIME_SLOT_OUTSIDE_WINDOW`, `TIME_SLOT_CROSSES_MIDNIGHT`, `TIME_SLOT_END_NOT_AFTER_START` —los tres que ya define `TimeSlot` en el plan de UC1— más `DURATION_EXCEEDED` y `SHAPE_DOES_NOT_MATCH_CATEGORY`. Ninguno es un `RES-00X`, y esta respuesta sale **antes** de consultar sanción, cupo o disponibilidad: las pruebas de T013 lo verifican.

**`404`** — el recurso no existe en el Módulo 1

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/resource-not-found",
  "title": "Resource no encontrado",
  "status": 404,
  "detail": "El recurso ACT-999999 no existe en el inventario.",
  "instance": "/api/reservations",
  "resourceId": "ACT-999999"
}
```

**`409`** — las cuatro denegaciones del diccionario (FR-003). Todas comparten `type`, `title` y el campo `code`; cada código agrega lo que su mensaje necesita.

`RES-001` — conflicto académico (escenario 3; cubre el cruce parcial de 09:30–10:30 contra una clase de 08:00–10:00)

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/reservation-denied",
  "title": "Reservation denegada",
  "status": 409,
  "detail": "El recurso está reservado para actividad instructor en la timeSlot solicitada.",
  "instance": "/api/reservations",
  "code": "RES-001",
  "resourceId": "ESP-0412",
  "occupiedUntil": "2026-09-01T10:00:00-05:00"
}
```

`RES-002` — cupo lleno (escenario 4: "3 de 3" y cuándo se libera la próxima)

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/reservation-denied",
  "title": "Reservation denegada",
  "status": 409,
  "detail": "Tienes 3 de 3 reservations active. La próxima se libera el 2026-09-01 a las 12:00.",
  "instance": "/api/reservations",
  "code": "RES-002",
  "active": 3,
  "max": 3,
  "nextRelease": "2026-09-01T12:00:00-05:00"
}
```

Cuando la vigente más próxima es un **préstamo vencido y sin devolver**, no hay fecha que prometer: `nextRelease` se omite y el `detail` dice `"Tienes 3 de 3 reservas vigentes. Una corresponde a un activo vencido: el cupo se libera cuando se registre su devolución."` (FR-008, FR-015).

`RES-003` — sanción activa (escenario 5: informa la fecha de finalización)

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/reservation-denied",
  "title": "Reservation denegada",
  "status": 409,
  "detail": "Tienes una sanción vigente until el 2026-09-15 y no puedes reservar.",
  "instance": "/api/reservations",
  "code": "RES-003",
  "sanctionReason": "Dos ausencias no justificadas en el semestre",
  "sanctionUntil": "2026-09-15"
}
```

`sanctionReason` y `sanctionUntil` se copian del reporte del Módulo 3 (UC6 FR-004). Si el reporte trae una sanción sin fecha de fin, `sanctionUntil` se omite y el `detail` no la menciona.

`RES-004` — el recurso acaba de ser tomado (edge case **Concurrencia sobre el mismo tiempo ocupado**)

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/reservation-denied",
  "title": "Reservation denegada",
  "status": 409,
  "detail": "El recurso acaba de ser tomado por otra solicitud. Vuelve a consultar la disponibilidad.",
  "instance": "/api/reservations",
  "code": "RES-004",
  "resourceId": "ESP-0107"
}
```

Nunca se dice **quién** lo tomó, ni en `detail` ni en ningún campo (UC8 FR-005). El mismo `RES-004` sale de los dos sitios: de la recomprobación de disponibilidad y de la violación de `reservation_no_overlap` al insertar; el cliente no los distingue, y así debe ser.

`RES-004` provisional por mantenimiento — el recurso está `EN_MANTENIMIENTO` y el diccionario no tiene código para eso:

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/reservation-denied",
  "title": "Reservation denegada",
  "status": 409,
  "detail": "El recurso está en mantenimiento y no se puede reservar.",
  "instance": "/api/reservations",
  "code": "RES-004",
  "detectedStatus": "EN_MANTENIMIENTO",
  "resourceId": "ACT-000912"
}
```

El campo `detectedStatus` existe precisamente para que esta rama se pueda separar sin romper a nadie el día que el equipo acepte el `RES-005` que este plan propone: el frontend ya la distingue por ese campo y no por el texto.

**`503`** — un módulo externo no respondió. Dos sabores, y los dos impiden la reserva:

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/inventory-unavailable",
  "title": "Inventario no disponible",
  "status": 503,
  "detail": "No se pudo consultar el recurso en este momento. Intenta de nuevo en unos minutos.",
  "instance": "/api/reservations",
  "module": "MODULO_1"
}
```

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/sanctions-not-verifiable",
  "title": "No se pudo comprobar tu situación",
  "status": 503,
  "detail": "No se pudo comprobar si tienes sanciones active, así que la reserva no se confirmó. Intenta de nuevo en unos minutos.",
  "instance": "/api/reservations",
  "module": "MODULO_3"
}
```

Esta es **la diferencia con UC1**: allí la caída del Módulo 3 era un aviso y la consulta seguía; aquí bloquea, porque confirmar sería dar por buena una situación que no se comprobó (UC6 FR-006). Un tercer `503` con `codigo: "INCOMPLETE_RESOURCE_DETAILS"` cubre el activo cuyo `loanTermBusinessDays` no viene en la ficha: no es culpa de la persona ni una regla de negocio, es el contrato del Módulo 1 incompleto (FR-012, P-16).

Kafka caído **no** produce error: la reserva se confirma y la ficha espera en la *outbox* (UC10 FR-005).

---

### 2. `GET /api/loans/expected-due-date`

Lo que FR-001 exige informar **antes** de confirmar. No crea nada, no reserva nada y no consume cupo.

```http
GET /api/loans/expected-due-date?resourceId=ACT-004512&date=2026-09-01&pickup=14:30
```

```json
{
  "resourceId": "ACT-004512",
  "name": "Libro de Cálculo I",
  "pickup": "2026-09-01T14:30:00-05:00",
  "dueAt": "2026-09-10T22:00:00-05:00",
  "termBusinessDays": 7,
  "skippedNonBusinessDays": ["2026-09-05", "2026-09-06"],
  "renewable": true
}
```

`skippedNonBusinessDays` es lo que vuelve auditable el cálculo de FR-014: son los días que no consumieron plazo. Con `termBusinessDays: 0` (uso en sitio, como el microscopio) el vencimiento es a las 22:00 del mismo día de la recogida y `renewable` es `false`.

> El bosquejo anterior de este plan usaba `recogida=2026-09-01T14:30`. Se parte en `date` + `pickup` para que la entrada sea idéntica a la de `POST /api/reservations` y a la de UC1, y el frontend no tenga que armar dos formatos.

Errores: `400` si la hora cae fuera de la ventana, `404` si el recurso no existe, `409` con `codigo: "NOT_AN_ASSET"` si se pregunta por un espacio, y `503` si el Módulo 1 no responde.

---

### 3. `POST /api/loans/{reservationId}/renovacion`

Sin cuerpo: el plazo no se negocia, se vuelve a aplicar el del tipo (FR-016). El titular sale del JWT.

**`200 OK`**

```json
{
  "reservationId": "4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3",
  "previousDueAt": "2026-09-10T22:00:00-05:00",
  "newDueAt": "2026-09-21T22:00:00-05:00",
  "termBusinessDays": 7,
  "renewed": true,
  "renewable": false,
  "quota": { "used": 2, "max": 3 }
}
```

`newDueAt` se cuenta **desde `previousDueAt`**, no desde hoy (FR-016), y `quota` viene igual que antes para dejar claro en la respuesta que la renovación no consumió cupo nuevo.

**`409`** — los cinco rechazos de FR-017, con `reason` en vez de `code`, porque el diccionario de errores no cubre la renovación:

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/renewal-denied",
  "title": "Renovación denegada",
  "status": 409,
  "detail": "Este préstamo ya fue renewed una vez y no admite otra prórroga.",
  "instance": "/api/loans/4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3/renewal",
  "reason": "ALREADY_RENEWED"
}
```

| `reason` | `detail` |
|---|---|
| `ALREADY_RENEWED` | `Este préstamo ya fue renovado una vez y no admite otra prórroga.` |
| `LOAN_OVERDUE` | `El préstamo venció el 2026-09-10 a las 22:00. Devuelve el activo; la mora la calcula el Módulo 3.` (agrega `dueAt`) |
| `ACTIVE_SANCTION` | `Tienes una sanción vigente hasta el 2026-09-15 y no puedes renovar.` (agrega `sanctionUntil`) |
| `OVERLAPS_ANOTHER_RESERVATION` | `Otra persona ya tiene este activo apartado en las fechas que ocuparía la renovación.` (sin decir quién, UC8 FR-005) |
| `NO_EXTENSION` | `Este activo es de uso en sitio y se devuelve el mismo día, así que no admite prórroga.` |

Los otros errores: `403` con `type` `.../errors/not-reservation-holder` cuando quien pide no es el titular, `404` si la reserva no existe, `409` con `motivo: "NOT_A_LOAN"` si el id corresponde a una reserva de espacio, y `503` si el Módulo 3 no responde —la renovación se bloquea igual que la reserva.

---

### 4. `GET /api/reservations/mias`

La lista mínima que sirve de entrada a la renovación. `?vigentes=true` (por defecto) aplica la misma definición de vigente de FR-008; `?vigentes=false` trae también las cerradas.

```json
{
  "reservations": [
    {
      "id": "4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3",
      "resourceId": "ACT-004512",
      "name": "Libro de Cálculo I",
      "category": "ACTIVO",
      "status": "CONFIRMADA",
      "start": "2026-09-01T14:30:00-05:00",
      "end": "2026-09-10T22:00:00-05:00",
      "loan": {
        "pickup": "2026-09-01T14:30:00-05:00",
        "dueAt": "2026-09-10T22:00:00-05:00",
        "termBusinessDays": 7,
        "renewed": false,
        "renewable": true,
        "pickedUp": true,
        "overdue": false
      }
    },
    {
      "id": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
      "resourceId": "ESP-0107",
      "name": "Sala de Estudio 3",
      "category": "ESPACIO",
      "status": "CONFIRMADA",
      "start": "2026-09-01T10:00:00-05:00",
      "end": "2026-09-01T12:00:00-05:00"
    }
  ],
  "quota": { "used": 2, "max": 3 },
  "catalogUnavailable": false
}
```

Orden: por `start` ascendente, y ante empate por `id`, para que la lista sea estable entre recargas igual que la de UC1 (FR-014 de UC1).

**Decisión: si el Módulo 1 no responde, esta lista sí se entrega.** El `name` del recurso no es nuestro —hay que pedirlo al Módulo 1 por cada `resourceId`, porque no guardamos el catálogo (UC1 FR-006)—, pero la reserva, su vencimiento y el cupo sí lo son. Cuando el inventario falla se responde `200` con `name` omitido en cada recurso y `catalogUnavailable: true`, y la pantalla muestra el `resourceId` con un aviso. Es distinto de UC1, donde sin catálogo no hay absolutamente nada que mostrar y por eso es `503` (UC1 FR-008): aquí negar la lista dejaría a la persona sin poder renovar un préstamo por una caída que no afecta a sus propios datos. Es una decisión de este plan; el spec no la trata.

`prestamo.vencido` es `true` cuando `ahora > vencimiento` y no hay check-out: es el caso de FR-015 y lo que la pantalla necesita para explicar por qué ese cupo no se libera.

---

### 5. Módulo 1 — ficha de un recurso

Tercera operación de `InventoryPort`, además de las dos que definió [UC1 § Contratos §2](./plan-uc1-consultar-recursos.md#2-módulo-1--inventoryport). Sigue siendo **nuestra propuesta** mientras P-16 esté abierto.

```http
GET /api/v1/resources/ACT-004512 HTTP/1.1
Authorization: Bearer <token>
```

```json
{
  "id": "ACT-004512",
  "name": "Libro de Cálculo I",
  "category": "ACTIVO",
  "type": "LIBRO",
  "operationalStatus": "DISPONIBLE",
  "location": "Biblioteca, estantería C-4",
  "assetTag": "ACT-004512",
  "condition": "BUENO",
  "loanTermBusinessDays": 7
}
```

Es el mismo objeto que el Módulo 1 pone dentro de `contenido[]` en el catálogo de UC1, servido de a uno. No se inventa una forma nueva: así un solo DTO y un solo mapper sirven para las dos operaciones.

**`loanTermBusinessDays` es el campo que FR-012 necesita y que hoy no existe.** Los valores acordados con el Módulo 1 son Libro 7, Kit de dibujo 3, Videobeam 1 y Microscopio 0. El Módulo 2 **no** guarda esa tabla: si el campo no viene en un activo, el préstamo no se puede calcular y la respuesta es `503` con `codigo: "INCOMPLETE_RESOURCE_DETAILS"`, nunca un valor por defecto nuestro. Un `0` legítimo significa uso en sitio y es distinto de que el campo falte, así que el DTO lo modela como `Integer`, no como `int`.

| Respuesta del Módulo 1 | Qué hace el adaptador | Qué ve la persona |
|---|---|---|
| `200` | Ficha normal. | Sigue el flujo. |
| `404` | El recurso no existe. | `404` |
| `5xx`, timeout, conexión rechazada | `ExternalServiceUnavailableException`. | `503` con `modulo: MODULO_1` |
| `200` sin `loanTermBusinessDays` en un activo | Se registra como contrato incompleto. | `503` con `codigo: INCOMPLETE_RESOURCE_DETAILS` |

---

### 6. Módulo 3 — sanciones al confirmar

El contrato de la petición y la respuesta es exactamente el de [UC1 § Contratos §3](./plan-uc1-consultar-recursos.md#3-módulo-3--complianceport): `GET /api/v1/compliance/people/{codigo}`, con el `code` tomado del claim del JWT (UC6 FR-007). Lo que cambia es **qué se hace con la respuesta**:

| Situación | UC1 (consultar) | UC2 (confirmar) |
|---|---|---|
| Sanción vigente | `sanctionNotice` y la lista se muestra | `409` con `RES-003`, motivo y `sanctionUntil` |
| Sin sanciones o `404` | Nada | Sigue al paso del cupo |
| Módulo 3 no responde | `sanctionNotice` con `NOT_VERIFIED`, respuesta `200` | **`503`**, la reserva no se confirma (UC6 FR-006, P-10) |

Del reporte se toma la sanción de `end` más lejano. El campo `scope` se lee si viene, pero hoy **no se usa para filtrar**: cualquier sanción vigente bloquea cualquier reserva, de espacio o de activo (P-11 abierto). Cuando P-11 se cierre, el cambio es solo esa comparación.

Cada consulta queda en el log con su duración y su resultado, y la denegación resultante en la tabla `denial` (FR-005): entre las dos cosas se cubre la auditoría de UC6 FR-009.

---

### 7. Evento hacia el Módulo 3 — `module2.reservation.record.v1`

Lo que UC2 inserta en `outbox_message.payload` y el publicador manda al topic, con la clave de partición `reservation_id` y el envoltorio del plan general. Es el `<<include>>` a UC10 (FR-018).

**Reserva estudiantil**

```json
{
  "eventId": "c3a7f1d2-4e58-4b09-9a61-7d2e8f0b5c34",
  "type": "ReservationRecordCreated",
  "version": 1,
  "occurredAt": "2026-08-30T09:14:22-05:00",
  "data": {
    "reservationId": "4b8e2a16-9c37-4d58-b1fa-6e0c74d9b2a3",
    "origin": "ESTUDIANTIL",
    "sanctionable": true,
    "resource": {
      "id": "ACT-004512",
      "name": "Libro de Cálculo I",
      "category": "ACTIVO",
      "type": "LIBRO",
      "location": "Biblioteca, estantería C-4"
    },
    "holder": {
      "userId": "5f1b9c2d-7a34-4e81-b0f6-3c8d1e9a4b72",
      "code": "2019114045",
      "name": "Camilo Cerpa",
      "role": "ESTUDIANTE"
    },
    "occupancy": {
      "start": "2026-09-01T14:30:00-05:00",
      "end": "2026-09-10T22:00:00-05:00"
    },
    "loan": {
      "pickup": "2026-09-01T14:30:00-05:00",
      "dueAt": "2026-09-10T22:00:00-05:00",
      "termBusinessDays": 7,
      "renewed": false
    }
  }
}
```

**Bloqueo académico** (lo usará UC3; el campo ya existe para no refactorizar luego, UC10 FR-009)

```json
{
  "eventId": "8d41b6c0-2f93-4a7e-8c15-9b0e3d7f2a68",
  "type": "ReservationRecordCreated",
  "version": 1,
  "occurredAt": "2026-08-30T09:14:22-05:00",
  "data": {
    "reservationId": "b7c2e418-3d65-4f09-a2b8-1e5c9d0a7f34",
    "origin": "ACADEMICO",
    "sanctionable": false,
    "resource": {
      "id": "ESP-0412",
      "name": "Laboratorio de Redes",
      "category": "ESPACIO",
      "type": "LABORATORIO",
      "location": "Bloque 7, piso 2"
    },
    "academic": {
      "course": "Redes de Computadores",
      "program": "Ingeniería de Sistemas",
      "instructor": "Nombre del instructor"
    },
    "occupancy": {
      "start": "2026-09-01T08:00:00-05:00",
      "end": "2026-09-01T10:00:00-05:00"
    }
  }
}
```

| Regla del evento | Detalle |
|---|---|
| `holder` y `academic` | Mutuamente excluyentes: uno u otro según `origin`, igual que el `CHECK` de la tabla `reservation`. |
| `sanctionable` | `false` en las académicas, que no tienen a quién sancionar (UC9 FR-006, UC11 SC-004). |
| `loan` | Solo cuando la categoría es `ACTIVO`. |
| `occupancy` | Siempre, en las dos categorías: es el dato con el que el Módulo 3 sabe cuándo constatar una ausencia o cerrar un check-out. |
| `eventId` | Es el `outbox_message.id`. Identifica el **hecho**, no el intento de envío, y es lo que permite al Módulo 3 descartar una reentrega (entrega *at-least-once*). |
| Clave de partición | `reservation_id`, para que la ficha nunca llegue después de la cancelación de la misma reserva. |

**Renovación: un tipo nuevo en el mismo topic.** Al renovar, UC10 FR-003 pide volver a informar el vencimiento. Sale otro evento al mismo topic, con `tipo: "ReservationRecordUpdated"`, el mismo bloque `data` y `loan.renewed: true`. Es un añadido compatible —el topic sigue siendo `v1` y el Módulo 3 puede ignorar el tipo que no conozca— pero **el nombre hay que confirmarlo con ellos**: el plan general solo había nombrado `ReservationRecordCreated`. Es una decisión de este plan.

El campo `payload` de `outbox_message` guarda este JSON **tal cual**, completo y con el envoltorio, para que republicar sea volver a enviar el mismo texto sin recomponer nada.

---

### 8. Tipos del frontend

Traducción literal de las secciones 1 a 4, en `frontend/src/features/reservations/types.ts`. Reusa `Categoria` y `ErrorApi` de `features/resources/types.ts` (UC1).

```ts
import type { Categoria, ErrorApi } from "../resources/tipos";

export type ReservationStatus =
  | "CONFIRMADA" | "CANCELADA" | "CANCELADA_POR_PRIORIDAD_ACADEMICA"
  | "CANCELADA_POR_RECURSO_NO_DISPONIBLE" | "FINALIZADA";

export type DenialCode = "RES-001" | "RES-002" | "RES-003" | "RES-004";

export type MotivoRenovacion =
  | "ALREADY_RENEWED" | "LOAN_OVERDUE" | "ACTIVE_SANCTION"
  | "OVERLAPS_ANOTHER_RESERVATION" | "NO_EXTENSION" | "NOT_A_LOAN";

export interface Cupo { used: number; max: number }

export interface Loan {
  pickup: string;
  dueAt: string;
  termBusinessDays: number;
  renewed: boolean;
  renewable: boolean;
  pickedUp: boolean;
  overdue?: boolean;          // solo en GET /api/reservations/mias
}

/** Cuerpo de POST /api/reservations: una forma u otra, nunca las dos. */
export type CreateReservationRequest =
  | { resourceId: string; date: string; start: string; end: string }
  | { resourceId: string; date: string; pickup: string };

export interface Reservation {
  id: string;
  resourceId: string;
  name?: string;            // se omite si el Módulo 1 no respondió
  category: Categoria;
  status: ReservationStatus;
  origin?: "ESTUDIANTIL" | "ACADEMICO";
  start: string;
  end: string;
  loan?: Loan;        // solo activos
  createdAt?: string;
  quota?: Cupo;                // viene en la respuesta de creación
}

export interface VencimientoPrevisto {
  resourceId: string;
  name: string;
  pickup: string;
  dueAt: string;
  termBusinessDays: number;
  skippedNonBusinessDays: string[];
  renewable: boolean;
}

export interface RenovacionResponse {
  reservationId: string;
  previousDueAt: string;
  newDueAt: string;
  termBusinessDays: number;
  renewed: boolean;
  renewable: boolean;
  quota: Cupo;
}

export interface MyReservationsResponse {
  reservations: Reservation[];
  quota: Cupo;
  catalogUnavailable: boolean;
}

/** Extensiones que puede traer un ProblemDetail de este caso de uso. */
export interface ErrorReserva extends ErrorApi {
  code?: DenialCode | string;
  reason?: MotivoRenovacion;
  detectedStatus?: "EN_MANTENIMIENTO";
  active?: number;
  max?: number;
  nextRelease?: string;
  sanctionReason?: string;
  sanctionUntil?: string;
  occupiedUntil?: string;
  limitMinutes?: number;
  requestedMinutes?: number;
  resourceId?: string;
  dueAt?: string;
}
```

`DenialMessage.tsx` (T036) escribe un texto por `code` y usa estos campos —el "3 de 3" sale de `active`/`max`, la fecha de la sanción de `sanctionUntil`— en vez de mostrar el `detail` crudo. El `detail` del backend sigue siendo la red de seguridad para un código que la pantalla todavía no conozca.

---

### 9. Fixtures compartidos

Se suman a los que creó el plan de UC1 en la misma carpeta:

```text
src/test/resources/contratos/
├── modulo1-ficha-activo.json            # con loanTermBusinessDays
├── modulo1-ficha-activo-sin-plazo.json  # el 503 INCOMPLETE_RESOURCE_DETAILS (P-16)
├── modulo1-ficha-espacio.json
├── api-reserva-espacio-201.json
├── api-reserva-activo-201.json
├── api-reserva-409-res-001.json         # y -002, -003, -004 y -004-mantenimiento
├── api-reserva-400-duracion.json
├── api-vencimiento-previsto.json
├── api-renovacion-200.json
├── api-renovacion-409.json
├── api-mis-reservas.json
├── evento-ficha-estudiantil.json        # lo que debe quedar en outbox_message.payload
└── evento-ficha-academica.json
```

`PublicadorOutboxIT` (T019) compara contra `evento-ficha-*.json` el mensaje que llega al topic, así que el contrato del evento se verifica de punta a punta —de la transacción al broker— y no solo en el adaptador.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Sumar a la base que dejó el plan de UC1 lo único que falta: mensajería y los parámetros nuevos del negocio.

- [ ] T001 Agregar a `build.gradle` `spring-kafka` y, en pruebas, el módulo de Kafka de Testcontainers
- [ ] T002 [P] Crear `docker-compose.yml` en la raíz con PostgreSQL y un broker de Kafka para desarrollo, y documentarlo en el README
- [ ] T003 [P] Extender `application.properties` con los parámetros de este caso de uso: `reservations.max-quota=3`, `reservations.max-space-duration=2h`, `reservations.closing-time=22:00`, `reservations.holidays` (lista de fechas) y el bloque de Kafka y de la *outbox* del plan general
- [ ] T004 [P] Extender `ReservationProperties` con esos parámetros y validarlos al arrancar (cupo > 0, duración > 0)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Esquema, mensajería y la parte de UC8 que UC2 necesita.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T005 Escribir `src/main/resources/db/migration/V3__denial_and_outbox.sql` con las tablas `denial` y `outbox_message` y el índice parcial de pendientes del plan general; verificar que la restricción `reservation_no_overlap` y el `CHECK` de titular según origen ya están en `V1`
- [ ] T006 [P] Crear `infrastructure/config/KafkaConfig.java`: productor con `acks=all` y `enable.idempotence=true`, serialización JSON y los nombres de los topics desde las propiedades
- [ ] T007 [P] Crear el puerto `BusinessCalendar` en `domain/port/out/` y su implementación en `infrastructure/config`, que excluye sábados, domingos y los festivos de las propiedades
- [ ] T008 [P] Extender `TimeSlot` con el tope de duración de FR-009, que lanza `DurationExceededException` con el tope aplicado, y mapear esa excepción a `400` con `codigo: DURATION_EXCEEDED`, `limitMinutes` y `requestedMinutes` ([Contratos §1](#1-post-apireservations))
- [ ] T009 [P] Crear `DenialCode`, `ReservationDeniedException` y `RenewalDeniedException` en `domain/model/error/`, y traducirlas a `409` con `ProblemDetail` en `GlobalErrorHandler` con las extensiones que fija [Contratos §1](#1-post-apireservations) y [§3](#3-post-apiloansreservationidrenovacion): `code` o `reason` y los campos de cada mensaje (SC-003)
- [ ] T010 Extender `InventoryPort` y sus adaptadores (real, falso) con `ficha(resourceId)` según [Contratos §5](#5-módulo-1--ficha-de-un-recurso): devuelve `ResourceDetails` con la categoría y el **plazo máximo de préstamo** de los activos como `Integer` —`0` es uso en sitio y ausente es ficha incompleta— y traduce la falta del campo a `503` con `codigo: INCOMPLETE_RESOURCE_DETAILS` (FR-012, P-16)
- [ ] T011 Completar `CheckAvailabilityUseCase` con la consulta de **un solo recurso** y el "hasta cuándo está comprometido" de UC8 FR-012, que el plan de UC1 dejó pendiente
- [ ] T012 [P] Crear `Reservation`, `ReservationStatus`, `ReservationOrigin`, `Loan` y `LoanPeriod` en `domain/model/reservation/`, con las transiciones de estado y sin nada de Spring ni JPA

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Reservar recursos con validación de reglas (Priority: P1)

**Goal**: Un Estudiante o Monitor aparta un espacio indicando fecha y franja, o un activo indicando cuándo lo recoge, y recibe el identificador de la reserva; cuando no puede, recibe un único código del diccionario de errores que dice exactamente qué regla incumplió.

**Independent Test**: Con el perfil local, reservar un recurso `DISPONIBLE` y comprobar que la reserva queda `CONFIRMADA` y el recurso aparece ocupado en la consulta de UC1; después forzar cada una de las cuatro denegaciones (bloqueo académico, cupo lleno, sanción vigente y dos peticiones simultáneas) y comprobar el código de cada una. No necesita reportería ni Kafka levantado: la ficha queda en la *outbox*.

### Tests for User Story 1

- [ ] T013 [P] [US1] Pruebas en `FranjaHorariaTest.java` para el tope de FR-009: 09:00–12:00 rechazada, 09:00–11:00 aceptada, y que el rechazo ocurre sin consultar sanción, cupo ni disponibilidad (edge case **Reserva más larga que el tope**)
- [ ] T014 [P] [US1] Pruebas en `CalculadoraVencimientoTest.java`: martes 2026-09-01 14:30 con plazo 7 → jueves 2026-09-10 22:00 (escenario 2), viernes con plazo 3 → miércoles siguiente (FR-014), plazo 0 → 22:00 del mismo día, y un festivo configurado que no consume plazo
- [ ] T015 [P] [US1] Pruebas en `ReservarRecursosUseCaseTest.java` con puertos falsos: los escenarios 1 a 5 del spec, el cruce parcial de 09:30–10:30 contra una clase de 08:00–10:00 → `RES-001`, el activo apartado para recogerlo el jueves que bloquea todo el periodo, el orden de validación con las tres causas a la vez → un solo código (edge case **Múltiples causas de denegación simultáneas**), el `503` cuando el Módulo 3 no responde (P-10) y el `503` cuando el Módulo 1 no responde
- [ ] T016 [P] [US1] Pruebas en `ReservarRecursosUseCaseTest.java` para el cupo: 3 vigentes → `RES-002` con "3 de 3" y la próxima liberación, un préstamo vencido y sin devolver que **sigue** ocupando cupo (FR-008), y una `CANCELADA` o `FINALIZADA` que lo libera
- [ ] T017 [P] [US1] Prueba de integración `ReservaPersistenceAdapterIT.java` con Testcontainers: la restricción de exclusión rechaza la ocupación solapada, el `INSERT` de la reserva y del evento en `outbox_message` ocurren en la misma transacción, un *rollback* no deja ni reserva ni evento, y la denegación sí queda registrada
- [ ] T018 [P] [US1] Prueba `ReservationConcurrencyIT.java`: N hilos piden a la vez el mismo recurso con tiempos que se cruzan y exactamente uno queda `CONFIRMADA`, el resto recibe `RES-004` (SC-004); y dos peticiones del mismo usuario con cupo 3 no se cuelan las dos
- [ ] T019 [P] [US1] Prueba `PublicadorOutboxIT.java` con Testcontainers de Kafka: el mensaje que llega al topic coincide con los *fixtures* `evento-ficha-*.json` de [Contratos §7](#7-evento-hacia-el-módulo-3--module2reservationrecordv1) —envoltorio, clave de partición `reservation_id` y las dos formas, estudiantil y académica—, publica el pendiente y lo marca `SENT`, reintenta con espera creciente y sube `intentos`, queda `FAILED` al agotar `max-attempts`, y dos instancias no publican el mismo evento (`SKIP LOCKED`)
- [ ] T020 [P] [US1] Guardar los JSON de [Contratos §1 a §7](#contratos) como *fixtures* en `src/test/resources/contratos/` y escribir con ellos `ReservaControllerTest.java` con `@WebMvcTest`: `201` con `Location` y el cuerpo de las dos formas (espacio y activo), `400` por franja de más de 2 horas y por cuerpo que no corresponde a la categoría, `401` sin sesión, `404` si el recurso no existe, `409` con el `code` y las extensiones de cada una de las cuatro denegaciones —incluida la de mantenimiento con `detectedStatus`—, los dos `503` (`MODULO_1` y `MODULO_3`), y que ninguna respuesta nombre al titular de otra reserva (UC8 FR-005)

### Implementation for User Story 1

- [ ] T021 [P] [US1] Definir `ReservationRepositoryPort` y `DenialRepositoryPort` en `domain/port/out/`, con el resultado de dominio de la confirmación (`Confirmada`, `CupoAgotado`, `RecursoTomado`)
- [ ] T022 [P] [US1] Definir `Module3NotifierPort` en `domain/port/out/` recibiendo la ficha como objeto de dominio, sin nada de Kafka
- [ ] T023 [P] [US1] Definir los puertos de entrada `ReserveResourcesPort`, `UpdateResourceStatusPort` y `ReportReservationPort` en `domain/port/in/`
- [ ] T024 [US1] Implementar `DueDateCalculator` sobre `BusinessCalendar` y el `Clock` (FR-012 a FR-014) (depende de T007, T012)
- [ ] T025 [US1] Implementar `UpdateResourceStatusUseCase` con el alcance vigente: deja escrita la ocupación y registra el cambio, sin avisar al Módulo 1 mientras P-20 esté abierto (UC7 FR-002, FR-005, FR-006)
- [ ] T026 [US1] Implementar `ReportReservationUseCase`: arma la `ReservationRecord` —con titular, o con asignatura, programa y docente si el origen es académico— y la entrega a `Module3NotifierPort` (UC10 FR-002, FR-009)
- [ ] T027 [US1] Implementar `ReserveResourcesUseCase` con los pasos 0 a 4 de la tabla de decisiones: validación de forma, ficha del recurso, sanción, cupo, disponibilidad, confirmación y los dos `<<include>>` (depende de T021 a T026)
- [ ] T028 [US1] Implementar el registro de denegaciones en el caso de uso y en `DenialPersistenceAdapter`, con su propia transacción (FR-005)
- [ ] T029 [P] [US1] Implementar `ReservationPersistenceAdapter` con la transacción de confirmación: bloqueo de la fila del titular, conteo del cupo, recomprobación de la ocupación (incluidos los préstamos abiertos sin devolver) e inserción de `reservation`, `loan` y el evento; traducir la violación de `reservation_no_overlap` a `RecursoTomado`
- [ ] T030 [P] [US1] Crear las entidades y repositorios JPA que faltan (`LoanJpa`, `DenialJpa`, `OutboxMessageJpa`) y sus mappers dominio ↔ JPA
- [ ] T031 [P] [US1] Implementar `OutboxNotifierAdapter`, que serializa `ReservationRecordEvent` según [Contratos §7](#7-evento-hacia-el-módulo-3--module2reservationrecordv1) —envoltorio completo, `holder` o `academic` según el origen, `loan` solo en activos— y lo inserta en `outbox_message` con ese mismo JSON como `payload`
- [ ] T032 [P] [US1] Implementar `OutboxPublisher` con `@Scheduled`: lee los `PENDING` con `FOR UPDATE SKIP LOCKED`, publica con la clave `reservation_id`, marca `SENT`, y en el fallo sube `intentos` con espera creciente hasta `FAILED`
- [ ] T033 [US1] Registrar los beans y las transacciones de UC2 en `UseCasesConfig` (depende de T024 a T032)
- [ ] T034 [US1] Implementar `ReservationController` (`POST /api/reservations`) exactamente como lo fija [Contratos §1](#1-post-apireservations): `CreateReservationRequest` validado contra la categoría que devuelve la ficha, `ReservationResponse` que omite lo que no aplica, `Location` en el `201`, y la documentación OpenAPI con los mismos ejemplos de la sección
- [ ] T035 [P] [US1] Frontend: copiar los tipos de [Contratos §8](#8-tipos-del-frontend) a `frontend/src/features/reservations/types.ts` y escribir en `api.ts` la llamada de creación y el mapeo del `ProblemDetail` a `ErrorReserva`
- [ ] T036 [US1] Frontend: `ConfirmReservation.tsx` (franja para espacios con el tope de 2 horas, hora de recogida y vencimiento previsto para activos tomado de [Contratos §2](#2-get-apiloansexpected-due-date)) y `DenialMessage.tsx` con un texto por código armado desde los campos del error —`active`/`max` para el "3 de 3", `sanctionUntil` para la sanción, `detectedStatus` para el mantenimiento— y el `detail` como respaldo
- [ ] T037 [US1] Frontend: enganchar el botón de reservar en `app/(app)/resources/page.tsx`, habilitado solo para los recursos seleccionables que ya marca UC1, y refrescar la lista tras confirmar

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: Renovación del préstamo (FR-016, FR-017)

**Purpose**: Completar el resto de la funcionalidad del spec. No es una historia de usuario aparte —el spec tiene una sola— pero se entrega y se prueba de forma independiente, y puede quedar después de la demo del MVP sin romper nada de la Phase 3.

- [ ] T038 [P] Pruebas en `RenovarPrestamoUseCaseTest.java`: renovación válida que suma el plazo desde el vencimiento vigente y no desde hoy, la pedida el mismo día del vencimiento antes de las 22:00 que se acepta y un minuto después que no (edge case **Renovación pedida el mismo día del vencimiento**), y los cinco rechazos de FR-017
- [ ] T039 [P] Prueba de integración de la renovación: el `UPDATE` de `reservation.ends_at` que invade la reserva de otra persona lo rechaza la restricción de exclusión, y la renovación **no** consume cupo nuevo (FR-016)
- [ ] T040 Definir `RenewLoanPort` e implementar `RenewLoanUseCase`: verifica titularidad, aplica los cinco rechazos con el `reason` de [Contratos §3](#3-post-apiloansreservationidrenovacion), mueve `reservation.ends_at`, marca `loan.renewed` y publica la ficha con el vencimiento nuevo como `ReservationRecordUpdated` (UC10 FR-003) (depende de T024, T029)
- [ ] T041 Implementar `LoanController` (`POST /api/loans/{reservationId}/renovacion` y `GET /api/loans/expected-due-date`) y `GET /api/reservations/mias` en `ReservationController`, según [Contratos §2, §3 y §4](#2-get-apiloansexpected-due-date), incluido el `catalogUnavailable` cuando el Módulo 1 no responda
- [ ] T042 Frontend: `MyReservationsList.tsx` y `RenewLoan.tsx` en una pantalla mínima `app/(app)/my-reservations/page.tsx`, que muestra el vencimiento, si queda renovación y el motivo cuando se deniega

**Checkpoint**: El spec de UC2 queda cubierto de punta a punta

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Mejoras que afectan a toda la funcionalidad

- [ ] T043 [P] Verificar SC-003 con una prueba que recorra las cuatro denegaciones y compruebe que ninguna respuesta sale sin `code` ni sin mensaje
- [ ] T044 [P] Registrar en logs cada confirmación, cada denegación y cada consulta de sanciones con su duración y su resultado, sin datos personales más allá del identificador del usuario (FR-005, FR-007, UC6 FR-009)
- [ ] T045 [P] Medir la confirmación bajo concurrencia contra un Módulo 1 y un Módulo 3 simulados con su latencia prometida, y comprobar que no aparecen ocupaciones solapadas (SC-004)
- [ ] T046 [P] Actualizar el README con `docker-compose up` para PostgreSQL y Kafka, y con cómo ver la *outbox* cuando el broker está caído
- [ ] T047 Enviar al Módulo 1 la ficha de [Contratos §5](#5-módulo-1--ficha-de-un-recurso) (P-16, con el plazo de préstamo) y al Módulo 3 el evento de [§7](#7-evento-hacia-el-módulo-3--module2reservationrecordv1) con el tipo `ReservationRecordUpdated` para confirmarlo, y llevar a `pendientes-clarificacion.md` los NEEDS CLARIFICATION que este plan deja abiertos

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: depende de que el plan de UC1 esté hecho; sin su Phase 2 no hay esquema, seguridad ni reloj
- **Foundational (Phase 2)**: depende de Setup - BLOCKS la user story
- **User Story 1 (Phase 3)**: depende de Foundational
- **Renovación (Phase 4)**: depende de la Phase 3, porque reusa el préstamo, la calculadora y la *outbox*
- **Polish (Phase 5)**: depende de que las Phases 3 y 4 estén completas

### Dependencias con otros casos de uso

- **UC1 `Consultar recursos`**: es el plan previo. Aporta el esquema, `TimeSlot`, `Occupancy`, la seguridad, el `Clock`, los clientes de los Módulos 1 y 3 y la pantalla desde la que se reserva.
- **UC8 `Consultar disponibilidad`**: este plan cierra lo que faltaba (un solo recurso y el "hasta cuándo", UC8 FR-012). Con esto UC8 queda completo.
- **UC6 `Consultar sanciones`**: este plan añade la denegación `RES-003` y el registro con el que se audita por qué se denegó una reserva (UC6 FR-009), que sale de la tabla `denial` (T028) y del log de cada consulta al Módulo 3 (T044). Con esto UC6 queda completo.
- **UC7 `Actualizar estado de los recursos`**: se implementa solo el alcance vigente (dejar escrita la ocupación). Su propio plan añadirá el aviso al Módulo 1 y la tabla `StatusChange` cuando se responda P-20.
- **UC10 `Reportar información de la reserva`**: este plan monta la *outbox*, el publicador y el evento de la ficha. Su plan añadirá la ficha de los bloqueos académicos que llegan por UC3 y el contrato definitivo del evento.
- **UC3 `Importar horarios semestrales`**: reusa `ReserveResourcesUseCase` con `origen = ACADEMICO`. El campo ya queda listo; lo que se salta depende de P-19.
- **UC4 `Cancelar reserva`** y **UC9 `Recibir reporte de no asistencia`**: cuelgan de la reserva creada aquí. UC2 no los necesita; mientras no existan, una reserva solo se libera al pasar su franja o con el check-out del préstamo. La pantalla `my-reservations` que se crea aquí es donde UC4 pondrá su botón.
- **UC12 `Recibir check-out`**: es lo único que cierra un préstamo y libera su cupo (FR-008, FR-015). Hasta que exista, en local se cierra con datos de prueba.

### Within User Story 1

- Modelo y puertos (T012, T021 a T023) → calculadora (T024) → casos de uso de los `<<include>>` (T025, T026) → `ReserveResourcesUseCase` (T027, T028)
- Los adaptadores (T029 a T032) solo dependen de los puertos, así que van en paralelo con los casos de uso
- Beans (T033) → controlador (T034) → frontend (T035 a T037)

### Parallel Opportunities

- En Setup: T002, T003 y T004
- En Foundational: T006 a T009 y T012
- En User Story 1: todas las pruebas (T013 a T020), los puertos (T021 a T023) y los adaptadores (T029 a T032)
- En Phase 4: T038 y T039
- En Polish: T043 a T046

## Notes

- La numeración `T0XX` es propia de este plan y empieza de nuevo; no continúa la del plan de UC1
- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Verify tests pass
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- La sección **Contratos** es la única fuente del JSON de UC2: si algo cambia ahí, cambia en los *fixtures*, en el OpenAPI y en los tipos del frontend, no al revés. Las convenciones comunes no se repiten: viven en el plan de UC1.
- **Decisiones de los contratos que el spec no fija**: el cuerpo de `POST /api/reservations` no lleva `category` ni `origin` (los pone el Módulo 1 y el JWT); `GET /api/reservations/mias` responde `200` con `catalogUnavailable: true` si el Módulo 1 no contesta, en vez de `503`, porque la reserva y el cupo son datos nuestros; la renovación publica `ReservationRecordUpdated` en el mismo topic; y `detectedStatus: "EN_MANTENIMIENTO"` acompaña al `RES-004` provisional para poder migrarlo a `RES-005` sin romper al frontend.
- **NEEDS CLARIFICATION abiertos en este plan**:
  - **P-10**: si el Módulo 3 no responde, aquí se bloquea la reserva con `503`, que es la opción conservadora del spec
  - **P-11**: el alcance de la sanción; hoy cualquier sanción vigente bloquea cualquier reserva
  - **P-19**: qué reglas se salta una reserva de origen académico; el supuesto es sanción, cupo y tope de 2 horas
  - **P-16**: el plazo máximo de préstamo tiene que venir en la ficha del Módulo 1; sin él, FR-012 no se puede implementar
  - **P-20 punto 1**: mientras no se responda, `Actualizar estado de los recursos` no le envía nada al Módulo 1
  - **P-08**: un préstamo vencido y no devuelto sigue ocupando el activo sin fecha de fin; falta cuándo se da por perdido
  - **Código para `EN_MANTENIMIENTO`**: el diccionario de errores no tiene uno; se responde `RES-004` con un mensaje propio y se propone un `RES-005`
  - **Calendario de festivos**: FR-014 cuenta días hábiles y nadie nos da el calendario de la universidad; por ahora es una lista en `application.properties`
  - **`ReservationRecordUpdated`**: el nombre del evento de la renovación es propuesta de este plan; el plan general solo había nombrado `ReservationRecordCreated` y hay que confirmarlo con el Módulo 3
