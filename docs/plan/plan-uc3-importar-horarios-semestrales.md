# Implementation Plan: Importar horarios semestrales (UC3)

**Date**: 2026-10-02
**Spec**: [spec-modulo2-uc3-importar-horarios-semestrales.md](../specs/spec-modulo2-uc3-importar-horarios-semestrales.md)
**Plan general**: [plan-arquitectura.md](./plan-arquitectura.md)
**Planes previos**: [plan-uc1-consultar-recursos.md](./plan-uc1-consultar-recursos.md) (base compartida) y [plan-uc2-reservar-recursos.md](./plan-uc2-reservar-recursos.md) — este plan **reusa `ReserveResourcesUseCase`** con `origen = ACADEMICO`, la *outbox* y `BusinessCalendar`, que UC2 dejó montados

## Summary

UC3 es el caso de uso que le dice al sistema qué le pertenece a la actividad docente. La Dirección de Programa carga el horario del semestre y cada clase queda apartada; después puede registrar una necesidad extraordinaria que **se impone** sobre las reservas estudiantiles ya confirmadas, cancelándolas con su motivo. De aquí sale toda la prioridad académica del módulo: es lo que alimenta el `BLOQUEO_ACADEMICO` que UC1 muestra y el `RES-001` que UC2 deniega.

**Enfoque técnico:**

1. La carga es un **CSV de clases semanales**, no de sesiones sueltas: una fila dice "Redes, laboratorio ESP-0412, lunes de 08:00 a 10:00". `SessionExpander` la convierte en una sesión por semana dentro del periodo académico, omitiendo los días no hábiles con el `BusinessCalendar` que UC2 ya creó.
2. Todo pasa por **dos peticiones**: una valida y no cambia nada, la otra aplica. La primera devuelve el reporte fila por fila (FR-003) y **cuántas y cuáles reservas estudiantiles se cancelarían** (FR-008); la segunda aplica todo en una sola transacción. Sin esa confirmación explícita no se cancela la reserva de nadie.
3. La validación es **todo o nada** (FR-008, escenario 2): una sola fila con error —recurso inexistente, hora mal escrita, choque con otra clase (FR-006), activo ya entregado (FR-010) o recurso en mantenimiento— y no se carga nada.
4. Cada sesión que sobrevive entra por `ReserveResourcesUseCase` con `origen = ACADEMICO` (P-06), así que pasa igual por `Actualizar estado de los recursos` (UC7) y por `Reportar información de la reserva` (UC10) sin código nuevo.
5. Cuando una sesión desplaza una reserva estudiantil, la cancelación y el bloqueo ocurren en **la misma transacción**, de modo que la restricción de exclusión nunca vea las dos `CONFIRMADA` a la vez. Cada cancelación sale hacia el Módulo 3 por la *outbox* (UC11).
6. Recargar el mismo horario **no duplica nada** (FR-007, SC-003): cada sesión lleva una huella `session_key`, y una recarga reconcilia —crea las nuevas, deja intactas las que no cambiaron y propone cancelar las que desaparecieron del archivo.

De UC4 y UC11 este plan implementa solo lo que necesita: la cancelación por prioridad académica y el evento de cancelación hacia el Módulo 3. El plan de UC4 completará la cancelación que hace el titular, con sus códigos `CAN-001` y `CAN-002`.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript con Next.js y React (frontend)
**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Validation, RestClient), Spring for Apache Kafka, **Apache Commons CSV** (nuevo en este plan), Flyway, springdoc-openapi
**Storage**: PostgreSQL con `btree_gist`. UC3 **escribe** `semester_schedule`, `schedule_upload_row`, `academic_block`, `reservation` y `outbox_message`, y lee `loan` y `app_user`.
**Testing**: JUnit 5 y AssertJ (dominio y casos de uso con puertos falsos), `@WebMvcTest` con `MockMultipartFile` (controlador), Testcontainers con PostgreSQL (la transacción de cancelar y bloquear, y la idempotencia de la recarga), WireMock (Módulo 1), ArchUnit
**Target Platform**: Servidor Linux con JVM 21; navegador web para el frontend
**Performance Goals**: La carga de un semestre completo termina en menos de 5 minutos (SC-001). El presupuesto se gasta sobre todo en el Módulo 1, y por eso se le pregunta **una vez por lote** de recursos y no una vez por fila.
**Constraints**:
- Todo o nada: una carga nunca se aplica a medias (FR-008, edge case **Carga tardía del horario**).
- Una clase nunca desplaza a otra clase (FR-006), y nunca le quita a nadie un activo que ya recogió (FR-010).
- Recargar el mismo horario deja cero bloqueos repetidos (FR-007, SC-003).
- Ninguna reserva estudiantil se cancela sin que la Dirección de Programa lo confirme antes (FR-008).
- Las cancelaciones por prioridad académica no penalizan a nadie (UC4 FR-005, UC11 FR-006).
- Solo el rol `DIRECCION_PROGRAMA` entra a este caso de uso.
**Scale/Scope**: Un horario de semestre del orden de cientos de clases semanales, que se expanden a miles de sesiones. Dos pantallas nuevas en el frontend, las dos de Dirección de Programa.

## Project Structure

### Documentation (this feature)

```text
docs/
├── specs/
│   ├── spec-modulo2-uc3-importar-horarios-semestrales.md       # spec de este plan
│   ├── spec-modulo2-uc2-reservar-recursos.md                   # <<include>>: por aquí entra cada clase
│   ├── spec-modulo2-uc4-cancelar-reserva.md                    # parte implementada aquí: prioridad académica
│   ├── spec-modulo2-uc11-reportar-cancelacion-reserva.md       # parte implementada aquí: el evento
│   ├── spec-modulo2-uc7-actualizar-estado-recursos.md          # <<include>>: vía UC2
│   └── spec-modulo2-uc10-reportar-informacion-reserva.md       # <<include>>: vía UC2
└── plan/
    ├── plan-arquitectura.md                   # capas, carpetas, base de datos y Kafka
    ├── modelo-datos-der.md                    # lo actualiza T026
    ├── plan-uc1-consultar-recursos.md         # base compartida
    ├── plan-uc2-reservar-recursos.md          # el caso de uso que este incluye
    └── plan-uc3-importar-horarios-semestrales.md   # este archivo
```

### Source Code (repository root)

