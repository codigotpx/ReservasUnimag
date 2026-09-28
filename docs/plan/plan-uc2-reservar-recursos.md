# Implementation Plan: Reservar recursos (UC2)

**Date**: 2026-09-28
**Spec**: [spec-modulo2-uc2-reservar-recursos.md](../specs/spec-modulo2-uc2-reservar-recursos.md)
**Plan general**: [plan-arquitectura.md](./plan-arquitectura.md)
**Plan previo**: [plan-uc1-consultar-recursos.md](./plan-uc1-consultar-recursos.md) — deja montada la base compartida (esquema, seguridad, reloj, clientes HTTP, frontend) que este plan da por hecha

## Summary

UC2 es para lo que existe el módulo: que el propio estudiante aparte un recurso sin pedírselo a nadie. Se llega desde la lista de UC1 y, antes de confirmar, el sistema revisa las reglas que deciden si puede apartarlo. Cuando la respuesta es no, nunca se dice "no se pudo": cada negativa sale con un código del diccionario de errores (`RES-001` a `RES-004`).

**Enfoque técnico:**

1. `ReservarRecursosUseCase` valida primero la **forma** de la solicitud: franja dentro de 06:00–22:00, del mismo día y de máximo 2 horas para un espacio (FR-009). Una franja mal formada es un `400`, no una denegación con código.
2. Pide la ficha del recurso al Módulo 1 por `InventarioPort` (categoría, y el **plazo máximo de préstamo** si es un activo, FR-012). Si el Módulo 1 no responde, no se confirma nada a ciegas (UC8 FR-007).
3. Aplica las reglas **en el orden de FR-002**: sanción (`RES-003`), cupo de 3 vigentes (`RES-002`) y conflicto académico u ocupación (`RES-001` / `RES-004`). Devuelve un único código, el primero que falla (edge case **Múltiples causas de denegación simultáneas**).
4. Para un **activo**, la persona solo elige la hora de recogida: el vencimiento lo calcula el sistema sumando el plazo en **días hábiles** y fijándolo a las **22:00** de ese día (FR-013, FR-014), y se lo informa antes de confirmar.
5. Escribe la reserva en una sola transacción, donde la **restricción de exclusión** de PostgreSQL es la que garantiza que dos ocupaciones solapadas no puedan coexistir aunque dos personas den clic a la vez (FR-004). Si la restricción salta, la respuesta es `RES-004`.
6. En esa misma transacción inserta el evento de la ficha en la *outbox* (`<<include>>` a UC10, FR-018) y deja la ocupación escrita, que es lo que hoy significa `Actualizar estado de los recursos` (`<<include>>` a UC7, FR-011).
7. Toda denegación queda registrada con su código, usuario, recurso y marca de tiempo (FR-005), en su propia transacción para que sobreviva al *rollback*.

Además de la reserva, este plan implementa la **renovación única** del préstamo (FR-016, FR-017) y la parte de UC8 que UC2 necesita —la consulta de un solo recurso, con el "hasta cuándo" de UC8 FR-012—, que el plan de UC1 dejó pendiente.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript con Next.js y React (frontend)
**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Validation, RestClient), **Spring for Apache Kafka** (`spring-kafka`, nuevo en este plan), Flyway, springdoc-openapi
**Storage**: PostgreSQL con `btree_gist`. UC2 **escribe** `reserva`, `prestamo`, `denegacion` y `mensaje_saliente`, y lee `bloqueo_academico` y `usuario`.
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
│   │   ├── reserva/
│   │   │   ├── Reserva.java                        # nueva: titular, recurso, ocupación, estado, origen
│   │   │   ├── EstadoReserva.java                  # CONFIRMADA, CANCELADA, CANCELADA_POR_*, FINALIZADA
│   │   │   ├── OrigenReserva.java                  # ESTUDIANTIL, ACADEMICO
│   │   │   ├── Prestamo.java                       # plazo aplicado, renovado, entrega y devolución
│   │   │   ├── PeriodoDePrestamo.java              # recogida → vencimiento (puede durar días)
│   │   │   ├── FranjaHoraria.java                  # (existe) + tope de 2 horas de FR-009
│   │   │   └── Ocupacion.java                      # (existe)
│   │   ├── recurso/
│   │   │   └── FichaRecurso.java                   # ficha de un recurso con su plazo de préstamo
│   │   └── error/
│   │       ├── CodigoDenegacion.java               # RES-001 a RES-004
│   │       ├── ReservaDenegadaException.java       # código + datos del mensaje
│   │       ├── DuracionExcedidaException.java      # FR-009: franja mal formada, no denegación
│   │       └── RenovacionDenegadaException.java    # motivo de FR-017
│   ├── port/
│   │   ├── in/
│   │   │   ├── ReservarRecursosPort.java
│   │   │   ├── RenovarPrestamoPort.java
│   │   │   ├── ActualizarEstadoRecursosPort.java   # UC7 (la parte vigente mientras P-20 esté abierto)
│   │   │   └── ReportarInformacionReservaPort.java # UC10
│   │   └── out/
│   │       ├── ReservaRepositoryPort.java          # confirmar, vigentes, buscar préstamo, renovar
│   │       ├── DenegacionRepositoryPort.java       # FR-005
│   │       ├── NotificadorModulo3Port.java         # outbox: ficha y (más adelante) cancelación
│   │       ├── CalendarioHabil.java                # días hábiles de FR-014
│   │       └── InventarioPort.java                 # (existe) + ficha(recursoId) con el plazo
│   └── usecase/
│       ├── reservarrecursos/
│       │   ├── ReservarRecursosUseCase.java
│       │   ├── SolicitudDeReserva.java             # comando: recurso, franja o recogida, origen
│       │   ├── ResultadoReserva.java
│       │   └── CalculadoraVencimiento.java         # plazo hábil + 22:00 (FR-013, FR-014)
│       ├── renovarprestamo/
│       │   └── RenovarPrestamoUseCase.java
│       ├── actualizarestado/
│       │   └── ActualizarEstadoRecursosUseCase.java
│       ├── reportarreserva/
│       │   └── ReportarInformacionReservaUseCase.java
│       └── consultardisponibilidad/
│           └── ConsultarDisponibilidadUseCase.java # (existe) + un recurso y "hasta cuándo" (UC8 FR-012)
│
└── infrastructure/
    ├── adapter/
    │   ├── in/web/
    │   │   ├── reserva/
    │   │   │   ├── ReservaController.java          # POST /api/reservas, GET /api/reservas/mias
    │   │   │   ├── PrestamoController.java         # POST .../renovacion, GET vencimiento previsto
    │   │   │   ├── CrearReservaRequest.java
    │   │   │   ├── ReservaResponse.java
    │   │   │   └── MisReservasResponse.java
    │   │   └── error/
    │   │       └── ManejadorGlobalErrores.java     # (existe) + 409 con el código de denegación
    │   └── out/
    │       ├── persistence/
    │       │   ├── entity/                         # + PrestamoJpa, DenegacionJpa, MensajeSalienteJpa
    │       │   ├── repository/                     # + ReservaJpaRepository, DenegacionJpaRepository,
    │       │   │                                   #   MensajeSalienteJpaRepository
    │       │   ├── ReservaPersistenceAdapter.java  # la transacción de confirmación
    │       │   └── DenegacionPersistenceAdapter.java
    │       └── mensajeria/
    │           ├── OutboxNotificadorAdapter.java   # → NotificadorModulo3Port
    │           ├── PublicadorOutbox.java           # @Scheduled: mensaje_saliente → Kafka
    │           └── evento/EventoFichaDeReserva.java
    └── config/
        ├── PropiedadesReservas.java                # (existe) + cupo, tope de franja, festivos
        ├── KafkaConfig.java                        # productor idempotente, acks=all
        └── CasosDeUsoConfig.java                   # (existe) + los beans y transacciones de UC2

src/main/resources/
├── application.properties                          # + cupo, tope, festivos y bloque de Kafka
└── db/migration/
    └── V3__denegacion_y_outbox.sql                 # tablas denegacion y mensaje_saliente