Solo los archivos que crea o toca este plan; los que ya existen van marcados. La organización completa está en [plan-arquitectura.md](./plan-arquitectura.md#organización-de-carpetas).

```text
build.gradle                                        # + commons-csv (T001)

src/main/java/edu/unimagdalena/reservasunimag/
├── domain/
│   ├── model/
│   │   ├── academicblock/
│   │   │   ├── WeeklyClass.java                   # una fila del archivo, ya validada
│   │   │   ├── ClassSession.java                  # una ocurrencia concreta con fecha
│   │   │   ├── AcademicData.java                # asignatura, código, grupo, programa, docente
│   │   │   ├── BlockType.java                    # REGULAR, EXTRAORDINARIO
│   │   │   ├── AcademicBlock.java
│   │   │   ├── SessionKey.java                    # la huella que hace idempotente la recarga
│   │   │   └── SemesterSchedule.java               # la carga y su resultado
│   │   ├── upload/
│   │   │   ├── ScheduleUpload.java                   # estado de la carga en curso
│   │   │   ├── UploadStatus.java                    # VALIDATED, APPLIED, REJECTED, EXPIRED
│   │   │   ├── ValidatedRow.java                   # fila + dictamen + sesiones expandidas
│   │   │   ├── RowVerdict.java                   # los siete dictámenes de la tabla
│   │   │   ├── ImportReport.java           # FR-003
│   │   │   └── UploadImpact.java                 # FR-008: qué se cancelaría
│   │   ├── reservation/
│   │   │   └── CancellationReason.java              # nuevo: ACADEMIC_PRIORITY, HOLDER, ...
│   │   └── error/
│   │       ├── UploadNotApplicableException.java
│   │       └── InvalidFileException.java
│   ├── port/
│   │   ├── in/
│   │   │   ├── ValidateSchedulePort.java             # paso 1: no cambia nada
│   │   │   ├── ApplySchedulePort.java             # paso 2: aplica
│   │   │   ├── RegisterExtraordinaryNeedPort.java
│   │   │   └── CancelReservationPort.java            # UC4: solo la rama académica
│   │   └── out/
│   │       ├── ScheduleUploadRepositoryPort.java
│   │       ├── AcademicBlockRepositoryPort.java          # bloqueos por clave de sesión y por periodo
│   │       ├── ScheduleReader.java               # parsea el archivo → filas crudas
│   │       ├── ReservationRepositoryPort.java          # (existe) + cancelar en lote
│   │       ├── InventoryPort.java                 # (existe) usa operationalStatus por lote
│   │       ├── Module3NotifierPort.java         # (existe) + evento de cancelación
│   │       └── BusinessCalendar.java                # (existe, de UC2)
│   └── usecase/
│       ├── importschedule/
│       │   ├── ValidateScheduleUseCase.java
│       │   ├── ApplyScheduleUseCase.java
│       │   ├── RegisterExtraordinaryNeedUseCase.java
│       │   ├── SessionExpander.java             # clase semanal → sesiones del periodo
│       │   ├── RowClassifier.java            # asigna el dictamen a cada fila
│       │   └── ScheduleReconciler.java         # FR-007: recarga sin duplicar
│       ├── cancelreservation/
│       │   └── CancelReservationUseCase.java         # solo prioridad académica (UC4 FR-004 a FR-006)
│       └── reserveresources/
│           └── ReserveResourcesUseCase.java        # (existe) se reusa con origen ACADEMICO
│
└── infrastructure/
    ├── adapter/
    │   ├── in/web/schedule/
    │   │   ├── ScheduleController.java              # los cuatro endpoints
    │   │   ├── ValidateScheduleRequest.java          # multipart: archivo + periodo
    │   │   ├── ImportReportResponse.java
    │   │   ├── ExtraordinaryNeedRequest.java
    │   │   └── UploadResponse.java
    │   └── out/
    │       ├── persistence/
    │       │   ├── entity/                         # + SemesterScheduleJpa, ScheduleUploadRowJpa,
    │       │   │                                   #   AcademicBlockJpa
    │       │   ├── repository/                     # + los Spring Data de esas tres
    │       │   ├── ScheduleUploadPersistenceAdapter.java
    │       │   └── AcademicBlockPersistenceAdapter.java
    │       ├── file/
    │       │   └── CsvScheduleReader.java        # → ScheduleReader
    │       └── messaging/
    │           └── evento/ReservationCancelledEvent.java   # UC11
    └── config/
        ├── ReservationProperties.java                # (existe) + vigencia de la carga validada
        └── UseCasesConfig.java                   # (existe) + los beans de UC3

src/main/resources/db/migration/
└── V4__schedule_upload.sql                           # semester_schedule, schedule_upload_row,
                                                    # academic_block y la clave de sesión

src/test/java/edu/unimagdalena/reservasunimag/
├── domain/usecase/importschedule/
│   ├── SessionExpanderTest.java
│   ├── RowClassifierTest.java
│   ├── ValidateScheduleUseCaseTest.java
│   ├── ApplyScheduleUseCaseTest.java
│   └── ScheduleReconcilerTest.java
├── domain/usecase/cancelreservation/CancelReservationUseCaseTest.java
└── infrastructure/adapter/
    ├── in/web/schedule/ScheduleControllerTest.java
    ├── out/archivo/CsvScheduleReaderTest.java
    ├── out/persistence/ScheduleUploadPersistenceAdapterIT.java
    └── out/persistence/AcademicPriorityIT.java

src/test/resources/contratos/                       # + los JSON y el CSV de la sección Contratos

frontend/src/
├── app/(direccion)/schedules/page.tsx               # cargar, revisar el reporte y confirmar
├── app/(direccion)/horarios/extraordinary/page.tsx
└── features/schedules/
    ├── FileUpload.tsx
    ├── ImportReportView.tsx                      # tabla fila por fila
    ├── ConfirmImpact.tsx                        # las reservas que se cancelarían
    ├── ExtraordinaryNeedForm.tsx
    ├── api.ts
    └── types.ts
```

**Structure Decision**: se mantiene la estructura del plan general. Tres añadidos: el paquete `domain/model/upload`, porque la carga validada es un objeto de negocio con estado propio y no un detalle del controlador; el adaptador `out/archivo`, primera salida del hexágono que no es base de datos ni HTTP —el parseo del CSV es tecnología y no puede vivir en `domain`—; y el grupo `(direction)` del frontend, que el plan general ya preveía y aquí se usa por primera vez.

### Decisiones de diseño de este caso de uso

**El archivo trae clases semanales, no sesiones.** Una fila dice "Redes, ESP-0412, lunes de 08:00 a 10:00" y el sistema la expande a una sesión por semana del periodo. La alternativa —una fila por sesión— obligaría a escribir a mano dieciséis filas por clase, y un horario de cientos de clases sería un archivo de miles de líneas mantenido a mano. La expansión es también lo que hace que SC-001 tenga sentido: el trabajo del sistema es grande, el del archivo es pequeño. El periodo (`termStart` y `termEnd`) va en la petición, no en el archivo, porque es el mismo para todas las filas.

**Los días no hábiles no tienen clase.** La expansión salta sábados, domingos y festivos usando el `BusinessCalendar` de UC2, así que una clase de lunes no genera sesión el lunes festivo. Es la misma lista de `application.properties` y arrastra el mismo pendiente: nadie nos ha dado el calendario de la universidad.

**Dos peticiones: validar y aplicar.** FR-008 pide mostrar el impacto y esperar antes de cancelar nada, así que no cabe en una sola llamada:

| Paso | Endpoint | Qué hace | Qué cambia |
|---|---|---|---|
| 1 | `POST /api/schedules/validations` | Lee el archivo, expande, clasifica cada fila y calcula qué reservas se cancelarían | Guarda la carga como `VALIDATED` y sus filas; **ninguna reserva se toca** |
| 2 | `POST /api/schedules/{uploadId}/confirmation` | Vuelve a comprobar y aplica todo en una transacción | Crea los bloqueos y cancela las reservas desplazadas |

La carga validada se guarda —no se le pide al navegador que reenvíe el archivo— por dos razones: el reporte que la persona aprobó es exactamente lo que se aplica, y queda la constancia de FR-009 aunque nunca se confirme. Caduca a los `reservations.upload.vigencia` (30 minutos por defecto): pasado ese plazo queda `EXPIRED` y hay que volver a validar, porque el impacto que se mostró ya no es de fiar.

**Ocho dictámenes por fila.** `RowClassifier` le pone a cada fila uno de estos, y de ellos sale si la carga es aplicable:

| Dictamen | Cuándo | ¿Bloquea la carga? |
|---|---|---|
| `OK` | La franja está libre | No |
| `UNCHANGED` | Ya existe un bloqueo con la misma `session_key` (FR-007) | No |
| `DISPLACES_RESERVATIONS` | Se cruza con reservas estudiantiles cancelables (FR-005) | No, pero exige la confirmación de FR-008 |
| `REJECTED_FORMAT` | Hora mal escrita, día inválido, columna faltante (escenario 2) | **Sí** |
| `REJECTED_RESOURCE` | El `resource_id` no existe en el Módulo 1 (escenario 2) | **Sí** |
| `ACADEMIC_CLASH` | Ya hay otro `BLOQUEO_ACADEMICO` cruzado (FR-006, escenario 4) | **Sí** |
| `ASSET_ALREADY_PICKED_UP` | El activo ya está en manos de alguien (FR-010, segundo caso) | **Sí** |
| `RESOURCE_UNDER_MAINTENANCE` | El Módulo 1 reporta `EN_MANTENIMIENTO` (edge case **Recurso dado de baja**) | **Sí** |

**Decisión: un choque bloquea la carga entera, igual que un error de formato.** El escenario 2 solo habla de filas con error, y FR-006 y FR-010 dicen "avisa del choque y pide que lo resuelvan las personas responsables". Aplicar las demás filas y callar el choque dejaría una clase sin espacio sin que nadie se enterara, que es justo lo que este caso de uso existe para evitar; y aplicar a medias contradice el todo-o-nada de FR-008. Así que la carga es aplicable **solo** si ninguna fila tiene un dictamen bloqueante. El reporte los lista todos de una vez —no se para en el primero— para poder corregir el archivo en una sola pasada.

**Un activo prestado se trata según si ya salió del inventario (FR-010).** Es la única regla donde el mismo choque tiene dos desenlaces:

| Situación del activo | Dictamen | Por qué |
|---|---|---|
| Apartado, sin recoger (`prestamo.picked_up_at` nulo) | `DISPLACES_RESERVATIONS` | Se cancela como cualquier reserva y el activo queda libre para la clase |
| Ya entregado y sin devolver | `ASSET_ALREADY_PICKED_UP` | La prioridad académica desplaza lo que no ha salido; lo que ya salió hay que pedirlo de vuelta, y eso no lo puede hacer el sistema |

**La huella que hace idempotente la recarga (FR-007, SC-003).** Cada sesión lleva una `session_key`: el hash de `academic_term`, `resource_id`, `start`, `end`, `course_code` y `group`. Va en `academic_block` con un índice **único**, así que la base misma impide el duplicado. El truco para que funcione con las cancelaciones: al cancelar un bloqueo la columna se pone a `NULL`, y Postgres no considera iguales dos `NULL` en un índice único, de modo que la misma clase se puede volver a cargar después de retirarla.

```sql
ALTER TABLE academic_block ADD COLUMN session_key varchar;
CREATE UNIQUE INDEX academic_block_session_key ON academic_block (session_key);
```

**Reconciliar una recarga.** Volver a cargar el mismo `academic_term` no es empezar de cero: `ScheduleReconciler` compara las claves del archivo con los bloqueos vigentes del periodo y reparte en tres montones — las que ya están (`UNCHANGED`, no se tocan), las nuevas (se crean) y las que **estaban y ya no vienen**, que se proponen para cancelar y entran en el impacto que hay que confirmar. Retirar una clase es una cancelación de una reserva de origen académico y se le reporta al Módulo 3 por la *outbox*, igual que cualquier otra.

**Cancelar y bloquear en la misma transacción (escenario 3, FR-008).** La restricción `reservation_no_overlap` no admite dos `CONFIRMADA` solapadas, así que el orden importa y no puede partirse:

1. `SELECT ... FOR UPDATE` de las reservas estudiantiles que se van a desplazar, por `resource_id`, para que nadie confirme una nueva en medio.
2. Recomprobar que el impacto sigue siendo el que se mostró; si cambió, la carga se rechaza con `409` y hay que volver a validar.
3. Cancelar esas reservas con `CANCELADA_POR_PRIORIDAD_ACADEMICA` y su `cancelled_at`.
4. Crear cada sesión llamando a `ReserveResourcesUseCase` con `origen = ACADEMICO`.
5. Insertar en `outbox_message` un evento de cancelación por cada reserva desplazada (UC11) —las fichas de los bloqueos las inserta UC2 por su cuenta.

Una carga grande hace esto en **una sola transacción**, que es lo que FR-008 exige. Para que quepa en el presupuesto de SC-001, los pasos 3 y 4 van por lotes y la comprobación de cruces es una sola consulta por lote de recursos, no una por fila.

**Qué reglas se salta el origen académico.** Las mismas que ya asumió el plan de UC2: sanción, cupo de 3 y tope de 2 horas —una clase de 3 horas es normal—. UC3 agrega que una sesión tampoco tiene titular, así que `user_id` va nulo y el `CHECK` de la tabla lo exige.

**Una sola llamada al Módulo 1 por carga.** El catálogo no se pide fila por fila: se juntan todos los `resource_id` distintos del archivo y se usa la operación por lote de [UC1 § Contratos §2.2](./plan-uc1-consultar-recursos.md#2-módulo-1--inventoryport), que hasta ahora no tenía consumidor real. De ahí salen a la vez los `REJECTED_RESOURCE` (los que vuelven en `notFound`) y los `RESOURCE_UNDER_MAINTENANCE`. Si el Módulo 1 no responde, la validación falla completa con `503`: no se puede bloquear un salón sin saber si existe.

**La necesidad extraordinaria es la misma maquinaria con una sola fila.** `RegisterExtraordinaryNeedUseCase` no repite nada: arma una `ClassSession` suelta con `BlockType.EXTRAORDINARIO` y `semester_schedule_id` nulo, la pasa por el mismo clasificador y, si desplaza reservas, por la misma confirmación. La diferencia está en la respuesta: al ser una sola franja, el impacto se muestra en la misma petición y se aplica con `confirm: true` en lugar de guardar una carga (ver [Contratos §3](#3-post-apischedulesextraordinary-needs)).

## Contratos

Aquí queda definido todo el JSON de UC3: los cuatro endpoints de Dirección de Programa, el formato del archivo, lo que se le pregunta al Módulo 1 y el evento de cancelación que sale hacia el Módulo 3. Los ejemplos son el contrato: las pruebas de T019, T020 y T023 se escriben contra ellos y los mismos JSON viven como *fixtures* en `src/test/resources/contratos/`.

Se aplican sin repetirlas las **convenciones comunes** de [plan-uc1-consultar-recursos.md § Contratos](./plan-uc1-consultar-recursos.md#contratos). Cuatro reglas propias de este caso de uso:

| Regla | Detalle |
|---|---|
| Solo Dirección de Programa | Los cuatro endpoints exigen el rol `DIRECCION_PROGRAMA`. Un `ESTUDIANTE` o `MONITOR` con sesión válida recibe `403`, no `401`. |
| Validar nunca cambia nada | `POST /api/schedules/validations` es seguro de repetir: guarda la carga y su reporte, pero no crea bloqueos ni cancela reservas. Lo único que cambia es `semester_schedule` y sus filas. |
| El reporte habla de filas, no de sesiones | Los dictámenes y los errores se reportan **por fila del archivo**, que es lo que la persona puede corregir. Las sesiones expandidas se cuentan, pero no se listan una por una: un semestre son miles. |
| `row` es el número real del archivo | Empieza en 2, porque la 1 es la cabecera. Así el mensaje de error se puede buscar directamente en el CSV. |

---

### 1. `POST /api/schedules/validations`

Paso 1 de la carga. `multipart/form-data`, porque va un archivo:

| Parte | Tipo | Obligatorio | Nota |
|---|---|---|---|
| `archivo` | archivo CSV | sí | El formato está en [§5](#5-formato-del-archivo-csv). Máximo `reservations.upload.max-size` (2 MB por defecto). |
| `academicTerm` | texto | sí | Por ejemplo `2026-2`. Es la clave con la que se reconcilia una recarga (FR-007). |
| `termStart` | `yyyy-MM-dd` | sí | Primer día de clases del periodo. |
| `termEnd` | `yyyy-MM-dd` | sí | Último día; debe ser posterior a `termStart`. |

**`200 OK`** — el archivo está limpio y hay reservas que se desplazarían (escenario 3 + edge case **Carga tardía del horario**)

```json
{
  "uploadId": "7e2a4c81-3f95-4d60-b8a7-1c9e5d0f2a63",
  "academicTerm": "2026-2",
  "term": { "start": "2026-08-10", "end": "2026-12-05" },
  "status": "VALIDATED",
  "validatedAt": "2026-10-02T15:40:11-05:00",
  "expiresAt": "2026-10-02T16:10:11-05:00",
  "applicable": true,
  "requiresConfirmation": true,
  "summary": {
    "rowsRead": 312,
    "rowsOk": 300,
    "rowsUnchanged": 12,
    "rowsWithBlockingVerdict": 0,
    "sessionsToCreate": 4680,
    "sessionsSkippedNonBusinessDay": 96,
    "blocksToWithdraw": 0,
    "reservationsToCancel": 3
  },
  "rows": [
    {
      "row": 2,
      "resourceId": "ESP-0412",
      "resourceName": "Laboratorio de Redes",
      "course": "Redes de Computadores",
      "courseCode": "IS-402",
      "group": "1",
      "dayOfWeek": "LUNES",
      "start": "08:00",
      "end": "10:00",
      "verdict": "OK",
      "sessions": 16
    },
    {
      "row": 7,
      "resourceId": "ESP-0301",
      "resourceName": "Auditorio Menor",
      "course": "Seminario de Investigación",
      "courseCode": "IS-510",
      "group": "1",
      "dayOfWeek": "JUEVES",
      "start": "14:00",
      "end": "16:00",
      "verdict": "DISPLACES_RESERVATIONS",
      "sessions": 16,
      "affectedReservations": [
        {
          "reservationId": "a1b2c3d4-5e6f-4789-9abc-def012345678",
          "holder": { "code": "2019114045", "name": "Nombre del estudiante" },
          "start": "2026-09-10T14:00:00-05:00",
          "end": "2026-09-10T16:00:00-05:00",
          "category": "ESPACIO"
        }
      ]
    },
    {
      "row": 19,
      "resourceId": "ESP-0107",
      "course": "Cálculo I",
      "courseCode": "MA-101",
      "group": "3",
      "dayOfWeek": "MARTES",
      "start": "06:00",
      "end": "08:00",
      "verdict": "UNCHANGED",
      "sessions": 16
    }
  ]
}
```

| Campo | Nota |
|---|---|
| `uploadId` | Lo que se le pasa al paso 2. Identifica esta validación, no el periodo. |
| `applicable` | `false` si alguna fila tiene un dictamen bloqueante. El paso 2 lo rechaza. |
| `requiresConfirmation` | `true` cuando `reservationsToCancel` o `blocksToWithdraw` son mayores que 0 (FR-008). Si es `false`, el paso 2 sigue siendo obligatorio, pero la pantalla no tiene que advertir nada. |
| `expiresAt` | Pasado ese instante la carga queda `EXPIRED` y hay que volver a validar, porque el impacto mostrado dejó de ser fiable. |
| `resumen.sessionsSkippedNonBusinessDay` | Las sesiones que caían en sábado, domingo o festivo. Se informa para que nadie piense que se perdieron filas. |
| `filas[].sesiones` | Cuántas ocurrencias genera esa fila en el periodo. |
| `filas[].affectedReservations` | Solo en `DISPLACES_RESERVATIONS`. Lleva el titular **con nombre**, porque FR-008 pide mostrar *cuáles* se cancelarían y quien decide es Dirección de Programa, que ya tiene esa información. Es la única respuesta del módulo que nombra al titular de una reserva, y por eso solo este rol la ve. |

**`200 OK`** — el archivo tiene problemas y la carga no se puede aplicar (escenario 2, 4 y edge cases)

```json
{
  "uploadId": "b4d9f0c2-7a16-4e38-95bd-2f8c1e0a7d45",
  "academicTerm": "2026-2",
  "term": { "start": "2026-08-10", "end": "2026-12-05" },
  "status": "REJECTED",
  "validatedAt": "2026-10-02T15:44:02-05:00",
  "applicable": false,
  "requiresConfirmation": false,
  "summary": {
    "rowsRead": 312,
    "rowsOk": 307,
    "rowsUnchanged": 0,
    "rowsWithBlockingVerdict": 5,
    "sessionsToCreate": 0,
    "sessionsSkippedNonBusinessDay": 0,
    "blocksToWithdraw": 0,
    "reservationsToCancel": 0
  },
  "rows": [
    {
      "row": 23,
      "resourceId": "ESP-9999",
      "course": "Física II",
      "courseCode": "FI-202",
      "group": "2",
      "dayOfWeek": "MIERCOLES",
      "start": "10:00",
      "end": "12:00",
      "verdict": "REJECTED_RESOURCE",
      "sessions": 0,
      "problem": "El recurso ESP-9999 no existe en el inventario."
    },
    {
      "row": 41,
      "resourceId": "ESP-0412",
      "course": "Sistemas Operativos",
      "courseCode": "IS-404",
      "group": "1",
      "dayOfWeek": "LUNES",
      "start": "9:3",
      "end": "11:30",
      "verdict": "REJECTED_FORMAT",
      "sessions": 0,
      "problem": "La column start_time no tiene el formato HH:mm.",
      "column": "start_time"
    },
    {
      "row": 58,
      "resourceId": "ESP-0412",
      "course": "Arquitectura de Computadores",
      "courseCode": "IS-301",
      "group": "1",
      "dayOfWeek": "LUNES",
      "start": "09:00",
      "end": "11:00",
      "verdict": "ACADEMIC_CLASH",
      "sessions": 0,
      "problem": "Se cruza con otra clase ya bloqueada en este recurso: IS-402 group 1, lunes de 08:00 a 10:00.",
      "conflictingBlock": { "course": "Redes de Computadores", "courseCode": "IS-402", "group": "1" }
    },
    {
      "row": 77,
      "resourceId": "ACT-004512",
      "course": "Dibujo Técnico",
      "courseCode": "IC-110",
      "group": "1",
      "dayOfWeek": "VIERNES",
      "start": "14:00",
      "end": "16:00",
      "verdict": "ASSET_ALREADY_PICKED_UP",
      "sessions": 0,
      "problem": "El active ya está en manos de una person until el 2026-09-12 a las 22:00. Hay que pedirlo de vuelta antes de bloquearlo para la clase."
    },
    {
      "row": 92,
      "resourceId": "ESP-0220",
      "course": "Química General",
      "courseCode": "QU-101",
      "group": "1",
      "dayOfWeek": "MARTES",
      "start": "08:00",
      "end": "10:00",
      "verdict": "RESOURCE_UNDER_MAINTENANCE",
      "sessions": 0,
      "problem": "El recurso está en mantenimiento. Esta clase necesita otro espacio."
    }
  ]
}
```

`ASSET_ALREADY_PICKED_UP` dice **hasta cuándo** está comprometido el activo, pero no quién lo tiene: ahí sí aplica UC8 FR-005, porque es información que no hace falta para decidir. `ACADEMIC_CLASH` nombra la asignatura en conflicto, no al docente.

El `estado: "REJECTED"` queda guardado: la constancia de FR-009 vale también para una carga que no se pudo aplicar.

**Errores**

| Código | Cuándo |
|---|---|
| `400` con `codigo: "EMPTY_FILE"` | El CSV no trae ninguna fila de datos (edge case **Archivo vacío**). Nunca se responde con un resumen de éxito. |
| `400` con `codigo: "INVALID_HEADER"` | Faltan columnas obligatorias o el archivo no es un CSV legible; lleva `missingColumns`. |
| `400` con `codigo: "INVALID_TERM"` | `termEnd` no es posterior a `termStart`. |
| `401` / `403` | Sin sesión / con sesión pero sin el rol `DIRECCION_PROGRAMA`. |
| `413` | El archivo pasa de `reservations.upload.max-size`. |
| `503` con `modulo: "MODULO_1"` | El inventario no respondió y no se puede validar ni una fila. |

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/invalid-file",
  "title": "Archivo de horario inválido",
  "status": 400,
  "detail": "El archivo no trae ninguna clase. No se cargó nada.",
  "instance": "/api/schedules/validations",
  "code": "EMPTY_FILE"
}
```

---

### 2. `POST /api/schedules/{uploadId}/confirmation`

Paso 2. Sin cuerpo: lo que se aplica es exactamente la carga que se validó y que la persona acaba de revisar.

**`200 OK`**

```json
{
  "uploadId": "7e2a4c81-3f95-4d60-b8a7-1c9e5d0f2a63",
  "academicTerm": "2026-2",
  "status": "APPLIED",
  "appliedAt": "2026-10-02T15:47:35-05:00",
  "appliedBy": { "userId": "3c7d1e9a-4b72-4f08-a561-8d2e0c9f4b13", "name": "Dirección de Programa" },
  "result": {
    "blocksCreated": 4680,
    "blocksUnchanged": 192,
    "blocksWithdrawn": 0,
    "reservationsCancelled": 3,
    "eventsQueued": 3
  }
}
```

`eventsQueued` son los de cancelación que quedaron en la *outbox* (UC11); las fichas de los bloqueos las encola `ReserveResourcesUseCase` por su cuenta y no se cuentan aquí. El `result` se guarda en `semester_schedule.resultado`, que es la columna `jsonb` que el plan general ya preveía, así que esta misma respuesta es lo que queda como constancia de FR-009.

**Errores**

| Código | `code` | Cuándo |
|---|---|---|
| `404` | — | El `uploadId` no existe. |
| `409` | `UPLOAD_NOT_APPLICABLE` | La validación había dado `aplicable: false`. Lleva `rowsWithBlockingVerdict`. |
| `409` | `UPLOAD_EXPIRED` | Pasó la vigencia; hay que volver a validar. Lleva `expiredAt`. |
| `409` | `UPLOAD_ALREADY_APPLIED` | Ya se confirmó. Lleva `appliedAt`, y la petición es inocua: no se aplica dos veces. |
| `409` | `IMPACT_CHANGED` | Entre validar y confirmar aparecieron o desaparecieron reservas afectadas. Lleva `reservationsToCancelNow` frente a `reservationsToCancelAtValidation`. |
| `403` | — | Otro rol, o un usuario distinto del que validó. |
| `503` | — | El Módulo 1 no respondió en la recomprobación. |

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/upload-not-applicable",
  "title": "La upload no se puede aplicar",
  "status": 409,
  "detail": "El impacto cambió desde que se validó: ahora se cancelarían 5 reservations en vez de 3. Vuelve a validar el archivo.",
  "instance": "/api/schedules/7e2a4c81-3f95-4d60-b8a7-1c9e5d0f2a63/confirmation",
  "code": "IMPACT_CHANGED",
  "reservationsToCancelAtValidation": 3,
  "reservationsToCancelNow": 5
}
```

**Decisión: `IMPACT_CHANGED` rechaza en vez de aplicar.** FR-008 dice que se muestra lo que se cancelaría y *luego* se aplica; si entre las dos cosas el impacto creció, lo que se aplicaría no es lo que se aprobó. Se prefiere la molestia de revalidar a cancelarle la reserva a alguien que no estaba en la lista.

---

### 3. `POST /api/schedules/extraordinary-needs`

La necesidad extraordinaria: una sola franja, sin archivo. Como es una, el impacto se devuelve en la misma petición y se aplica con `confirm`.

```json
{
  "resourceId": "ESP-0301",
  "date": "2026-09-10",
  "start": "14:00",
  "end": "16:00",
  "course": "Visita de pares académicos",
  "courseCode": "EXT-001",
  "group": "1",
  "program": "Ingeniería de Sistemas",
  "instructor": "Nombre del instructor",
  "confirm": false
}
```

Con `confirmar: false` (el valor por defecto) **no cambia nada** y responde `200` con lo que pasaría:

```json
{
  "applied": false,
  "verdict": "DISPLACES_RESERVATIONS",
  "resourceId": "ESP-0301",
  "resourceName": "Auditorio Menor",
  "start": "2026-09-10T14:00:00-05:00",
  "end": "2026-09-10T16:00:00-05:00",
  "affectedReservations": [
    {
      "reservationId": "a1b2c3d4-5e6f-4789-9abc-def012345678",
      "holder": { "code": "2019114045", "name": "Nombre del estudiante" },
      "start": "2026-09-10T14:00:00-05:00",
      "end": "2026-09-10T16:00:00-05:00",
      "category": "ESPACIO"
    }
  ]
}
```

Con `confirm: true` se aplica y responde `201` (escenario 3):

```http
HTTP/1.1 201 Created
Location: /api/reservations/d5e8f1a2-6b94-4c07-8d31-0f7a2e9c5b48
```

```json
{
  "applied": true,
  "verdict": "DISPLACES_RESERVATIONS",
  "block": {
    "reservationId": "d5e8f1a2-6b94-4c07-8d31-0f7a2e9c5b48",
    "resourceId": "ESP-0301",
    "type": "EXTRAORDINARIO",
    "start": "2026-09-10T14:00:00-05:00",
    "end": "2026-09-10T16:00:00-05:00",
    "course": "Visita de pares académicos",
    "program": "Ingeniería de Sistemas",
    "instructor": "Nombre del instructor"
  },
  "reservationsCancelled": 1,
  "eventsQueued": 1
}
```

Un bloqueo extraordinario es una reserva como las demás, así que su `reservationId` sirve en `GET /api/reservations/{id}` y su `Location` apunta ahí, no a una ruta de horarios.

**Errores**: `400` si la franja cae fuera de 06:00–22:00 o cruza la medianoche —el tope de 2 horas **no** aplica, por ser académica—, `404` si el recurso no existe, `409` con `verdict` `ACADEMIC_CLASH`, `ASSET_ALREADY_PICKED_UP` o `RESOURCE_UNDER_MAINTENANCE` cuando la franja no se puede tomar (FR-006, FR-010), `403` si el rol no es Dirección de Programa y `503` si el Módulo 1 no responde.

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errors/conflicting-academic-block",
  "title": "La timeSlot ya tiene actividad instructor",
  "status": 409,
  "detail": "Se cruza con la clase IS-402 group 1, de 08:00 a 10:00. Una clase no desplaza a otra: resuélvanlo entre los responsables.",
  "instance": "/api/schedules/extraordinary-needs",
  "verdict": "ACADEMIC_CLASH",
  "conflictingBlock": { "course": "Redes de Computadores", "courseCode": "IS-402", "group": "1" }
}
```

---

### 4. `GET /api/schedules`  y  `GET /api/schedules/{uploadId}`

El histórico, que es la cara consultable de la constancia de FR-009.

```json
{
  "uploads": [
    {
      "uploadId": "7e2a4c81-3f95-4d60-b8a7-1c9e5d0f2a63",
      "academicTerm": "2026-2",
      "status": "APPLIED",
      "fileName": "horario-2026-2.csv",
      "uploadedBy": { "userId": "3c7d1e9a-4b72-4f08-a561-8d2e0c9f4b13", "name": "Dirección de Programa" },
      "validatedAt": "2026-10-02T15:40:11-05:00",
      "appliedAt": "2026-10-02T15:47:35-05:00",
      "result": { "blocksCreated": 4680, "blocksUnchanged": 192, "blocksWithdrawn": 0, "reservationsCancelled": 3 }
    }
  ],
  "pagination": { "page": 1, "pageSize": 20, "totalPages": 1, "total": 1 }
}
```

`GET /api/schedules/{uploadId}` devuelve ese mismo objeto más el `rows[]` de la validación, con la misma forma de [§1](#1-post-apischedulesvalidations), para poder volver a mirar un reporte sin repetir la carga.

---

### 5. Formato del archivo CSV

CSV con cabecera, UTF-8, separador coma, comillas dobles para los campos que contengan comas. **Una fila es una clase semanal**, no una sesión.

```csv
course_code,course,group,program,instructor,resource_id,day_of_week,start_time,end_time
IS-402,Redes de Computadores,1,Ingeniería de Sistemas,Nombre del instructor,ESP-0412,LUNES,08:00,10:00
IS-510,Seminario de Investigación,1,Ingeniería de Sistemas,Nombre del instructor,ESP-0301,JUEVES,14:00,16:00
IC-110,"Dibujo Técnico, taller",1,Ingeniería Civil,Nombre del instructor,ESP-0150,VIERNES,14:00,16:00
```

| Columna | Obligatoria | Validación |
|---|---|---|
| `course_code` | sí | No vacía. Entra en la `session_key`. |
| `course` | sí | No vacía. |
| `group` | sí | No vacía. Entra en la `session_key`: dos grupos de la misma asignatura son clases distintas. |
| `program` | sí | No vacía. |
| `instructor` | no | Puede ir vacía si todavía no se asignó. |
| `resource_id` | sí | Debe existir en el Módulo 1. |
| `day_of_week` | sí | `LUNES` a `SABADO`; `DOMINGO` se rechaza. Sin tildes y en mayúsculas. |
| `start_time`, `end_time` | sí | `HH:mm`, dentro de 06:00–22:00, `end_time > start_time`, el mismo día. |

El `academicTerm` y las fechas del periodo **no** van en el archivo: viajan en la petición, porque son iguales para todas las filas y así el mismo archivo sirve para otro periodo.

Una fila con `day_of_week: SABADO` es válida —hay clases los sábados—, pero sus sesiones se omiten si el sábado está en la lista de días no hábiles. Una columna extra en el CSV se ignora; una obligatoria que falte es `INVALID_HEADER` y la carga no empieza.

**Decisión: CSV y no Excel.** Un `.xlsx` obligaría a sumar Apache POI y a tratar con celdas con formato de hora, que es donde aparecen los errores difíciles de explicar. Excel exporta a CSV en dos clics, y el reporte de [§1](#1-post-apischedulesvalidations) señala la fila y la columna exactas.
---

### 6. Módulo 1 — estado operativo por lote

UC3 no define nada nuevo: usa la operación `POST /api/v1/resources/operational-status` de [UC1 § Contratos §2.2](./plan-uc1-consultar-recursos.md#2-módulo-1--inventoryport), y es su primer consumidor real. Se le manda **una sola vez** la lista de `resource_id` distintos del archivo:

```json
{ "resourceIds": ["ESP-0412", "ESP-0301", "ESP-0150", "ESP-9999"] }
```

```json
{
  "statuses": [
    { "resourceId": "ESP-0412", "operationalStatus": "DISPONIBLE" },
    { "resourceId": "ESP-0301", "operationalStatus": "DISPONIBLE" },
    { "resourceId": "ESP-0150", "operationalStatus": "EN_MANTENIMIENTO", "statusReason": "Gotera en el techo" }
  ],
  "notFound": ["ESP-9999"]
}
```

De esa única respuesta salen tres cosas: los `REJECTED_RESOURCE` (lo que viene en `notFound`), los `RESOURCE_UNDER_MAINTENANCE` y el `resourceName` del reporte. Si el archivo trae más recursos distintos que `reservations.module1.status-batch` (200 por defecto), se parte en varias llamadas y se juntan las respuestas; una sola que falle invalida la validación completa con `503`.

> `statusReason` sigue sin propagarse al frontend, igual que en UC1: el texto del `problem` lo escribe el Módulo 2.

---

### 7. Evento de cancelación — `module2.reservation.cancellation.v1`

El `<<include>>` a UC11 que UC3 estrena. Un evento por cada reserva desplazada, insertado en `outbox_message` dentro de la misma transacción que la cancelación, con la clave de partición `reservation_id` y el envoltorio del plan general.

```json
{
  "eventId": "f2b8c4d1-6e39-4a70-95c8-3d1e7a0f2b56",
  "type": "ReservationCancelled",
  "version": 1,
  "occurredAt": "2026-10-02T15:47:35-05:00",
  "data": {
    "reservationId": "a1b2c3d4-5e6f-4789-9abc-def012345678",
    "status": "CANCELADA_POR_PRIORIDAD_ACADEMICA",
    "cancellationOrigin": "ACADEMIC_PRIORITY",
    "reason": "La timeSlot se asignó a actividad instructor: IS-510 group 1.",
    "attributableToPerson": false,
    "cancelledAt": "2026-10-02T15:47:35-05:00",
    "resource": {
      "id": "ESP-0301",
      "name": "Auditorio Menor",
      "category": "ESPACIO"
    },
    "holder": {
      "userId": "5f1b9c2d-7a34-4e81-b0f6-3c8d1e9a4b72",
      "code": "2019114045",
      "name": "Nombre del estudiante",
      "role": "ESTUDIANTE"
    },
    "releasedTime": {
      "start": "2026-09-10T14:00:00-05:00",
      "end": "2026-09-10T16:00:00-05:00"
    }
  }
}
```

| Campo | Por qué está | FR |
|---|---|---|
| `cancellationOrigin` | Distingue las tres procedencias: `HOLDER`, `ACADEMIC_PRIORITY` y `RESOURCE_UNAVAILABLE`. UC3 solo produce la segunda. | UC11 FR-003 |
| `attributableToPerson` | `false` siempre en UC3: una cancelación por prioridad académica no puede computar en contra de nadie. Es el campo del que depende que el Módulo 3 no sancione. | UC4 FR-005, UC11 FR-006 |
| `releasedTime` | La franja completa si era un espacio, y el periodo de préstamo entero si era un activo apartado y sin recoger —nunca un trozo—. | UC11 FR-002 |
| `reason` | Texto legible que nombra la clase que desplazó la reserva, sin el docente. | UC11 FR-002 |
| `eventId` | El `outbox_message.id`. Es lo que permite al Módulo 3 descartar una reentrega y lo que cumple "una misma cancelación no se reporta dos veces". | UC11 FR-007 |

`antelacionMinutos`, que UC11 FR-004 pide para la cancelación del titular, **no va** en estos eventos: no tiene sentido en una cancelación que la persona no pidió. Lo agrega el plan de UC4 cuando implemente esa rama.

Si Kafka está caído la carga se aplica igual y los eventos salen después (UC11 FR-008): están en la *outbox*, que es la misma de UC2.

**Retirar una clase también emite este evento**, con `estado: "CANCELADA"` y un `reason` que dice que la clase salió del horario.

---

### 8. Tipos del frontend

En `frontend/src/features/schedules/types.ts`. Reusa `Categoria` y `ErrorApi` de `features/resources/types.ts`.

```ts
import type { Categoria, ErrorApi } from "../resources/tipos";

export type DiaSemana =
  | "LUNES" | "MARTES" | "MIERCOLES" | "JUEVES" | "VIERNES" | "SABADO";

export type Verdict =
  | "OK" | "UNCHANGED" | "DISPLACES_RESERVATIONS"
  | "REJECTED_FORMAT" | "REJECTED_RESOURCE"
  | "ACADEMIC_CLASH" | "ASSET_ALREADY_PICKED_UP" | "RESOURCE_UNDER_MAINTENANCE";

export type UploadStatus = "VALIDATED" | "APPLIED" | "REJECTED" | "EXPIRED";

/** Los dictámenes que impiden aplicar la upload. */
export const BLOCKING_VERDICTS: Verdict[] = [
  "REJECTED_FORMAT", "REJECTED_RESOURCE",
  "ACADEMIC_CLASH", "ASSET_ALREADY_PICKED_UP", "RESOURCE_UNDER_MAINTENANCE",
];

export interface ReservaAfectada {
  reservationId: string;
  holder: { code: string; name: string };
  start: string;
  end: string;
  category: Categoria;
}

export interface ValidatedRow {
  row: number;
  resourceId: string;
  resourceName?: string;
  course: string;
  courseCode: string;
  group: string;
  dayOfWeek: DiaSemana;
  start: string;
  end: string;
  verdict: Verdict;
  sessions: number;
  problem?: string;
  column?: string;
  conflictingBlock?: { course: string; courseCode: string; group: string };
  affectedReservations?: ReservaAfectada[];
}

export interface ResumenValidacion {
  rowsRead: number;
  rowsOk: number;
  rowsUnchanged: number;
  rowsWithBlockingVerdict: number;
  sessionsToCreate: number;
  sessionsSkippedNonBusinessDay: number;
  blocksToWithdraw: number;
  reservationsToCancel: number;
}

export interface ValidacionResponse {
  uploadId: string;
  academicTerm: string;
  term: { start: string; end: string };
  status: UploadStatus;
  validatedAt: string;
  expiresAt?: string;
  applicable: boolean;
  requiresConfirmation: boolean;
  summary: ResumenValidacion;
  rows: ValidatedRow[];
}

export interface ConfirmacionResponse {
  uploadId: string;
  academicTerm: string;
  status: "APPLIED";
  appliedAt: string;
  appliedBy: { userId: string; name: string };
  result: {
    blocksCreated: number;
    blocksUnchanged: number;
    blocksWithdrawn: number;
    reservationsCancelled: number;
    eventsQueued: number;
  };
}

export interface ExtraordinaryNeedRequest {
  resourceId: string;
  date: string;
  start: string;
  end: string;
  course: string;
  courseCode: string;
  group: string;
  program: string;
  instructor?: string;
  confirm?: boolean;
}

export interface NecesidadExtraordinariaResponse {
  applied: boolean;
  verdict: Verdict;
  resourceId?: string;
  resourceName?: string;
  start?: string;
  end?: string;
  affectedReservations?: ReservaAfectada[];
  block?: {
    reservationId: string;
    resourceId: string;
    type: "REGULAR" | "EXTRAORDINARIO";
    start: string;
    end: string;
    course: string;
    program: string;
    instructor?: string;
  };
  reservationsCancelled?: number;
  eventsQueued?: number;
}

export interface ErrorHorario extends ErrorApi {
  code?: "EMPTY_FILE" | "INVALID_HEADER" | "INVALID_TERM"
         | "UPLOAD_NOT_APPLICABLE" | "UPLOAD_EXPIRED" | "UPLOAD_ALREADY_APPLIED" | "IMPACT_CHANGED";
  verdict?: Verdict;
  missingColumns?: string[];
  reservationsToCancelAtValidation?: number;
  reservationsToCancelNow?: number;
  expiredAt?: string;
  appliedAt?: string;
}
```

`ImportReportView.tsx` agrupa `rows` por `verdict` y muestra primero los bloqueantes, que son los que hay que corregir. `ConfirmImpact.tsx` solo se muestra cuando `requiresConfirmation` es `true`, y lista las `affectedReservations` de todas las filas juntas: eso es lo que FR-008 pide aprobar.

---

### 9. Fixtures compartidos

Se suman a los de UC1 y UC2 en la misma carpeta:

```text
src/test/resources/contratos/
├── schedule-valid.csv                      # 3 clases limpias
├── schedule-with-errors.csv                 # una fila por cada dictamen bloqueante
├── schedule-empty.csv                       # solo la cabecera
├── schedule-invalid-header.csv           # sin la columna resource_id
├── api-schedule-validation-applicable.json   # el 200 con DISPLACES_RESERVATIONS
├── api-schedule-validation-rejected.json   # el 200 con aplicable: false
├── api-schedule-confirmation.json
├── api-schedule-409-impact-changed.json
├── api-extraordinary-need-preview.json          # confirmar: false
├── api-extraordinary-need-201.json
├── module1-operational-status-batch.json      # con notFound y un EN_MANTENIMIENTO
└── event-reservation-cancelled.json           # lo que debe quedar en outbox_message.payload
```

Los CSV son también la entrada de `CsvScheduleReaderTest` (T021), así que el formato de [§5](#5-formato-del-archivo-csv) se verifica contra los mismos archivos que usan las pruebas del controlador.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Sumar a la base de UC1 y UC2 lo único que falta: leer archivos y los parámetros de la carga.

- [ ] T001 Agregar `org.apache.commons:commons-csv` a `build.gradle`
- [ ] T002 [P] Extender `application.properties` con los parámetros de este caso de uso: `reservations.upload.vigencia=30m`, `reservations.upload.max-size=2MB`, `reservations.upload.write-batch=500`, `reservations.module1.status-batch=200`, y el `spring.servlet.multipart.max-file-size` acorde
- [ ] T003 [P] Extender `ReservationProperties` con esos parámetros y validarlos al arrancar (vigencia > 0, lotes > 0)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Esquema, modelo de dominio y la parte de UC4 y UC11 que UC3 necesita.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T004 Escribir `V4__schedule_upload.sql`: tabla `semester_schedule` con `status`, `file_name`, `term_starts_at`, `term_ends_at` y `resultado jsonb`; tabla `schedule_upload_row` con la fila cruda, su dictamen y su problema; tabla `academic_block` del plan general con la columna `session_key` y su índice único; e índice por `(academic_term, estado)` para la reconciliación
- [ ] T005 [P] Crear el modelo de `domain/model/academicblock/`: `WeeklyClass`, `ClassSession`, `AcademicData`, `BlockType`, `AcademicBlock`, `SessionKey` (con el hash de periodo, recurso, franja, código de asignatura y grupo) y `SemesterSchedule`
- [ ] T006 [P] Crear el modelo de `domain/model/upload/`: `ScheduleUpload`, `UploadStatus`, `ValidatedRow`, `RowVerdict` con los ocho dictámenes, `ImportReport` e `UploadImpact`, con el método que decide si la carga es aplicable
- [ ] T007 [P] Crear `CancellationReason` en `domain/model/reservation/` y extender `Reservation` con la transición a `CANCELADA_POR_PRIORIDAD_ACADEMICA`, que no penaliza (UC4 FR-004, FR-005)
- [ ] T008 [P] Definir los puertos de salida `ScheduleUploadRepositoryPort`, `AcademicBlockRepositoryPort` y `ScheduleReader` en `domain/port/out/`, y extender `ReservationRepositoryPort` con la cancelación en lote por prioridad académica
- [ ] T009 [P] Definir los puertos de entrada `ValidateSchedulePort`, `ApplySchedulePort`, `RegisterExtraordinaryNeedPort` y `CancelReservationPort` en `domain/port/in/`
- [ ] T010 Implementar `CancelReservationUseCase` con **solo la rama académica**: cancela sin penalizar, deja la auditoría con autor, motivo y marca de tiempo, y encola el evento de cancelación (UC4 FR-002, FR-004 a FR-006, FR-009). La rama del titular, con `CAN-001` y `CAN-002`, queda para el plan de UC4
- [ ] T011 [P] Implementar `ReservationCancelledEvent` y extender `OutboxNotifierAdapter` para encolarlo en el topic de cancelación según [Contratos §7](#7-evento-de-cancelación--module2reservationcancellationv1), con `attributableToPerson` en `false` (UC11 FR-002, FR-003, FR-006)
- [ ] T012 [P] Mapear `InvalidFileException` y `UploadNotApplicableException` a `400` y `409` en `GlobalErrorHandler`, con los `code` de [Contratos §1 y §2](#1-post-apischedulesvalidations)
- [ ] T013 [P] Exigir el rol `DIRECCION_PROGRAMA` en `/api/schedules/**` en `SecurityConfig`, y comprobar que un `ESTUDIANTE` autenticado recibe `403` y no `401`

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Cargar el horario del semestre (Priority: P2)

**Goal**: La Dirección de Programa carga el archivo del semestre, ve un reporte fila por fila y, cuando hay reservas que se desplazarían, las aprueba antes de que se cancelen. Al confirmar, cada clase queda en `BLOQUEO_ACADEMICO` y ningún estudiante puede reservar esos recursos en esas franjas.

**Independent Test**: Con el perfil local, cargar `schedule-valid.csv` y comprobar en la consulta de UC1 que las franjas de clase aparecen como `BLOQUEO_ACADEMICO` y no son seleccionables; cargar `schedule-with-errors.csv` y comprobar que no se creó ni un bloqueo y que el reporte señala las cinco filas; volver a cargar el mismo archivo válido y comprobar que no se duplicó nada. No necesita que exista UC4 ni que Kafka esté levantado: los eventos quedan en la *outbox*.

### Tests for User Story 1

- [ ] T014 [P] [US1] Pruebas en `SessionExpanderTest.java`: una clase de lunes en un periodo de 16 semanas da 16 sesiones; un festivo configurado en lunes la deja en 15 y lo informa; `SABADO` genera sesiones si el sábado es hábil; un periodo que no contiene ningún día de esa clase da 0 sesiones y la fila lo dice
- [ ] T015 [P] [US1] Pruebas en `RowClassifierTest.java`: un dictamen por cada fila de la tabla, el cruce parcial de 09:00–11:00 contra una clase de 08:00–10:00 como `ACADEMIC_CLASH`, el activo apartado sin recoger como `DISPLACES_RESERVATIONS` y el ya entregado como `ASSET_ALREADY_PICKED_UP` (FR-010), y que una fila con dos problemas reporta el bloqueante
- [ ] T016 [P] [US1] Pruebas en `ValidateScheduleUseCaseTest.java`: el archivo limpio deja la carga `VALIDATED` y `aplicable: true` sin crear bloqueos ni cancelar reservas; una sola fila bloqueante deja `aplicable: false` y **cero** bloqueos (escenario 2); el reporte lista **todas** las filas con problema y no se para en la primera; el archivo vacío responde `EMPTY_FILE`; y el Módulo 1 caído da `503`
- [ ] T017 [P] [US1] Pruebas en `ScheduleReconcilerTest.java`: recargar el mismo archivo deja todas las filas en `UNCHANGED` y cero bloqueos nuevos (FR-007, SC-003); una clase nueva se crea; una clase que desapareció del archivo entra como `blocksToWithdraw` y exige confirmación; y una clase retirada y vuelta a cargar funciona, porque la `session_key` cancelada quedó en `NULL`
- [ ] T018 [P] [US1] Pruebas en `ApplyScheduleUseCaseTest.java`: la confirmación crea los bloqueos y cancela las reservas desplazadas; una carga `aplicable: false` responde `UPLOAD_NOT_APPLICABLE`; una vencida, `UPLOAD_EXPIRED`; confirmar dos veces no aplica dos veces y responde `UPLOAD_ALREADY_APPLIED`; y un impacto que creció entre validar y confirmar responde `IMPACT_CHANGED` sin cancelar nada
- [ ] T019 [P] [US1] Prueba `AcademicPriorityIT.java` con Testcontainers: la cancelación de la reserva estudiantil y la creación del bloqueo ocurren en la misma transacción y la restricción de exclusión nunca ve las dos `CONFIRMADA` (escenario 3); un fallo a mitad de la carga no deja ni un bloqueo ni una reserva cancelada (FR-008); y por cada cancelación queda un `outbox_message` que coincide con `event-reservation-cancelled.json`
- [ ] T020 [P] [US1] Prueba `ScheduleUploadPersistenceAdapterIT.java`: el índice único de `session_key` rechaza el duplicado, cancelar un bloqueo pone la clave en `NULL`, y la escritura por lotes de miles de sesiones termina dentro del presupuesto de SC-001
- [ ] T021 [P] [US1] Pruebas `CsvScheduleReaderTest.java` con los CSV de [Contratos §9](#9-fixtures-compartidos): archivo válido, campo con comas entre comillas, cabecera sin una columna obligatoria, hora mal escrita, `DOMINGO` rechazado, columna extra ignorada y archivo con solo la cabecera
- [ ] T022 [P] [US1] Prueba `ScheduleControllerTest.java` con `@WebMvcTest` y `MockMultipartFile`, comparando contra los *fixtures* `api-schedule-*.json`: el `200` aplicable y el `200` rechazado, los tres `400`, `401` sin sesión, `403` con rol `ESTUDIANTE`, `413` con un archivo grande, los cuatro `409` de la confirmación y el `503` del Módulo 1

### Implementation for User Story 1

- [ ] T023 [P] [US1] Implementar `CsvScheduleReader` con Commons CSV según [Contratos §5](#5-formato-del-archivo-csv): valida la cabecera, devuelve las filas crudas con su número real empezando en 2, y nunca lanza por una fila mal formada —el problema va en el dictamen
- [ ] T024 [US1] Implementar `SessionExpander` sobre `BusinessCalendar`: de `WeeklyClass` y el periodo a las `ClassSession`, contando las omitidas por día no hábil (depende de T005)
- [ ] T025 [US1] Implementar `RowClassifier`: una llamada por lote al Módulo 1 para los recursos distintos, una consulta por lote de cruces contra `reservation` y `loan`, y el dictamen de cada fila con su `problem` (depende de T005, T006, T024)
- [ ] T026 [US1] Implementar `ScheduleReconciler` con la `session_key`: reparte en `UNCHANGED`, nuevas y por retirar (FR-007) (depende de T025)
- [ ] T027 [US1] Implementar `ValidateScheduleUseCase`: lee, expande, clasifica, reconcilia, calcula el `UploadImpact` y guarda la carga con sus filas, **sin tocar ninguna reserva** (depende de T023 a T026)
- [ ] T028 [US1] Implementar `ApplyScheduleUseCase`: comprueba estado y vigencia, recomprueba el impacto, cancela las desplazadas por `CancelReservationPort` y crea cada sesión por `ReserveResourcesPort` con `origen = ACADEMICO`, todo en una transacción y por lotes (depende de T010, T027)
- [ ] T029 [P] [US1] Implementar `ScheduleUploadPersistenceAdapter` y `AcademicBlockPersistenceAdapter`, con la consulta de cruces por lote, la búsqueda por `session_key` y la escritura por lotes
- [ ] T030 [US1] Registrar los beans y las transacciones de UC3 en `UseCasesConfig`, con la validación en `readOnly` y la aplicación en una sola transacción de escritura (depende de T027 a T029)
- [ ] T031 [US1] Implementar `ScheduleController` con `POST /api/schedules/validations`, `POST /api/schedules/{uploadId}/confirmation`, `GET /api/schedules` y `GET /api/schedules/{uploadId}`, exactamente como los fija [Contratos §1, §2 y §4](#1-post-apischedulesvalidations), documentado con OpenAPI
- [ ] T032 [P] [US1] Frontend: copiar los tipos de [Contratos §8](#8-tipos-del-frontend) a `frontend/src/features/schedules/types.ts` y escribir `api.ts` con la subida `multipart` y el mapeo a `ErrorHorario`
- [ ] T033 [US1] Frontend: `FileUpload.tsx` (archivo, periodo académico y fechas), `ImportReportView.tsx` (tabla por fila, agrupada por dictamen y con los bloqueantes primero) y `ConfirmImpact.tsx`, que solo aparece si `requiresConfirmation` y lista todas las `affectedReservations` juntas
- [ ] T034 [US1] Frontend: `app/(direccion)/schedules/page.tsx`, que encadena validar → revisar → confirmar, y muestra el resultado, el archivo vacío, la carga expirada y el `IMPACT_CHANGED` que obliga a revalidar

**Checkpoint**: At this point, the semester upload works end to end and can be demoed on its own

---

## Phase 4: Necesidad extraordinaria (FR-005, FR-006, FR-010)

**Purpose**: La otra mitad del spec. Reusa todo lo de la Phase 3 con una sola fila, así que se entrega después sin romper nada.

- [ ] T035 [P] Pruebas en `RegisterExtraordinaryNeedUseCaseTest.java`: con `confirmar: false` no cambia nada y devuelve el impacto; con `confirm: true` cancela la reserva estudiantil y crea el bloqueo `EXTRAORDINARIO` (escenario 3); una franja con otra clase responde `ACADEMIC_CLASH` sin cancelar el bloqueo que ya estaba (escenario 4, FR-006); un activo ya entregado responde `ASSET_ALREADY_PICKED_UP` (FR-010); y una franja de 3 horas se acepta, porque el tope de 2 horas no aplica a lo académico
- [ ] T036 Implementar `RegisterExtraordinaryNeedUseCase` reusando `RowClassifier` y `CancelReservationUseCase`, con `BlockType.EXTRAORDINARIO` y `semester_schedule_id` nulo (depende de T025, T010)
- [ ] T037 Implementar `POST /api/schedules/extraordinary-needs` en `ScheduleController` según [Contratos §3](#3-post-apischedulesextraordinary-needs), con el `Location` apuntando a `/api/reservations/{id}`
- [ ] T038 Frontend: `ExtraordinaryNeedForm.tsx` y `app/(direccion)/horarios/extraordinary/page.tsx`, con la previsualización del impacto antes de confirmar

**Checkpoint**: El spec de UC3 queda cubierto de punta a punta

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Mejoras que afectan a toda la funcionalidad

- [ ] T039 [P] Verificar SC-001 con un archivo de un semestre real —del orden de 300 clases y 4500 sesiones— y comprobar que validar y aplicar terminan en menos de 5 minutos, con el Módulo 1 simulado con su latencia prometida
- [ ] T040 [P] Verificar SC-002 de punta a punta: después de una carga, la consulta de UC1 no deja seleccionable ningún recurso con clase, y UC2 responde `RES-001` al intentar reservarlo
- [ ] T041 [P] Registrar en logs cada carga con su autor, su periodo, el conteo de cada dictamen y su duración, sin nombres de estudiantes
- [ ] T042 [P] Actualizar [modelo-datos-der.md](./modelo-datos-der.md) con las columnas nuevas de `semester_schedule`, la tabla `schedule_upload_row` y la `session_key` de `academic_block`, y volver a generar el PNG
- [ ] T043 [P] Actualizar el README con cómo cargar un horario de prueba en el perfil local
- [ ] T044 Enviar a `pendientes-clarificacion.md` lo que este plan deja abierto, y llevarle a la Dirección de Programa el formato de [Contratos §5](#5-formato-del-archivo-csv) para confirmar que puede exportarlo

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: depende de que los planes de UC1 y UC2 estén hechos; sin ellos no hay esquema, seguridad, reloj, *outbox* ni `BusinessCalendar`
- **Foundational (Phase 2)**: depende de Setup - BLOCKS la user story
- **User Story 1 (Phase 3)**: depende de Foundational
- **Necesidad extraordinaria (Phase 4)**: depende de la Phase 3, porque reusa el clasificador y la cancelación
- **Polish (Phase 5)**: depende de que las Phases 3 y 4 estén completas

### Dependencias con otros casos de uso

- **UC2 `Reservar recursos`**: es la dependencia fuerte. UC3 **no inserta reservas por su cuenta**: cada sesión entra por `ReserveResourcesPort` con `origen = ACADEMICO`, que es la flecha `<<include>>` del diagrama (P-06). De ahí hereda gratis los `<<include>>` a UC7 y UC10.
- **UC1 `Consultar recursos`**: consume lo que este plan produce. Los bloqueos aparecen como `BLOQUEO_ACADEMICO`, que es el estado de mayor prioridad después del mantenimiento, sin tocar nada de UC1.
- **UC4 `Cancelar reserva`**: este plan implementa **solo** la rama de prioridad académica (UC4 FR-002, FR-004 a FR-006, FR-009). Su plan añadirá la cancelación del titular con `CAN-001` y `CAN-002`, la antelación de 10 minutos, la prohibición de cancelar un activo ya entregado (UC4 FR-008) y la cancelación por recurso no disponible (UC4 FR-010).
- **UC11 `Reportar cancelación de reserva`**: este plan monta el evento y lo encola. Su plan añadirá `antelacionMinutos` para la cancelación del titular (UC11 FR-004) y el historial de envíos (UC11 FR-010).
- **UC7 `Actualizar estado de los recursos`**: entra por UC2 y hace lo que le toca, dejar escrita la ocupación. Crear el bloqueo no le avisa nada al Módulo 1, porque `BLOQUEO_ACADEMICO` es nuestro (UC7 FR-012); lo que sí sale es el `EN_USO` de cada clase **cuando llega su hora de inicio**, y de eso se encarga la tarea de [UC7](./plan-uc7-actualizar-estado-recursos.md), no este plan.
- **UC10 `Reportar información de la reserva`**: la ficha de cada bloqueo sale por la *outbox* de UC2, marcada con `origen: ACADEMICO` y `sancionable: false`, que es la forma ya definida en [UC2 § Contratos §7](./plan-uc2-reservar-recursos.md#6-evento-hacia-el-módulo-3--module2reservationrecordv1).
- **UC9 `Recibir reporte de no asistencia`**: no se toca aquí, pero este plan crea la situación que P-19 señala —nada impide hoy que el Módulo 3 reporte una ausencia sobre una clase—. El rechazo le toca a UC9.

### Within User Story 1

- Esquema y modelo (T004 a T007) → puertos (T008, T009) → `CancelReservationUseCase` (T010)
- Lector (T023) → expansor (T024) → clasificador (T025) → reconciliador (T026) → validar (T027) → aplicar (T028)
- Los adaptadores de persistencia (T029) solo dependen de los puertos, así que van en paralelo con los casos de uso
- Beans (T030) → controlador (T031) → frontend (T032 a T034)

### Parallel Opportunities

- En Setup: T002 y T003
- En Foundational: T005 a T009, T011, T012 y T013
- En User Story 1: todas las pruebas (T014 a T022), el lector (T023) y los adaptadores (T029)
- En Polish: T039 a T043

## Notes

- La numeración `T0XX` es propia de este plan y empieza de nuevo; no continúa la de UC1 ni la de UC2
- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Verify tests pass
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- La sección **Contratos** es la única fuente del JSON y del CSV de UC3: si algo cambia ahí, cambia en los *fixtures*, en el OpenAPI y en los tipos del frontend, no al revés
- **Decisiones de los contratos que el spec no fija**: el archivo trae clases semanales y el sistema las expande a sesiones; la carga va en dos peticiones, validar y confirmar, con una vigencia de 30 minutos; un choque bloquea la carga entera igual que un error de formato; `IMPACT_CHANGED` rechaza la confirmación en vez de aplicar lo que nadie aprobó; el formato es CSV y no `.xlsx`; y el reporte de validación es la única respuesta del módulo que nombra al titular de una reserva, porque FR-008 exige mostrar cuáles se cancelarían y solo Dirección de Programa la ve