src/test/java/edu/unimagdalena/reservasunimag/
├── domain/model/reserva/FranjaHorariaTest.java     # (existe) + tope de 2 horas
├── domain/usecase/reservarrecursos/
│   ├── ReservarRecursosUseCaseTest.java
│   └── CalculadoraVencimientoTest.java
├── domain/usecase/renovarprestamo/RenovarPrestamoUseCaseTest.java
└── infrastructure/adapter/
    ├── in/web/reserva/ReservaControllerTest.java
    ├── out/persistence/ReservaPersistenceAdapterIT.java
    ├── out/persistence/ReservaConcurrenciaIT.java
    └── out/mensajeria/PublicadorOutboxIT.java

frontend/src/
├── app/(app)/recursos/page.tsx                     # (existe) + botón reservar sobre los seleccionables
├── app/(app)/mis-reservas/page.tsx                 # lista mínima: entrada a la renovación
└── features/reservas/
    ├── ConfirmarReserva.tsx                        # franja o recogida + vencimiento previsto
    ├── MensajeDenegacion.tsx                       # traduce RES-001 a RES-004
    ├── ListaMisReservas.tsx
    ├── RenovarPrestamo.tsx
    ├── api.ts
    └── tipos.ts
```

**Structure Decision**: se mantiene la estructura del plan general y del plan de UC1. Dos añadidos: la carpeta `infrastructure/adapter/out/mensajeria`, que aparece aquí por primera vez porque UC10 es el primer `<<include>>` que publica a Kafka, y la pantalla `(app)/mis-reservas`, que se crea mínima —solo listar y renovar— porque la renovación de FR-016 necesita una entrada en la interfaz; `Cancelar reserva` (UC4) la completa con su propio plan.

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
FROM reserva r
LEFT JOIN prestamo p ON p.reserva_id = r.id
WHERE r.usuario_id = :usuarioId
  AND r.estado = 'CONFIRMADA'
  AND ( (r.categoria_recurso = 'ESPACIO' AND r.fin > :ahora)
     OR (r.categoria_recurso = 'ACTIVO'  AND p.devuelto_en IS NULL) );
```

Para el mensaje de `RES-002` se toma el `fin` más próximo entre las vigentes; si la más próxima es un préstamo ya vencido y sin devolver, el mensaje dice que ese cupo se libera cuando se registre la devolución, no una fecha.

**Cómo se garantiza que no haya cruces (FR-004, SC-004).** La `EXCLUDE USING gist` sobre `(recurso_id, ocupacion)` con `WHERE (estado = 'CONFIRMADA')` es la única garantía real: dos peticiones simultáneas pasan las dos la comprobación previa y solo una sobrevive al `INSERT`. El adaptador de persistencia traduce esa violación a `RES-004`, y esto vale igual para dos franjas de un espacio, dos periodos de préstamo de un activo y la mezcla con un bloqueo académico. La comprobación previa de UC8 no se quita: sirve para dar un código exacto (`RES-001` frente a `RES-004`) en el caso normal.

**Dónde empieza y acaba la transacción.** Las llamadas HTTP a los Módulos 1 y 3 se hacen **fuera** de la transacción de escritura, para no tener una transacción abierta esperando a otro sistema. La transacción hace solo lo que debe ser atómico, y es un método del adaptador de persistencia:

1. `SELECT ... FOR UPDATE` de la fila del titular en `usuario`, que serializa las reservas de una misma persona y evita que dos peticiones suyas se cuelen las dos en el cupo.
2. Contar el cupo vigente (la comprobación que decide; la del paso 2 de la tabla es la que da el mensaje temprano).
3. Volver a comprobar la ocupación en nuestra base, que cuesta milisegundos.
4. `INSERT` de `reserva`, del `prestamo` si es un activo, y del evento en `mensaje_saliente`.

El puerto `ReservaRepositoryPort.confirmar(...)` devuelve un resultado de dominio (`Confirmada`, `CupoAgotado`, `RecursoTomado`) y el caso de uso lo traduce al código. Así la regla sigue leyéndose en el dominio y la atomicidad vive donde puede garantizarse. Los casos de uso siguen sin `@Service` ni `@Transactional`: eso se configura en `infrastructure/config` (plan general).

**Préstamo vencido y no devuelto (FR-015).** La ocupación de un activo es `[recogida, vencimiento)`, así que la restricción de exclusión deja de proteger justo cuando el plazo vence, y el activo sigue en manos de alguien. Por eso la comprobación de disponibilidad no se limita al rango: un préstamo con `entregado_en` y sin `devuelto_en` ocupa desde su inicio y sin fin, igual que ya hace la consulta de UC1. Dentro de la transacción esos préstamos abiertos del recurso se leen con `FOR UPDATE`, para que un *check-out* concurrente no se cruce con una reserva nueva. Queda una ventana estrecha —dos peticiones nuevas sobre un activo vencido y no devuelto— que se cierra con esa misma comprobación; se documenta porque depende de P-08, que sigue abierto.

**Cálculo del vencimiento (FR-012, FR-013, FR-014).** `CalculadoraVencimiento` suma el plazo en días hábiles a partir del **día siguiente** a la recogida y fija la hora a las 22:00 de `America/Bogota`. Con el ejemplo del spec: recogida el martes 2026-09-01 a las 14:30 con plazo 7 → se saltan el sábado 5 y el domingo 6 → **jueves 2026-09-10 a las 22:00**. Un plazo `0` vence a las 22:00 del mismo día de la recogida (uso en sitio). Los días no hábiles salen del puerto `CalendarioHabil`, cuya primera implementación excluye sábados y domingos más una lista de festivos en `application.properties`. (NEEDS CLARIFICATION: FR-014 habla de "días en que la universidad no abre" y nadie nos da ese calendario; mientras no exista, la lista se mantiene a mano.)

**Renovación (FR-016, FR-017).** Es el mismo préstamo: no consume cupo nuevo, `prestamo.renovado` pasa a `true` y `reserva.fin` se mueve al nuevo vencimiento, que se calcula **desde el vencimiento vigente** y no desde hoy. Los cinco rechazos de FR-017 se devuelven como `409` con un motivo enumerado, porque el diccionario de errores de UC2 no cubre la renovación:

| Motivo | Cuándo |
|---|---|
| `YA_RENOVADO` | `prestamo.renovado` ya es `true` |
| `PRESTAMO_VENCIDO` | `ahora > reserva.fin`; el corte son las 22:00 del día del vencimiento (edge case **Renovación pedida el mismo día del vencimiento**) |
| `SANCION_ACTIVA` | el Módulo 3 reporta sanción vigente; si no responde, `503` |
| `INVADE_OTRA_RESERVA` | el nuevo rango choca con otra ocupación; lo detecta la restricción de exclusión al actualizar `reserva.fin` |
| `SIN_PRORROGA` | el activo es de plazo `0` |

Al renovar sale una ficha nueva hacia el Módulo 3 con el vencimiento actualizado (UC10 FR-003).

**`Actualizar estado de los recursos` dentro de UC2 (FR-011).** Mientras P-20 siga abierto, el Módulo 2 **no le envía nada** al Módulo 1: el estado que este caso de uso "actualiza" es la ocupación de nuestra propia base, que es justamente la fila de `reserva` que se acaba de insertar. Se llama igual al puerto `ActualizarEstadoRecursosPort` para que el `<<include>>` exista en el código y quede el registro de auditoría; la tabla `CambioDeEstado` y el aviso al Módulo 1 se diseñan cuando se responda P-20.

**`Reportar información de la reserva` dentro de UC2 (FR-018).** El evento `FichaDeReservaCreada` se inserta en `mensaje_saliente` en la misma transacción que la reserva, y un publicador `@Scheduled` lo lleva al topic `modulo2.reserva.ficha.v1` con la clave de partición `reserva_id`, según el plan general. Si Kafka o el Módulo 3 están caídos, la reserva se confirma igual y el evento sale cuando vuelvan (UC10 FR-005).

**Registro de denegaciones (FR-005).** Se escribe en `denegacion` en una transacción aparte (`REQUIRES_NEW`), porque una denegación por `RES-004` ocurre cuando la transacción de la reserva ya está condenada por la violación de la restricción. Las denegaciones alimentan la reportería y no llevan datos del titular más allá de su `usuario_id`.

**Reservas de origen académico.** `SolicitudDeReserva` lleva el `origen` desde ya, para que UC3 pueda reusar este caso de uso sin refactor. Con `origen = ACADEMICO` se saltan la sanción, el cupo y el tope de 2 horas, y no hay titular. (NEEDS CLARIFICATION: P-19 pregunta exactamente esto; lo de aquí es el supuesto de partida.)

**Contrato HTTP.**

```text
POST /api/reservas
  espacio: { recursoId, fecha, inicio, fin }
  activo:  { recursoId, fecha, recogida }
→ 201 { id, recursoId, categoria, inicio, fin, estado, vencimiento?, renovable? }
→ 400 ProblemDetail  franja fuera de la ventana, que cruza la medianoche, inicio ≥ fin o más de 2 horas
→ 401                sin sesión
→ 404 ProblemDetail  el recurso no existe en el Módulo 1
→ 409 ProblemDetail  { codigo: "RES-001".."RES-004", detalle, sancionHasta?, vigentes?, proximaLiberacion? }
→ 503 ProblemDetail  el Módulo 1 o el Módulo 3 no respondieron

GET  /api/prestamos/vencimiento-previsto?recursoId=...&recogida=2026-09-01T14:30
→ 200 { vencimiento: "2026-09-10T22:00", plazoDiasHabiles: 7 }     # FR-001: informarlo antes de confirmar

POST /api/prestamos/{reservaId}/renovacion
→ 200 { reservaId, vencimientoAnterior, vencimientoNuevo }
→ 403                no es el titular
→ 409 ProblemDetail  { motivo: "YA_RENOVADO" | "PRESTAMO_VENCIDO" | "SANCION_ACTIVA" |
                                "INVADE_OTRA_RESERVA" | "SIN_PRORROGA" }

GET  /api/reservas/mias?vigentes=true
→ 200 { reservas: [{ id, recursoId, nombre, categoria, inicio, fin, estado, renovable }], cupo: { usado, maximo } }
```

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Sumar a la base que dejó el plan de UC1 lo único que falta: mensajería y los parámetros nuevos del negocio.

- [ ] T001 Agregar a `build.gradle` `spring-kafka` y, en pruebas, el módulo de Kafka de Testcontainers
- [ ] T002 [P] Crear `docker-compose.yml` en la raíz con PostgreSQL y un broker de Kafka para desarrollo, y documentarlo en el README
- [ ] T003 [P] Extender `application.properties` con los parámetros de este caso de uso: `reservas.cupo-maximo=3`, `reservas.duracion-maxima-espacio=2h`, `reservas.hora-cierre=22:00`, `reservas.festivos` (lista de fechas) y el bloque de Kafka y de la *outbox* del plan general
- [ ] T004 [P] Extender `PropiedadesReservas` con esos parámetros y validarlos al arrancar (cupo > 0, duración > 0)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Esquema, mensajería y la parte de UC8 que UC2 necesita.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T005 Escribir `src/main/resources/db/migration/V3__denegacion_y_outbox.sql` con las tablas `denegacion` y `mensaje_saliente` y el índice parcial de pendientes del plan general; verificar que la restricción `reserva_sin_cruces` y el `CHECK` de titular según origen ya están en `V1`
- [ ] T006 [P] Crear `infrastructure/config/KafkaConfig.java`: productor con `acks=all` y `enable.idempotence=true`, serialización JSON y los nombres de los topics desde las propiedades
- [ ] T007 [P] Crear el puerto `CalendarioHabil` en `domain/port/out/` y su implementación en `infrastructure/config`, que excluye sábados, domingos y los festivos de las propiedades
- [ ] T008 [P] Extender `FranjaHoraria` con el tope de duración de FR-009, que lanza `DuracionExcedidaException` con el tope aplicado, y mapear esa excepción a `400` en `ManejadorGlobalErrores`
- [ ] T009 [P] Crear `CodigoDenegacion`, `ReservaDenegadaException` y `RenovacionDenegadaException` en `domain/model/error/`, y traducirlas a `409` con `ProblemDetail` en `ManejadorGlobalErrores`, incluyendo el campo `codigo` (SC-003)
- [ ] T010 Extender `InventarioPort` y sus adaptadores (real, falso) con `ficha(recursoId)`, que devuelve `FichaRecurso` con la categoría y el **plazo máximo de préstamo** de los activos (FR-012, P-16)
- [ ] T011 Completar `ConsultarDisponibilidadUseCase` con la consulta de **un solo recurso** y el "hasta cuándo está comprometido" de UC8 FR-012, que el plan de UC1 dejó pendiente
- [ ] T012 [P] Crear `Reserva`, `EstadoReserva`, `OrigenReserva`, `Prestamo` y `PeriodoDePrestamo` en `domain/model/reserva/`, con las transiciones de estado y sin nada de Spring ni JPA

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
- [ ] T017 [P] [US1] Prueba de integración `ReservaPersistenceAdapterIT.java` con Testcontainers: la restricción de exclusión rechaza la ocupación solapada, el `INSERT` de la reserva y del evento en `mensaje_saliente` ocurren en la misma transacción, un *rollback* no deja ni reserva ni evento, y la denegación sí queda registrada
- [ ] T018 [P] [US1] Prueba `ReservaConcurrenciaIT.java`: N hilos piden a la vez el mismo recurso con tiempos que se cruzan y exactamente uno queda `CONFIRMADA`, el resto recibe `RES-004` (SC-004); y dos peticiones del mismo usuario con cupo 3 no se cuelan las dos
- [ ] T019 [P] [US1] Prueba `PublicadorOutboxIT.java` con Testcontainers de Kafka: publica el pendiente y lo marca `ENVIADO`, reintenta con espera creciente y sube `intentos`, queda `FALLIDO` al agotar `max-intentos`, y dos instancias no publican el mismo evento (`SKIP LOCKED`)
- [ ] T020 [P] [US1] Prueba `ReservaControllerTest.java` con `@WebMvcTest`: `201` con el cuerpo de la reserva, `400` por franja de más de 2 horas, `401` sin sesión, `404` si el recurso no existe, `409` con el campo `codigo` en cada una de las cuatro denegaciones y `503` con el Módulo 3 caído

### Implementation for User Story 1

- [ ] T021 [P] [US1] Definir `ReservaRepositoryPort` y `DenegacionRepositoryPort` en `domain/port/out/`, con el resultado de dominio de la confirmación (`Confirmada`, `CupoAgotado`, `RecursoTomado`)
- [ ] T022 [P] [US1] Definir `NotificadorModulo3Port` en `domain/port/out/` recibiendo la ficha como objeto de dominio, sin nada de Kafka
- [ ] T023 [P] [US1] Definir los puertos de entrada `ReservarRecursosPort`, `ActualizarEstadoRecursosPort` y `ReportarInformacionReservaPort` en `domain/port/in/`
- [ ] T024 [US1] Implementar `CalculadoraVencimiento` sobre `CalendarioHabil` y el `Reloj` (FR-012 a FR-014) (depende de T007, T012)
- [ ] T025 [US1] Implementar `ActualizarEstadoRecursosUseCase` con el alcance vigente: deja escrita la ocupación y registra el cambio, sin avisar al Módulo 1 mientras P-20 esté abierto (UC7 FR-002, FR-005, FR-006)
- [ ] T026 [US1] Implementar `ReportarInformacionReservaUseCase`: arma la `FichaDeReserva` —con titular, o con asignatura, programa y docente si el origen es académico— y la entrega a `NotificadorModulo3Port` (UC10 FR-002, FR-009)
- [ ] T027 [US1] Implementar `ReservarRecursosUseCase` con los pasos 0 a 4 de la tabla de decisiones: validación de forma, ficha del recurso, sanción, cupo, disponibilidad, confirmación y los dos `<<include>>` (depende de T021 a T026)
- [ ] T028 [US1] Implementar el registro de denegaciones en el caso de uso y en `DenegacionPersistenceAdapter`, con su propia transacción (FR-005)
- [ ] T029 [P] [US1] Implementar `ReservaPersistenceAdapter` con la transacción de confirmación: bloqueo de la fila del titular, conteo del cupo, recomprobación de la ocupación (incluidos los préstamos abiertos sin devolver) e inserción de `reserva`, `prestamo` y el evento; traducir la violación de `reserva_sin_cruces` a `RecursoTomado`
- [ ] T030 [P] [US1] Crear las entidades y repositorios JPA que faltan (`PrestamoJpa`, `DenegacionJpa`, `MensajeSalienteJpa`) y sus mappers dominio ↔ JPA
- [ ] T031 [P] [US1] Implementar `OutboxNotificadorAdapter`, que serializa `EventoFichaDeReserva` con el envoltorio `eventoId`, `tipo`, `version`, `ocurridoEn` y `datos`, y lo inserta en `mensaje_saliente`
- [ ] T032 [P] [US1] Implementar `PublicadorOutbox` con `@Scheduled`: lee los `PENDIENTE` con `FOR UPDATE SKIP LOCKED`, publica con la clave `reserva_id`, marca `ENVIADO`, y en el fallo sube `intentos` con espera creciente hasta `FALLIDO`
- [ ] T033 [US1] Registrar los beans y las transacciones de UC2 en `CasosDeUsoConfig` (depende de T024 a T032)
- [ ] T034 [US1] Implementar `ReservaController` (`POST /api/reservas`) con `CrearReservaRequest` validado según la categoría del recurso y `ReservaResponse`, documentado con OpenAPI
- [ ] T035 [P] [US1] Frontend: `frontend/src/features/reservas/tipos.ts` y `api.ts` con la llamada de creación y el mapeo de los `ProblemDetail` con `codigo`
- [ ] T036 [US1] Frontend: `ConfirmarReserva.tsx` (franja para espacios con el tope de 2 horas, hora de recogida y vencimiento previsto para activos) y `MensajeDenegacion.tsx` con un texto por código, incluida la fecha de fin de la sanción y el "3 de 3" del cupo
- [ ] T037 [US1] Frontend: enganchar el botón de reservar en `app/(app)/recursos/page.tsx`, habilitado solo para los recursos seleccionables que ya marca UC1, y refrescar la lista tras confirmar

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: Renovación del préstamo (FR-016, FR-017)

**Purpose**: Completar el resto de la funcionalidad del spec. No es una historia de usuario aparte —el spec tiene una sola— pero se entrega y se prueba de forma independiente, y puede quedar después de la demo del MVP sin romper nada de la Phase 3.

- [ ] T038 [P] Pruebas en `RenovarPrestamoUseCaseTest.java`: renovación válida que suma el plazo desde el vencimiento vigente y no desde hoy, la pedida el mismo día del vencimiento antes de las 22:00 que se acepta y un minuto después que no (edge case **Renovación pedida el mismo día del vencimiento**), y los cinco rechazos de FR-017
- [ ] T039 [P] Prueba de integración de la renovación: el `UPDATE` de `reserva.fin` que invade la reserva de otra persona lo rechaza la restricción de exclusión, y la renovación **no** consume cupo nuevo (FR-016)
- [ ] T040 Definir `RenovarPrestamoPort` e implementar `RenovarPrestamoUseCase`: verifica titularidad, aplica los cinco rechazos, mueve `reserva.fin`, marca `prestamo.renovado` y publica la ficha con el vencimiento nuevo (UC10 FR-003) (depende de T024, T029)
- [ ] T041 Implementar `PrestamoController`: `POST /api/prestamos/{reservaId}/renovacion` y `GET /api/prestamos/vencimiento-previsto`, y `GET /api/reservas/mias` en `ReservaController` con el cupo usado
- [ ] T042 Frontend: `ListaMisReservas.tsx` y `RenovarPrestamo.tsx` en una pantalla mínima `app/(app)/mis-reservas/page.tsx`, que muestra el vencimiento, si queda renovación y el motivo cuando se deniega

**Checkpoint**: El spec de UC2 queda cubierto de punta a punta

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Mejoras que afectan a toda la funcionalidad

- [ ] T043 [P] Verificar SC-003 con una prueba que recorra las cuatro denegaciones y compruebe que ninguna respuesta sale sin `codigo` ni sin mensaje
- [ ] T044 [P] Registrar en logs cada confirmación, cada denegación y cada consulta de sanciones con su duración y su resultado, sin datos personales más allá del identificador del usuario (FR-005, FR-007, UC6 FR-009)
- [ ] T045 [P] Medir la confirmación bajo concurrencia contra un Módulo 1 y un Módulo 3 simulados con su latencia prometida, y comprobar que no aparecen ocupaciones solapadas (SC-004)
- [ ] T046 [P] Actualizar el README con `docker-compose up` para PostgreSQL y Kafka, y con cómo ver la *outbox* cuando el broker está caído
- [ ] T047 Llevar a `pendientes-clarificacion.md` los NEEDS CLARIFICATION que este plan deja abiertos y revisarlos con el equipo

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: depende de que el plan de UC1 esté hecho; sin su Phase 2 no hay esquema, seguridad ni reloj
- **Foundational (Phase 2)**: depende de Setup - BLOCKS la user story
- **User Story 1 (Phase 3)**: depende de Foundational
- **Renovación (Phase 4)**: depende de la Phase 3, porque reusa el préstamo, la calculadora y la *outbox*
- **Polish (Phase 5)**: depende de que las Phases 3 y 4 estén completas

### Dependencias con otros casos de uso

- **UC1 `Consultar recursos`**: es el plan previo. Aporta el esquema, `FranjaHoraria`, `Ocupacion`, la seguridad, el `Reloj`, los clientes de los Módulos 1 y 3 y la pantalla desde la que se reserva.
- **UC8 `Consultar disponibilidad`**: este plan cierra lo que faltaba (un solo recurso y el "hasta cuándo", UC8 FR-012). Con esto UC8 queda completo.
- **UC6 `Consultar sanciones`**: este plan añade la denegación `RES-003` y el registro con el que se audita por qué se denegó una reserva (UC6 FR-009), que sale de la tabla `denegacion` (T028) y del log de cada consulta al Módulo 3 (T044). Con esto UC6 queda completo.
- **UC7 `Actualizar estado de los recursos`**: se implementa solo el alcance vigente (dejar escrita la ocupación). Su propio plan añadirá el aviso al Módulo 1 y la tabla `CambioDeEstado` cuando se responda P-20.
- **UC10 `Reportar información de la reserva`**: este plan monta la *outbox*, el publicador y el evento de la ficha. Su plan añadirá la ficha de los bloqueos académicos que llegan por UC3 y el contrato definitivo del evento.
- **UC3 `Importar horarios semestrales`**: reusa `ReservarRecursosUseCase` con `origen = ACADEMICO`. El campo ya queda listo; lo que se salta depende de P-19.
- **UC4 `Cancelar reserva`** y **UC9 `Recibir reporte de no asistencia`**: cuelgan de la reserva creada aquí. UC2 no los necesita; mientras no existan, una reserva solo se libera al pasar su franja o con el check-out del préstamo. La pantalla `mis-reservas` que se crea aquí es donde UC4 pondrá su botón.
- **UC12 `Recibir check-out`**: es lo único que cierra un préstamo y libera su cupo (FR-008, FR-015). Hasta que exista, en local se cierra con datos de prueba.

### Within User Story 1

- Modelo y puertos (T012, T021 a T023) → calculadora (T024) → casos de uso de los `<<include>>` (T025, T026) → `ReservarRecursosUseCase` (T027, T028)
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
- **NEEDS CLARIFICATION abiertos en este plan**:
  - **P-10**: si el Módulo 3 no responde, aquí se bloquea la reserva con `503`, que es la opción conservadora del spec
  - **P-11**: el alcance de la sanción; hoy cualquier sanción vigente bloquea cualquier reserva
  - **P-19**: qué reglas se salta una reserva de origen académico; el supuesto es sanción, cupo y tope de 2 horas
  - **P-16**: el plazo máximo de préstamo tiene que venir en la ficha del Módulo 1; sin él, FR-012 no se puede implementar
  - **P-20 punto 1**: mientras no se responda, `Actualizar estado de los recursos` no le envía nada al Módulo 1
  - **P-08**: un préstamo vencido y no devuelto sigue ocupando el activo sin fecha de fin; falta cuándo se da por perdido
  - **Código para `EN_MANTENIMIENTO`**: el diccionario de errores no tiene uno; se responde `RES-004` con un mensaje propio y se propone un `RES-005`
  - **Calendario de festivos**: FR-014 cuenta días hábiles y nadie nos da el calendario de la universidad; por ahora es una lista en `application.properties`
