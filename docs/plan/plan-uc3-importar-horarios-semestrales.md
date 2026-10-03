# Implementation Plan: Importar horarios semestrales (UC3)

**Date**: 2026-10-02
**Spec**: [spec-modulo2-uc3-importar-horarios-semestrales.md](../specs/spec-modulo2-uc3-importar-horarios-semestrales.md)
**Plan general**: [plan-arquitectura.md](./plan-arquitectura.md)
**Planes previos**: [plan-uc1-consultar-recursos.md](./plan-uc1-consultar-recursos.md) — base compartida y catálogo del Módulo 1; [plan-uc2-reservar-recursos.md](./plan-uc2-reservar-recursos.md) — reserva con `origen = ACADEMICO`, *outbox* y evento de la ficha

## Summary

UC3 es el que le dice al sistema qué le pertenece a la actividad docente. Dirección de Programa sube el archivo del semestre, el sistema crea un `BLOQUEO_ACADEMICO` por cada sesión de clase y, desde ese momento, ningún estudiante puede apartar esos salones en esas franjas (FR-002). También registra **necesidades extraordinarias** —un examen adicional, una jornada institucional— que se imponen sobre las reservas que ya estén confirmadas (FR-004, FR-005).

**Enfoque técnico:**

1. La carga es de **dos fases obligatorias** porque FR-008 lo pide así: primero un **análisis** que lee el archivo entero y no escribe ni un bloqueo, y después una **aplicación** que solo se dispara cuando la Dirección de Programa confirma. Entre una y otra, el conjunto de reservas a desplazar es el mismo que se mostró.
2. El archivo es **CSV** (UTF-8, una fila por sesión de clase con fecha concreta) y el sistema publica una **plantilla** (`GET /api/horarios/plantilla`) para que no haya que adivinar el formato. El parser vive en un adaptador; el dominio solo ve sesiones ya normalizadas.
3. El análisis pide el **catálogo del Módulo 1 una sola vez** y lo indexa por identificador, para validar las 400 filas sin 400 llamadas HTTP. Si el catálogo viene truncado (`catalogoTruncado`), el análisis **falla**: no se puede dar motivo del 100 % de las filas con un catálogo incompleto (FR-003, SC-001).
4. Cada fila cae en una de cinco categorías: `APLICABLE`, `YA_CARGADA`, `DESPLAZAMIENTO`, `CONFLICTO` o `RECHAZO`. Los **errores de datos abortan la carga entera** (escenario 2: no se carga nada) y los **choques no** (FR-006, FR-010 y el edge case del recurso en mantenimiento): se excluyen, se reportan y los resuelven las personas responsables.
5. La aplicación se hace **por lote y en una sola transacción**: N bloqueos, M cancelaciones y sus N+M eventos en la *outbox*, todo o nada (edge case **Carga tardía**). El `<<include>>` a `Reservar recursos` se cumple reusando su camino de dominio y de persistencia con `origen = ACADEMICO` —saltando sanción, cupo y el tope de 2 horas (P-19)—, no con 400 invocaciones por fila.
6. **FR-007** se resuelve con una `clave` natural única en `bloqueo_academico` y `ON CONFLICT DO UPDATE`: volver a cargar el mismo horario **actualiza** lo que existe y deja cero bloqueos repetidos (SC-003). Y el `sha256` del archivo evita subir dos veces lo mismo.
7. Cada desplazamiento emite **un `ReservaCancelada` por reserva** en `modulo2.reserva.cancelacion.v1` (UC11 FR-001, y su edge case de cancelación masiva), marcado como **no responsabilidad del estudiante** (UC11 FR-006, SC-004).

Este plan implementa además la parte de **UC4 `Cancelar reserva`** que UC3 necesita —la cancelación automática por prioridad académica (FR-004, FR-005)— y el **`<<include>>` a UC11 `Reportar cancelación de reserva`**, incluido el contrato del evento, que ningún plan anterior tenía. Su propio plan completa la cancelación del titular.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript con Next.js y React (frontend)
**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Validation, RestClient), **Apache Commons CSV** (`commons-csv`, nuevo en este plan), spring-kafka y Flyway ya los dejó el plan de UC2
**Storage**: PostgreSQL con `btree_gist`. UC3 **escribe** `horario_semestral`, `bloqueo_academico`, `reserva` (origen `ACADEMICO`), el estado de las reservas desplazadas y `mensaje_saliente`; lee `reserva` y `prestamo` para detectar cruces.
**Testing**: JUnit 5 y AssertJ (dominio y casos de uso con puertos falsos), `@WebMvcTest` (controlador), Testcontainers con PostgreSQL (aplicación atómica, idempotencia, concurrencia) y con Kafka (los eventos de la *outbox*), WireMock (Módulo 1), ArchUnit
**Target Platform**: Servidor Linux con JVM 21; navegador web para el frontend
**Project Type**: Web: backend en la raíz del repositorio y frontend en `frontend/`
**Performance Goals**: Un semestre completo (miles de sesiones) se analiza y se aplica en **menos de 5 minutos** (SC-001). El presupuesto es casi todo de la base: una sola travesía del catálogo del Módulo 1 y una única transacción de escritura. No hay ninguna llamada HTTP dentro de la fase de aplicación.
**Constraints**:
- Con rechazos, **no se carga nada** (escenario 2). Con choques, se carga el resto y se reporta (FR-006, FR-010).
- La **atomicidad es total**: los bloqueos, las cancelaciones y los eventos se escriben en la misma transacción, o no se escribe nada (FR-008).
- Una franja de clase puede ser de **más de 2 horas**: el tope de `Reservar recursos` (FR-009) no aplica al origen académico (P-19).
- Un bloqueo académico **nunca cancela** otro bloqueo académico (FR-006) ni un préstamo ya entregado (FR-010).
- Zona horaria `America/Bogota`, ventana de 06:00 a 22:00 y sin cruzar la medianoche, igual que en UC1 y UC2.
- Solo `DIRECCION_PROGRAMA` entra a estos endpoints; a los estudiantes nunca se les nombra el titular de una reserva (UC8 FR-005).
**Scale/Scope**: Un semestre completo son cientos o miles de filas y desplazamientos de decenas de reservas; el archivo se procesa entero en memoria, con un tope configurable. Una pantalla nueva en el frontend, en el grupo `(direccion)`.

## Project Structure

### Documentation (this feature)

```text
docs/
├── specs/
│   ├── spec-modulo2-uc3-importar-horarios-semestrales.md   # spec de este plan
│   ├── spec-modulo2-uc2-reservar-recursos.md               # <<include>>: origen ACADEMICO
│   ├── spec-modulo2-uc4-cancelar-reserva.md                # <<include>>: cancelación automática
│   ├── spec-modulo2-uc7-actualizar-estado-recursos.md      # <<include>>: deja la ocupación escrita
│   ├── spec-modulo2-uc10-reportar-informacion-reserva.md   # <<include>>: ficha académica
│   └── spec-modulo2-uc11-reportar-cancelacion-reserva.md   # <<include>>: cancelación desplazada
└── plan/
    ├── plan-arquitectura.md                  # plan general: capas, carpetas, base de datos y Kafka
    ├── plan-uc1-consultar-recursos.md        # catálogo del Módulo 1 y OccupacionRepositoryPort
    ├── plan-uc2-reservar-recursos.md         # origen ACADEMICO, outbox y evento de la ficha
    └── plan-uc3-importar-horarios-semestrales.md   # este archivo
```

### Source Code (repository root)

Solo los archivos que crea o toca este plan; los que ya existen vienen de UC1 y UC2 y van marcados. La organización completa está en [plan-arquitectura.md](./plan-arquitectura.md#organización-de-carpetas).

```text
build.gradle                                        # + commons-csv (T001)

src/main/java/edu/unimagdalena/reservasunimag/
├── domain/
│   │   ├── model/
│   │   │   ├── reserva/
│   │   │   │   └── FranjaHoraria.java              # (existe) − el tope de 2 h sale de aquí (P-19)
│   │   │   └── bloqueo/                            # nueva carpeta
│   │   │       ├── TipoBloqueo.java                # REGULAR, EXTRAORDINARIO
│   │   │       ├── DatosAcademicos.java            # asignatura, programa, docente
│   │   │       ├── SesionDeClase.java              # fila ya normalizada + su clave natural
│   │   │       ├── Hallazgo.java                   # ERROR | CONFLICTO | DESPLAZAMIENTO | APLICABLE | YA_CARGADA
│   │   │       ├── ResultadoAnalisis.java          # el reporte completo de una carga
│   │   │       └── HorarioSemestral.java           # carga del periodo + su resultado jsonb
│   │   ├── model/error/
│   │   │   ├── ImportacionInvalidaException.java  # 400: archivo que no se puede leer
│   │   │   └── CargaNoAplicableException.java     # 409: hay rechazos o el estado no permite aplicar
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   ├── ImportarHorariosPort.java       # analizar, confirmar, descartar, listar
│   │   │   │   ├── RegistrarNecesidadExtraordinariaPort.java
│   │   │   │   ├── CancelarReservaPort.java        # (nueva) solo la parte automática de UC4
│   │   │   │   └── ReportarCancelacionPort.java    # UC11 (nueva)
│   │   │   └── out/
│   │   │       ├── HorarioRepositoryPort.java      # horario_semestral y bloqueo_academico
│   │   │       ├── LectorArchivoPort.java          # CSV fuera del dominio
│   │   │       ├── OcupacionRepositoryPort.java    # (existe) + ocupacionesQueSeCruzan(...)
│   │   │       └── NotificadorModulo3Port.java     # (existe) + la cancelación
│   │   └── usecase/
│   │       ├── importarhorarios/
│   │       │   ├── ImportarHorariosUseCase.java    # analizar y confirmar
│   │       │   ├── AnalizadorCarga.java            # dominio puro: filas + catálogo + cruces → reporte
│   │       │   ├── ValidadorFila.java               # un rechazo por fila inválida, con su código
│   │       │   └── AnalizadorNecesidadExtraordinaria.java
│   │       ├── cancelareserva/
│   │       │   └── CancelarPorPrioridadAcademicaUseCase.java
│   │       └── reportarcancelacion/
│   │           └── ReportarCancelacionUseCase.java
│   └── infrastructure/
│       ├── adapter/
│       │   ├── in/web/
│       │   │   ├── horario/
│       │   │   │   ├── HorarioController.java      # /api/horarios/**
│       │   │   │   ├── ImportarHorarioRequest.java
│       │   │   │   ├── NecesidadExtraordinariaRequest.java
│       │   │   │   ├── ReporteImportacionResponse.java
│       │   │   │   └── DetalleFilasResponse.java
│       │   │   └── error/
│       │   │       └── ManejadorGlobalErrores.java # (existe) + 400, 409 y 413
│       │   └── out/
│       │       ├── persistence/
│       │       │   ├── entity/                     # + HorarioSemestralJpa, BloqueoAcademicoJpa
│       │       │   ├── repository/                 # + HorarioSemestralJpaRepository,
│       │       │   │                               #   BloqueoAcademicoJpaRepository
│       │       │   └── HorarioPersistenceAdapter.java  # la transacción de aplicación
│       │       └── archivo/
│       │           ├── FilaArchivo.java            # número de fila + celdas crudas
│           └── LectorCsvHorario.java       # → LectorArchivoPort
│       └── config/
│           ├── CasosDeUsoConfig.java               # (existe) + los beans de UC3
│           └── PropiedadesReservas.java            # (existe) + topes de importación
│
src/main/resources/
├── application.properties                          # + multipart y topes de UC3
└── db/migration/
    └── V4__horario_semestral_y_bloqueo.sql         # columnas nuevas + índice único de clave

src/test/java/edu/unimagdalena/reservasunimag/
├── domain/model/bloqueo/SesionDeClaseTest.java
├── domain/model/reserva/FranjaHorariaTest.java     # (existe) − el tope de 2 h
├── domain/usecase/importarhorarios/
│   ├── ValidadorFilaTest.java
│   ├── AnalizadorCargaTest.java
│   ├── ImportarHorariosUseCaseTest.java
│   └── NecesidadExtraordinariaUseCaseTest.java
├── domain/usecase/cancelareserva/CancelarPorPrioridadAcademicaUseCaseTest.java
└── infrastructure/adapter/
    ├── in/out/archivo/LectorCsvHorarioTest.java
    ├── in/web/horario/HorarioControllerTest.java
    ├── out/persistence/AplicarCargaIT.java
    ├── out/persistence/ImportarConcurrenciaIT.java
    └── out/mensajeria/PublicadorOutboxIT.java     # (existe) + el evento de cancelación

src/test/resources/contratos/                       # + los JSON de la sección Contratos (T023)

frontend/src/
├── app/(direccion)/horarios/page.tsx               # pantalla de carga y seguimiento
└── features/horarios/
    ├── CargarHorario.tsx
    ├── ReporteImportacion.tsx
    ├── DetalleFilas.tsx
    ├── ConfirmarCarga.tsx
    ├── ListaImportaciones.tsx
    ├── NecesidadExtraordinaria.tsx
    ├── api.ts
    └── tipos.ts
```

**Structure Decision**: se mantiene la estructura del plan general y de los planes de UC1 y UC2. Tres añadidos: la carpeta de dominio `model/bloqueo`, que el plan general ya nombraba y que ahora nace con UC3; la carpeta `infrastructure/adapter/out/archivo`, porque leer un CSV es tecnología y el dominio solo debe ver sesiones ya normalizadas (por eso el parser se esconde detrás de `LectorArchivoPort`); y el grupo `(direccion)` del frontend, que el plan general reserva para esta pantalla. Los otros dos planes de la serie están escritos; cuando los completen, este archivo no se reescribe.

### Decisiones de diseño de este caso de uso

**El archivo es CSV y hay una fila por sesión con fecha concreta.** No se acepta XLSX: nadie nos dio un archivo real del Sistema Académico, CSV se lee con una dependencia ligera y cualquier hoja de cálculo lo exporta. La granularidad por sesión —y no una clase recurrente de día de semana— evita tener que adivinar el calendario de periodos ni el de días en que la universidad no abre, que en UC2 (FR-014) es una lista a mano. La plantilla se publica en `GET /api/horarios/plantilla`:

```csv
codigoSesion,recursoId,fecha,inicio,fin,asignatura,programa,docente
2026-2-RED-01-01,ESP-0412,2026-08-04,08:00,10:00,Redes de Computadores,Ingeniería de Sistemas,Nombre del docente
```

**`codigoSesion` es opcional y es lo que hace posible FR-007.** Con él, la identidad de la clase es ese código y una clase que cambia de salón o de hora **se actualiza** en el mismo bloqueo, que es literalmente lo que pide el edge case *Cambio de horario a mitad de semestre*. Sin él, la identidad es la clave natural `recursoId|inicio|fin|asignatura|programa`: una clase que se mueve queda como clase nueva y la anterior se reporta como `RETIRADA` en el reporte, para que nadie la borre a mano. (NEEDS CLARIFICATION: si el Sistema Académico tiene un identificador de sesión, hay que pedirlo y volverlo obligatorio; de eso depende que "actualizar lo que existe" sea de verdad una actualización.)

**Cómo se clasifica cada fila.** Es la tabla que sostiene todo el caso de uso:

| Categoría | Qué es | ¿Aborta la carga? | Qué hace el sistema |
|---|---|---|---|
| `RECHAZO` | Error de datos: el recurso no existe en el Módulo 1, la hora no es `HH:mm`, `fin <= inicio`, la franja cae fuera de 06:00–22:00, falta un campo obligatorio, la fecha no es válida, la fila está repetida dentro del archivo | **Sí**, si hay una sola (escenario 2) | No se carga nada y se devuelve el reporte fila por fila |
| `CONFLICTO` | El dato es válido pero choca con algo que el sistema **no** puede tocar: ya hay un `BLOQUEO_ACADEMICO` (FR-006), el activo ya fue entregado (FR-010), el recurso está `EN_MANTENIMIENTO` | No | La fila se excluye, se reporta y se pide que lo resuelvan las personas responsables |
| `DESPLAZAMIENTO` | Choca con una reserva `ESTUDIANTIL` `CONFIRMADA` o con un préstamo **sin recoger** (FR-005, FR-010) | No | Se cancela al aplicar, con `CANCELADA_POR_PRIORIDAD_ACADEMICA`, y se reporta su cancelación al Módulo 3 |
| `YA_CARGADA` | La misma sesión ya está bloqueada y no cambió | No | No se duplica (FR-007); cuenta como aplicada sin escribir nada |
| `APLICABLE` | Se crea un bloqueo nuevo | No | Se inserta la reserva `ACADEMICO` y su detalle `bloqueo_academico` |

La distinción entre `RECHAZO` y `CONFLICTO` es la lectura del spec que este plan adopta: el escenario 2 aborta la carga por datos malos, y FR-006 y FR-010 piden explícitamente que el sistema **avise del choque** y no lo resuelva por su cuenta. Un choque no es un error del archivo, es un problema de mundo físico que nada en el archivo arregla.

**El `<<include>>` a `Reservar recursos`, por lote.** P-06 decidió que cada clase importada pasa por reservar, y UC2 dejó `SolicitudDeReserva.origen` listo para eso. Llamar a `ReservarRecursosUseCase` 400 veces por fila traería 400 viajes al Módulo 1 y 400 transacciones, y SC-001 es de 5 minutos para el semestre entero. La lectura que adopta este plan es la misma que ya usaron los planes anteriores con otros `<<include>>`: **se reusa el camino, no la llamada**. La aplicación usa la fábrica de dominio que crea la reserva `ACADEMICO`, el mismo `ReservaRepositoryPort` que garantiza que no haya cruces, el mismo `bloqueo_academico` como detalle y el mismo evento de la ficha en la *outbox`. Lo que no se reusa son las reglas de estudiante, porque no aplican: sin sanción (no hay titular), sin cupo (no hay titular) y sin el tope de 2 horas (P-19).

**El tope de 2 horas sale de `FranjaHoraria`.** El plan de UC2 lo metió dentro de `FranjaHoraria` (T008 de UC2). Eso estorba aquí: una clase de 3 horas es normal y debe entrar. La corrección es que el tope es una **regla de la reserva**, no de la franja, así que se mueve a `ReservarRecursosUseCase` y se aplica solo con `origen = ESTUDIANTIL`. `FranjaHoraria` se queda con la ventana, el mismo día y `inicio < fin`. Es un cambio en código de UC2 y por eso lleva su propia prueba de regresión (T012).

**Dónde empieza y acaba la transacción.** Igual que en UC2, ninguna llamada HTTP dentro de la transacción: el catálogo del Módulo 1 se pide entero en la fase de análisis y la fase de aplicación solo toca PostgreSQL. La transacción de aplicación la abre un método del adaptador de persistencia:

1. `SELECT ... FOR UPDATE` de los recursos y de las reservas que se van a desplazar, ordenados por identificador para no provocar deadlocks entre dos cargas simultáneas.
2. **Revalidación**: se vuelven a leer las ocupaciones de las filas aplicables y la lista de reservas a cancelar, y se compara con lo que el análisis guardó. Si el conjunto cambió, la transacción aborta con `409 ESTADO_CAMBIADO` y un diff: aplicar cancelaría algo que la Dirección de Programa no vio, y FR-008 manda sobre eso.
3. `INSERT` masivo de `reserva` (`origen = 'ACADEMICO'`, `usuario_id` nulo) y de `bloqueo_academico`, con `ON CONFLICT (clave) DO UPDATE` para las que ya existían.
4. `UPDATE` de las reservas desplazadas a `CANCELADA_POR_PRIORIDAD_ACADEMICA` con su motivo y su fecha.
5. `INSERT` masivo en `mensaje_saliente`: una `FICHA_RESERVA` por bloqueo nuevo o actualizado (UC10 FR-008) y una `CANCELACION` por reserva desplazada (UC11 FR-001).
6. `UPDATE horario_semestral SET estado = 'APLICADA'` con el reporte final.

La restricción de exclusión `reserva_sin_cruces` sigue siendo la red de seguridad: si algo se coló entre el análisis y la aplicación, el `INSERT` revienta y no queda nada a medias. Además, `bloqueo_academico.clave` es única y cubre FR-007 y SC-003 sin depender del análisis.

**Cómo se detecta el FR-010 de los activos.** Un `ACTIVO` prestado no se libera borrando un registro, así que el cruce se mira contra el **periodo de préstamo completo**, no contra la franja:

```sql
SELECT r.id, r.recurso_id, r.categoria_recurso, r.inicio, r.fin,
       p.entregado_en, p.devuelto_en
FROM reserva r
LEFT JOIN prestamo p ON p.reserva_id = r.id
WHERE r.recurso_id = ANY(:recursoIds)
  AND r.estado = 'CONFIRMADA'
  AND ( r.ocupacion && tstzrange(:inicio, :fin, '[)')
      OR (p.entregado_en IS NOT NULL AND p.devuelto_en IS NULL AND r.inicio < :fin) );
```

De ahí salen las dos ramas de FR-010: con `devuelto_en IS NULL` y `entregado_en IS NULL` el préstamo está apartado y **no recogido**, así que se cancela y el activo queda libre para la clase; con `entregado_en` puesta, el activo está en manos de alguien y el cruce es un `CONFLICTO` que el sistema no resuelve. El bloqueo académico de un activo ocupa **la franja exacta de la clase** en `reserva`: no es un préstamo y no genera fila en `prestamo`.

**Estado del Módulo 1 y de `EN_MANTENIMIENTO`.** Solo `EN_MANTENIMIENTO` es un conflicto. Un recurso que ahora mismo está `EN_USO` se puede bloquear para una clase de la próxima semana: el estado operativo es de "ahora" y la clase es de "entonces". Como P-20.3 sigue abierto (si el mantenimiento trae fechas o no), el análisis trata `EN_MANTENIMIENTO` como conflicto **para cualquier fecha futura**, que es la lectura conservadora.

**Quién avisa al estudiante desplazado.** P-20.5 sigue abierto: con el reparto de estados, al Módulo 1 ya no se le mandan cancelaciones. Lo que este plan hace es dejar la constancia que el sistema sí tiene: la reserva queda `CANCELADA_POR_PRIORIDAD_ACADEMICA` con su motivo y se ve en `mis-reservas` de UC2, y sale un `ReservaCancelada` al Módulo 3 por reserva. El **canal** por el que el estudiante se entera no es del Módulo 2; eso se decide cuando se responda P-20.5 y no bloquea la carga.

**Quién puede hacerlo.** Solo `DIRECCION_PROGRAMA`. Es el único rol que ve la lista de reservas a desplazar, con titular incluido, porque FR-008 pide saber *cuáles* y *quién*: es la información que necesita para avisar. Un `ESTUDIANTE` o un `MONITOR` reciben `403` en todos los endpoints de esta pantalla.

**Auditoría (FR-009).** `horario_semestral` guarda el archivo (nombre, `sha256`, contenido), quién lo subió (`cargado_por`), cuándo, y el reporte completo en `resultado`. `bloqueo_academico` hereda de `reserva` el `origen` y guarda `horario_semestral_id`, así que cada bloqueo sabe de qué carga salió. No se agrega columna de autor a `bloqueo_academico`: el autor de un bloqueo extraordinario es la Dirección de Programa y sale de `horario_semestral.cargado_por` a través de la carga.

## Contratos

Aquí queda definido todo el JSON de UC3: los endpoints que consume el frontend, el reporte de importación, lo que se le pide al Módulo 1, el evento de cancelación nuevo y el archivo de ejemplo. Los ejemplos son el contrato: las pruebas de T021 y T022 se escriben contra ellos y los mismos archivos viven como *fixtures* en `src/test/resources/contratos/`.

Se aplican sin repetirlas las **convenciones comunes** de [plan-uc1-consultar-recursos.md § Contratos](./plan-uc1-consultar-recursos.md#contratos): `camelCase`, enumeraciones en mayúsculas, **nunca `null`** (lo que no aplica se omite), `fecha` como `yyyy-MM-dd` y horas como `HH:mm` en `America/Bogota`, instantes ISO-8601 con `-05:00`, franjas `[inicio, fin)`, `recursoId` como cadena y errores RFC 9457 con `type` bajo `https://reservasunimag.unimagdalena.edu.co/errores/`.

Cuatro reglas propias de este caso de uso:

| Regla | Detalle |
|---|---|
| El archivo manda, el sistema responde | El CSV **no** lleva `categoria` ni `estadoOperativo`: los decide el Módulo 1 y nadie puede escribir un salón en el archivo (la misma decisión que `POST /api/reservas` de UC2). |
| Análisis y aplicación son recursos distintos | El análisis devuelve un `importacionId`; aplicar es otro endpoint sobre ese id. Nunca hay un `POST ...?confirmar=true`. |
| Un rechazo aborta, un choque no | `puedeAplicar` es `false` **solo** si hay `RECHAZO`. Los choques se aplican alrededor. |
| Las respuestas del listado no traen `detalle` | El detalle son cientos de filas; el resumen y los tres listados siempre, el detalle página a página. |

---

### 1. `POST /api/horarios/importaciones` — frontend → Módulo 2

Es la fase de análisis. **No escribe ni un bloqueo**: solo guarda una fila de `horario_semestral` en estado `ANALIZADA` con el reporte, para que exista algo que confirmar después.

**Petición** (`multipart/form-data`)

| Campo | Tipo | Obligatorio | Validación |
|---|---|---|---|
| `archivo` | archivo | sí | CSV, UTF-8, con cabecera; hasta `reservas.importacion.max-bytes` |
| `periodoAcademico` | cadena | sí | `AAAA-T`, por ejemplo `2026-2` |

**`201 Created`** — carga limpia, lista para aplicar (401 filas, 7 desplazamientos)

```http
HTTP/1.1 201 Created
Location: /api/horarios/importaciones/b1f0c7a4-2d51-4f8e-9a03-6c2e5b7d1e90
```

```json
{
  "importacionId": "b1f0c7a4-2d51-4f8e-9a03-6c2e5b7d1e90",
  "periodoAcademico": "2026-2",
  "origenCarga": "SEMESTRE",
  "estado": "ANALIZADA",
  "archivo": { "nombre": "horario-2026-2.csv", "sha256": "9f2c1ab4", "bytes": 42180, "filasDatos": 401 },
  "analizadoEn": "2026-09-01T09:14:22-05:00",
  "resumen": {
    "filasLeidas": 401,
    "aplicables": 394,
    "yaCargadas": 0,
    "desplazamientos": 7,
    "conflictos": 0,
    "rechazos": 0
  },
  "puedeAplicar": true,
  "mensaje": "La carga se puede aplicar: se crearán 394 bloqueos y se cancelarán 7 reservas de estudiantes.",
  "rechazos": [],
  "conflictos": [],
  "desplazamientos": [
    {
      "fila": 88,
      "recursoId": "ESP-0107",
      "nombre": "Sala de Estudio 3",
      "categoria": "ESPACIO",
      "reservaId": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
      "inicio": "2026-09-10T14:00:00-05:00",
      "fin": "2026-09-10T16:00:00-05:00",
      "titular": { "usuarioId": "5f1b9c2d-7a34-4e81-b0f6-3c8d1e9a4b72", "codigo": "2019114045", "nombre": "Camilo Cerpa" },
      "motivo": "La clase Redes de Computadores ocupa esa franja."
    }
  ],
  "detalle": { "pagina": 1, "tamanoPagina": 50, "total": 401, "totalPaginas": 9, "filas": [] }
}
```

**Decisión: `detalle.filas` viene vacío en el ejemplo.** El detalle siempre viaja en su propia llamada paginada (`GET .../detalle`), nunca en la respuesta del análisis: con un semestre de miles de filas, el `POST` respondería con un JSON que nadie va a leer de una sola vez. El sobre sí trae los tres listados —`rechazos`, `conflictos` y `desplazamientos`— porque son los que la Dirección de Programa tiene que revisar antes de confirmar, y siempre son cortos frente al total.

**Campos del sobre**

| Campo | Tipo | Nota |
|---|---|---|
| `importacionId` | uuid | Con este id se confirma o se descarta. |
| `periodoAcademico` | cadena | Lo que envió la persona. No se valida contra un calendario de periodos: nadie nos dio uno (NEEDS CLARIFICATION). |
| `origenCarga` | `SEMESTRE` \| `EXTRAORDINARIA` | Las necesidades extraordinarias son cargas de un solo ítem y se guardan en la misma tabla para poder previsualizarlas igual (decisión de este plan, ver §3). |
| `estado` | enumeración | `ANALIZADA`, `APLICADA` o `DESCARTADA`. |
| `archivo.sha256` | cadena | Primeros 8 caracteres del hex, para comparar rápido; el completo va en la base. Es lo que hace idempotente la subida. |
| `resumen` | objeto | Las cinco cuentas. `filasLeidas` siempre es el número de filas de datos, sin importar el resultado de cada una. |
| `puedeAplicar` | booleano | `false` **solo** si hay `RECHAZO`. Con choques se puede aplicar. |
| `mensaje` | cadena | Siempre. Es lo que se lee sin mirar nada más. |
| `rechazos`, `conflictos`, `desplazamientos` | arreglos | Siempre presentes; vacíos cuando no hay. |
| `detalle` | objeto | La paginación del detalle; `filas` viene vacío aquí y se pide en §2. |

**`201 Created`** — el archivo trae errores: no se carga nada (escenario 2)

```json
{
  "importacionId": "c4d8e210-6b3f-4a52-8d71-1f9b0c4e7a33",
  "periodoAcademico": "2026-2",
  "origenCarga": "SEMESTRE",
  "estado": "ANALIZADA",
  "archivo": { "nombre": "horario-2026-2-corregido.csv", "sha256": "5b71e0c9", "bytes": 42310, "filasDatos": 412 },
  "analizadoEn": "2026-09-01T10:02:10-05:00",
  "resumen": { "filasLeidas": 412, "aplicables": 400, "yaCargadas": 0, "desplazamientos": 7, "conflictos": 4, "rechazos": 1 },
  "puedeAplicar": false,
  "mensaje": "No se puede aplicar: 1 fila tiene errores. Corrige el archivo y vuelve a subirlo. No se cargó nada.",
  "rechazos": [
    {
      "fila": 137,
      "codigo": "HORA_INVALIDA",
      "campo": "inicio",
      "valor": "25:00",
      "recursoId": "ESP-0203",
      "mensaje": "25:00 no es una hora válida. Se espera HH:mm, por ejemplo 08:00."
    }
  ],
  "conflictos": [],
  "desplazamientos": [],
  "detalle": { "pagina": 1, "tamanoPagina": 50, "total": 412, "totalPaginas": 9, "filas": [] }
}
```

Los códigos de `RECHAZO`

| `codigo` | Qué lo causa |
|---|---|
| `CABECERA_INVALIDA` | Faltan columnas obligatorias o hay columnas que no se reconocen. Es el único rechazo que puede repetirse en 400 filas. |
| `CAMPO_OBLIGATORIO_AUSENTE` | `recursoId`, `fecha`, `inicio`, `fin`, `asignatura`, `programa` o `docente` vacíos. |
| `FECHA_INVALIDA` | La fecha no es `yyyy-MM-dd` o no es una fecha real. |
| `HORA_INVALIDA` | La hora no es `HH:mm` o no está entre `00:00` y `23:59`. |
| `FIN_NO_POSTERIOR_A_INICIO` | `fin <= inicio`. |
| `FRANJA_FUERA_DE_VENTANA` | La clase no cabe entre las 06:00 y las 22:00 (o cruza la medianoche). |
| `RECURSO_NO_ENCONTRADO` | El `recursoId` no existe en el catálogo del Módulo 1. |
| `TIPO_RECURSO_NO_SOPORTADO` | El recurso existe pero su categoría no es `ESPACIO` ni `ACTIVO`. |
| `FILA_DUPLICADA` | La misma sesión aparece dos veces dentro del archivo. |

Los códigos de `CONFLICTO`

| `codigo` | Qué lo causa | Por qué no se resuelve solo |
|---|---|---|
| `BLOQUEO_ACADEMICO_EXISTENTE` | Ya hay un bloqueo en ese recurso y esa franja. | FR-006: es una clase contra otra clase; la que estaba gana y las personas responsables deciden. |
| `ACTIVO_ENTREGADO_Y_SIN_DEVOLVER` | El activo está en manos de una persona. | FR-010: hay que pedirlo de vuelta, y eso no lo puede hacer el sistema. |
| `RECURSO_EN_MANTENIMIENTO` | El Módulo 1 lo reporta así. | El edge case **Recurso dado de baja**: esa clase necesita otro espacio y eso es una decisión académica. |
| `CHOQUE_ENTRE_FILAS_DEL_ARCHIVO` | Dos filas del mismo archivo se cruzan en el mismo recurso. | Es un error del archivo, pero no de forma: se reporta y las personas deciden cuál entra. |

**Errores**

`400` — el archivo no se puede ni leer

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errores/importacion-invalida",
  "title": "Archivo de horarios inválido",
  "status": 400,
  "detail": "El archivo no es un CSV válido: la cabecera debe ser exactamente codigoSesion,recursoId,fecha,inicio,fin,asignatura,programa,docente.",
  "instance": "/api/horarios/importaciones",
  "codigo": "CABECERA_INVALIDA",
  "esperado": "codigoSesion,recursoId,fecha,inicio,fin,asignatura,programa,docente",
  "recibido": "codigo,recurso,sala,hora_inicio,hora_fin"
}
```

`403`, `401`, `413` y `503`

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errores/horarios-no-autorizado",
  "title": "Acción reservada a Dirección de Programa",
  "status": 403,
  "detail": "Solo Dirección de Programa puede cargar el horario del semestre."
}
```

| Situación | Respuesta |
|---|---|
| Sin sesión, o cookie vencida | `401` con el `ProblemDetail` de `no-autenticado` de UC1 |
| Rol distinto de `DIRECCION_PROGRAMA` | `403` |
| El archivo supera `reservas.importacion.max-bytes` o `max-filas` | `413` con `codigo: "ARCHIVO_DEMASIADO_GRANDE"` y los dos topes |
| CSV con comillas desbalanceadas o ilegible | `400` con `codigo: "CSV_MAL_FORMADO"` |
| Sin cabecera de datos | `201` con `estado: "ANALIZADA"`, `filasDatos: 0`, `puedeAplicar: false` y el mensaje `El archivo no contiene filas de datos; no se cargó nada.` (edge case **Archivo vacío**) |
| El Módulo 1 no responde o el catálogo vino truncado | `503` con `modulo: "MODULO_1"` y, en el segundo caso, `codigo: "CATALOGO_TRUNCADO"` |

Nunca se analiza con un catálogo incompleto: si el Módulo 1 no responde o se alcanza `reservas.modulo1.max-paginas`, la carga se rechaza entera. Motivar una fila con un recurso que no se pudo comprobar sería inventar el motivo que SC-001 exige (P-14).

---

### 2. `POST /api/horarios/importaciones/{id}/confirmacion`

Sin cuerpo. Aplica exactamente lo que el análisis guardó, o no aplica nada (FR-008).

**`200 OK`** — aplicada

```json
{
  "importacionId": "b1f0c7a4-2d51-4f8e-9a03-6c2e5b7d1e90",
  "periodoAcademico": "2026-2",
  "origenCarga": "SEMESTRE",
  "estado": "APLICADA",
  "aplicadoEn": "2026-09-01T09:20:41-05:00",
  "resumen": { "filasLeidas": 401, "aplicables": 394, "yaCargadas": 0, "desplazamientos": 7, "conflictos": 0, "rechazos": 0 },
  "puedeAplicar": false,
  "mensaje": "Se crearon 394 bloqueos académicos y se cancelaron 7 reservas de estudiantes.",
  "bloqueosCreados": 394,
  "bloqueosActualizados": 0,
  "reservasCanceladas": 7,
  "fichasEnCola": 401,
  "cancelacionesEnCola": 7,
  "rechazos": [],
  "conflictos": [],
  "desplazamientos": [
    {
      "fila": 88,
      "recursoId": "ESP-0107",
      "nombre": "Sala de Estudio 3",
      "categoria": "ESPACIO",
      "reservaId": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
      "inicio": "2026-09-10T14:00:00-05:00",
      "fin": "2026-09-10T16:00:00-05:00",
      "titular": { "usuarioId": "5f1b9c2d-7a34-4e81-b0f6-3c8d1e9a4b72", "codigo": "2019114045", "nombre": "Camilo Cerpa" },
      "motivo": "La clase Redes de Computadores ocupa esa franja.",
      "estado": "CANCELADA_POR_PRIORIDAD_ACADEMICA",
      "motivoCancelacion": "Actividad académica: Redes de Computadores.",
      "canceladaEn": "2026-09-01T09:20:41-05:00"
    }
  ],
  "detalle": { "pagina": 1, "tamanoPagina": 50, "total": 401, "totalPaginas": 9, "filas": [] }
}
```

`fichasEnCola` y `cancelacionesEnCola` son **filas escritas en `mensaje_saliente`**, no eventos ya entregados: si Kafka está caído, la carga se aplica igual y los eventos salen cuando vuelva (UC10 FR-004, UC11 FR-008). Por eso el verbo del resumen no es "enviados".

`detalle.filas` vuelve a venir vacío: el detalle de una carga aplicada se pide en §4.

**Errores**

`409` — la carga no se puede aplicar

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errores/carga-no-aplicable",
  "title": "La carga no se puede aplicar",
  "status": 409,
  "detail": "La carga tiene 1 fila con errores y no se aplicó nada.",
  "instance": "/api/horarios/importaciones/c4d8e210-6b3f-4a52-8d71-1f9b0c4e7a33/confirmacion",
  "codigo": "TIENE_RECHAZOS",
  "rechazos": 1
}
```

| `codigo` | Cuándo |
|---|---|
| `TIENE_RECHAZOS` | El análisis encontró al menos un `RECHAZO` (escenario 2). |
| `ESTADO_INVALIDO` | La carga ya está `APLICADA` o `DESCARTADA`. `DESCARTE` ni siquiera se considera una cancelación idempotente: una carga descartada se vuelve a subir. |
| `ESTADO_CAMBIADO` | Entre el análisis y la confirmación aparecieron ocupaciones nuevas que amplían el conjunto de reservas a cancelar. Trae `nuevosConflictos` y `nuevasReservasAEditar` para que se vuelva a mirar antes de insistir. |

`ESTADO_CAMBIADO` es lo que hace honesta la frase "o se cancelan todas y se crean todos los bloqueos": lo que se confirma es el conjunto que se mostró. Si alguien reservó un salón entre el análisis y la confirmación, la carga no aplica y hay que mirarlo otra vez.

`404` si el `importacionId` no existe. `403` si el rol no es `DIRECCION_PROGRAMA`.

---

### 3. Necesidades extraordinarias

Mismo circuito en dos fases, con la diferencia de que una necesidad extraordinaria es **un solo ítem** que se registra por JSON y no por archivo, y de que su bloqueo queda con `horario_semestral_id` nulo —como dice el modelo de datos— aunque la previsualización viva en la misma tabla (decisión de este plan).

**`POST /api/horarios/necesidades-extraordinarias`**

```json
{
  "recursoId": "ESP-0300",
  "fecha": "2026-09-10",
  "inicio": "14:00",
  "fin": "16:00",
  "asignatura": "Examen final de Cálculo Vectorial",
  "programa": "Ingeniería de Sistemas",
  "docente": "Nombre del docente",
  "motivo": "Examen adicional programado por la dirección del programa",
  "periodoAcademico": "2026-2"
}
```

| Campo | Tipo | Obligatorio | Validación |
|---|---|---|---|
| `recursoId` | cadena | sí | Debe existir en el catálogo del Módulo 1. |
| `fecha`, `inicio`, `fin` | `yyyy-MM-dd`, `HH:mm` | sí | Igual que una fila del CSV, pero **sin** el tope de 2 horas. |
| `asignatura`, `programa`, `docente` | cadena | sí | Los tres van a `bloqueo_academico`. |
| `motivo` | cadena | sí | Texto libre. Va al motivo de cancelación de las reservas desplazadas (FR-005); no se guarda en `bloqueo_academico`. |
| `periodoAcademico` | cadena | no | Si falta, se toma el de la última carga `APLICADA` del semestre; si no hay ninguna, `400 PERIODO_REQUERIDO`. |

**`201 Created`** — análisis de la necesidad extraordinaria (escenario 3)

```json
{
  "importacionId": "e5a1f093-7c22-4d68-bb10-3f8a6d1c4e57",
  "periodoAcademico": "2026-2",
  "origenCarga": "EXTRAORDINARIA",
  "estado": "ANALIZADA",
  "analizadoEn": "2026-09-08T16:40:05-05:00",
  "resumen": { "filasLeidas": 1, "aplicables": 1, "yaCargadas": 0, "desplazamientos": 1, "conflictos": 0, "rechazos": 0 },
  "puedeAplicar": true,
  "mensaje": "La actividad se puede registrar: se creará 1 bloqueo y se cancelará 1 reserva de estudiante.",
  "rechazos": [],
  "conflictos": [],
  "desplazamientos": [
    {
      "fila": 1,
      "recursoId": "ESP-0300",
      "nombre": "Auditorio Menor",
      "categoria": "ESPACIO",
      "reservaId": "7b2c9d40-1e58-4a76-8f93-0d5b7c2a1e64",
      "inicio": "2026-09-10T14:00:00-05:00",
      "fin": "2026-09-10T16:00:00-05:00",
      "titular": { "usuarioId": "9c4e17b2-3a85-4d61-b0f2-6e8a5d9c7f31", "codigo": "2019233117", "nombre": "Ana Restrepo" },
      "motivo": "Examen final de Cálculo Vectorial ocupa esa franja."
    }
  ],
  "detalle": { "pagina": 1, "tamanoPagina": 50, "total": 1, "totalPaginas": 1, "filas": [] }
}
```

El sobre es el mismo con `archivo` omitido, porque no hay archivo. `POST /api/horarios/necesidades-extraordinarias/{id}/confirmacion` responde igual que §2, y por la misma ruta se aplica la regla de `ESTADO_CAMBIADO`: si entre el análisis y la confirmación alguien más reservó esa franja, no se aplica.

**Conflictos en una necesidad extraordinaria.** Aquí sí tienen un matiz que no tiene el archivo: si la franja ya tiene un `BLOQUEO_ACADEMICO`, `puedeAplicar` es `false` y el `409` de confirmar lleva `codigo: "BLOQUEO_ACADEMICO_EXISTENTE"`. Es FR-006 en estado puro —una actividad extraordinaria tampoco puede tumbar una clase— y se reporta como conflicto en el análisis, sin abortar nada, porque no hay otras filas que aplicar. Si el recurso está `EN_MANTENIMIENTO` o es un activo ya entregado, el comportamiento es el del archivo.

---

### 4. `GET /api/horarios/importaciones`, `GET /{id}` y `GET /{id}/detalle`

**`GET /api/horarios/importaciones?periodoAcademico=2026-2&pagina=1`** — el historial de cargas, ordenadas por `cargado_en` descendente y, ante empate, por id, igual que las listas de UC1 y UC2.

```json
{
  "cargas": [
    {
      "importacionId": "b1f0c7a4-2d51-4f8e-9a03-6c2e5b7d1e90",
      "periodoAcademico": "2026-2",
      "origenCarga": "SEMESTRE",
      "estado": "APLICADA",
      "archivo": { "nombre": "horario-2026-2.csv", "sha256": "9f2c1ab4", "bytes": 42180, "filasDatos": 401 },
      "analizadoEn": "2026-09-01T09:14:22-05:00",
      "aplicadoEn": "2026-09-01T09:20:41-05:00",
      "resumen": { "filasLeidas": 401, "aplicables": 394, "yaCargadas": 0, "desplazamientos": 7, "conflictos": 0, "rechazos": 0 }
    }
  ],
  "paginacion": { "pagina": 1, "tamanoPagina": 20, "totalPaginas": 2, "total": 27 }
}
```

`resumen`, `mensaje`, `rechazos`, `conflictos` y `desplazamientos` **no** vienen aquí: son los del reporte guardado, y se piden en el detalle.

**`GET /api/horarios/importaciones/{id}`** devuelve el sobre completo tal como quedó guardado en `resultado`, incluido el `detalle` de la primera página. Es lo que permite reabrir una carga analizada mañana y confirmarla pasado un día.

**`GET /api/horarios/importaciones/{id}/detalle?pagina=3`** — una fila por cada línea de datos del archivo, en el orden en que aparecen:

```json
{
  "detalle": {
    "pagina": 3,
    "tamanoPagina": 50,
    "total": 401,
    "totalPaginas": 9,
    "filas": [
      {
        "fila": 118,
        "codigoSesion": "2026-2-MAT-02-04",
        "recursoId": "ESP-0412",
        "nombre": "Laboratorio de Redes",
        "categoria": "ESPACIO",
        "fecha": "2026-08-06",
        "inicio": "10:00",
        "fin": "12:00",
        "asignatura": "Matemáticas Lineales",
        "resultado": "APLICABLE",
        "codigo": null,
        "mensaje": null,
        "reservaId": null
      },
      {
        "fila": 137,
        "codigoSesion": "2026-2-BIO-01-03",
        "recursoId": "ESP-0203",
        "nombre": "Laboratorio de Biología",
        "categoria": "ESPACIO",
        "fecha": "2026-08-07",
        "inicio": "25:00",
        "fin": "12:00",
        "asignatura": "Biología I",
        "resultado": "RECHAZO",
        "codigo": "HORA_INVALIDA",
        "mensaje": "25:00 no es una hora válida. Se espera HH:mm, por ejemplo 08:00.",
        "reservaId": null
      }
    ]
  }
}
```

**Excepción a "nunca `null`"**: dentro del detalle, `codigo`, `mensaje` y `reservaId` **sí** viajan como `null` cuando no aplican. Es el único JSON del módulo donde vale, porque el detalle es una tabla y una columna vacía por celda le costaría al frontend más lógica que los `null`. `codigoSesion` también puede ir `null` si el archivo no lo trajo. Fuera del detalle, la regla de siempre se cumple.

`resultado` toma los valores de `Hallazgo`: `APLICABLE`, `YA_CARGADA`, `DESPLAZAMIENTO`, `CONFLICTO` o `RECHAZO`. En una carga aplicada, `APLICABLE` pasa a ser `YA_CARGADA` en la columna `resultado` del detalle guardado, porque ya se escribió.

**`GET /api/horarios/plantilla`** — devuelve el CSV de ejemplo de §Decisiones, como `text/csv; charset=utf-8`, con `Content-Disposition: attachment`. No necesita sesión: no tiene datos de nadie. Es el botón "descargar plantilla" de la pantalla.

**`POST /api/horarios/importaciones/{id}/descarte`** → `204`, y la fila queda en `DESCARTADA`. Es lo que hace la persona cuando el reporte no le sirve; la fila se conserva porque el reporte es parte de la auditoría.

---

### 5. Módulo 1 — `InventarioPort`

UC3 **no agrega operaciones** al puerto: usa exactamente las dos que ya define [UC1 § Contratos §2](./plan-uc1-consultar-recursos.md#2-módulo-1--inventarioport), y por eso tampoco toca `InventarioRestAdapter`.

| Operación | Para qué la usa UC3 |
|---|---|
| `buscarCatalogo(FiltroRecursos)` sin filtros | **Una sola vez** por análisis, recorriendo todas las páginas (UC8 FR-011), para indexar por `recursoId` la categoría, el estado operativo, el nombre, el tipo y la ubicación. |
| `estadoOperativo(recursoIds)` | No se usa: la segunda operación existiría para revalidar, y aquí el estado operativo viene ya en el catálogo. Queda definida para UC2 y UC8. |

**Por qué el catálogo entero y no la operación por lote.** Con 400 filas que nombran 300 recursos distintos, tres llamadas de 100 identificadores (`estadoOperativo`) solo darían el estado, y faltaría el nombre y la categoría, que vienen en la ficha y esa sí sería una llamada por fila. El catálogo completo cuesta una travesía de páginas que UC1 ya sabe hacer y da todo de una vez.

**Catálogo truncado.** Si el recorrido llega a `reservas.modulo1.max-paginas` sin terminar, el análisis **no continúa**: responde `503` con `codigo: "CATALOGO_TRUNCADO"` y no crea la fila de carga. Un recurso que no vimos podría ser la razón de un rechazo que no se pudo comprobar, y el escenario 2 promete que quien lo corrigió se puede volver a subir sin sorpresas; mejor no pasar.

El adaptador falso del perfil local (`InventarioFakeAdapter`, de UC1) se amplía con un catálogo de **varios cientos de recursos** repartidos por tipo, para que la demo de la carga se parezca a un semestre real y no a cinco salones.

---

### 6. Eventos hacia el Módulo 3

UC3 produce **dos** eventos, y solo uno de ellos es nuevo.

**La ficha del bloqueo (ya existe).** Sale por el mismo camino de UC2: un `FICHA_RESERVA` con `origen: "ACADEMICO"`, `sancionable: false`, el bloque `academico` con asignatura, programa y docente y sin `titular`. Es exactamente el ejemplo de [UC2 § Contratos §7](./plan-uc2-reservar-recursos.md#7-evento-hacia-el-módulo-3--modulo2reservafichav1), y es lo que pide UC10 FR-008. Con esto UC10 queda completo: ya no le falta la ficha de los bloqueos académicos.

La diferencia es de volumen: una carga de 400 clases son 400 eventos al mismo topic, todos con su `reserva_id` como clave de partición. La *outbox* los guarda en la misma transacción y el `PublicadorOutbox` los va sacando en lotes de `reservas.outbox.lote`, así que no hay nada que cambiar ahí.

**La cancelación del estudiante desplazado (nuevo).** UC11 no tiene plan todavía, así que este plan fija su contrato. Es el mismo topic del plan general, `modulo2.reserva.cancelacion.v1`:

```json
{
  "eventoId": "6b93d0e8-2f41-4c07-9a15-8d3e5b7c1f92",
  "tipo": "ReservaCancelada",
  "version": 1,
  "ocurridoEn": "2026-09-01T09:20:41-05:00",
  "datos": {
    "reservaId": "9f3c1d7e-5b42-4a19-8c0d-2f7e6a1b3c45",
    "origenCancelacion": "PRIORIDAD_ACADEMICA",
    "responsabilidad": "NO_DEL_USUARIO",
    "titular": { "usuarioId": "5f1b9c2d-7a34-4e81-b0f6-3c8d1e9a4b72", "codigo": "2019114045", "nombre": "Camilo Cerpa" },
    "recurso": { "id": "ESP-0107", "nombre": "Sala de Estudio 3", "categoria": "ESPACIO", "tipo": "SALA_DE_ESTUDIO", "ubicacion": "Biblioteca, piso 1" },
    "liberado": { "inicio": "2026-09-10T14:00:00-05:00", "fin": "2026-09-10T16:00:00-05:00" },
    "motivo": "Actividad académica: Redes de Computadores.",
    "ejecutadaEn": "2026-09-01T09:20:41-05:00",
    "bloqueoOrigen": { "tipo": "REGULAR", "asignatura": "Redes de Computadores", "programa": "Ingeniería de Sistemas", "docente": "Nombre del docente" }
  }
}
```

| Regla del evento | Detalle |
|---|---|
| `origenCancelacion` | `TITULAR`, `PRIORIDAD_ACADEMICA` o `RECURSO_NO_DISPONIBLE`, exactamente los tres orígenes de UC11 FR-003. Aquí solo puede salir `PRIORIDAD_ACADEMICA`. |
| `responsabilidad` | `NO_DEL_USUARIO` en las de UC3. Es lo que impide que el Módulo 3 sancione a quien fue desplazado (UC11 FR-006, SC-004, UC9 FR-006). |
| `antelacionMinutos` | **Nunca viaja** desde UC3: cuando cancela el sistema, la antelación no dice nada de la persona, y UC11 avisa de no dejar que se lea como mérito. Solo la cancelación del titular lo lleva, en su plan. |
| `bloqueoOrigen.tipo` | `REGULAR` cuando viene de la carga del semestre y `EXTRAORDINARIO` cuando viene de una necesidad extraordinaria. Es lo que le dice al Módulo 3 quién puso la actividad. |
| `liberado` | El periodo completo en el caso de un activo no recogido, y la franja en un espacio (UC11 FR-002, UC4 FR-002). |
| Clave de partición | `reserva_id`. Con eso, la ficha de esa reserva (si la hubo) nunca llega después de su cancelación. |
| `eventoId` | Es el `mensaje_saliente.id`: identifica el hecho, y el mismo `eventoId` descarta la reentrega (UC11 FR-007). |

**Un evento por reserva, nunca agrupado.** El edge case de UC11 sobre la cancelación masiva es explícito: una carga que desplaza decenas de reservas genera un reporte por cada una. Aquí eso es directo: cada `UPDATE` de reserva escribe su fila en `mensaje_saliente` dentro del mismo bucle. No hay nada que agrupar ni que decidir.

**Cero eventos de un bloqueo que se excluye.** Una fila con `CONFLICTO` no crea bloqueo y por tanto **no genera ficha**: UC10 FR-007 manda. Un choque no es una reserva confirmada.

**Qué pasa con el evento si se retira una clase.** Si una segunda carga con el mismo `codigoSesion` mueve la clase de sitio, el bloqueo se **actualiza** y sale otra ficha con la franja nueva, porque el Módulo 3 necesita saber que el tiempo cambió. Si lo que ocurre es que la clase desaparece del archivo, el bloqueo viejo se queda: este plan no lo retira, porque el spec no dice que lo haga y borrarlo en silencio dejaría a los estudiantes con un salón libre que tiene clase. Se reporta como observación en el reporte y queda para decisión del equipo (P-19). (NEEDS CLARIFICATION: hay que decidir si retirar una clase genera una cancelación hacia el Módulo 3; hoy, no.)

---

### 7. Tipos del frontend

Traducción literal de las secciones 1 a 4, en `frontend/src/features/horarios/tipos.ts`. Reusa `Categoria` y `ErrorApi` de `features/recursos/tipos.ts` (UC1).

```ts
import type { Categoria, ErrorApi } from "../recursos/tipos";

export type OrigenCarga = "SEMESTRE" | "EXTRAORDINARIA";
export type EstadoCarga = "ANALIZADA" | "APLICADA" | "DESCARTADA";

export type ResultadoFila =
  | "APLICABLE" | "YA_CARGADA" | "DESPLAZAMIENTO" | "CONFLICTO" | "RECHAZO";

export type CodigoRechazo =
  | "CABECERA_INVALIDA" | "CAMPO_OBLIGATORIO_AUSENTE" | "FECHA_INVALIDA"
  | "HORA_INVALIDA" | "FIN_NO_POSTERIOR_A_INICIO" | "FRANJA_FUERA_DE_VENTANA"
  | "RECURSO_NO_ENCONTRADO" | "TIPO_RECURSO_NO_SOPORTADO" | "FILA_DUPLICADA";

export type CodigoConflicto =
  | "BLOQUEO_ACADEMICO_EXISTENTE" | "ACTIVO_ENTREGADO_Y_SIN_DEVOLVER"
  | "RECURSO_EN_MANTENIMIENTO" | "CHOQUE_ENTRE_FILAS_DEL_ARCHIVO";

export interface Titular {
  usuarioId: string;
  codigo: string;
  nombre: string;
}

export interface Hallazgo {
  fila: number;
  codigo: CodigoRechazo | CodigoConflicto | string;
  recursoId?: string;
  campo?: string;                 // solo en rechazos
  valor?: string;                 // solo en rechazos: lo que venía en el archivo
  mensaje: string;
}

export interface Desplazamiento {
  fila: number;
  recursoId: string;
  nombre?: string;
  categoria: Categoria;
  reservaId: string;
  inicio: string;
  fin: string;
  titular: Titular;
  motivo: string;
  estado?: "CANCELADA_POR_PRIORIDAD_ACADEMICA";   // solo después de aplicar
  motivoCancelacion?: string;
  canceladaEn?: string;
}

export interface ResumenCarga {
  filasLeidas: number;
  aplicables: number;
  yaCargadas: number;
  desplazamientos: number;
  conflictos: number;
  rechazos: number;
}

export interface FilaDetalle {
  fila: number;
  codigoSesion: string | null;
  recursoId: string;
  nombre?: string;
  categoria?: Categoria;
  fecha: string | null;           // null cuando no se pudo interpretar
  inicio: string | null;
  fin: string | null;
  asignatura: string | null;
  resultado: ResultadoFila;
  codigo: string | null;
  mensaje: string | null;
  reservaId: string | null;
}

export interface Paginacion {
  pagina: number;
  tamanoPagina: number;
  totalPaginas: number;
  total: number;
}

export interface ReporteImportacion {
  importacionId: string;
  periodoAcademico: string;
  origenCarga: OrigenCarga;
  estado: EstadoCarga;
  archivo?: { nombre: string; sha256: string; bytes: number; filasDatos: number };
  analizadoEn: string;
  aplicadoEn?: string;
  resumen: ResumenCarga;
  puedeAplicar: boolean;
  mensaje: string;
  bloqueosCreados?: number;
  bloqueosActualizados?: number;
  reservasCanceladas?: number;
  fichasEnCola?: number;
  cancelacionesEnCola?: number;
  rechazos: Hallazgo[];
  conflictos: Hallazgo[];
  desplazamientos: Desplazamiento[];
  detalle: Paginacion & { filas: FilaDetalle[] };
}

export interface NecesidadExtraordinariaRequest {
  recursoId: string;
  fecha: string;
  inicio: string;
  fin: string;
  asignatura: string;
  programa: string;
  docente: string;
  motivo: string;
  periodoAcademico?: string;
}

export interface ResumenCargaListado {
  importacionId: string;
  periodoAcademico: string;
  origenCarga: OrigenCarga;
  estado: EstadoCarga;
  archivo?: { nombre: string; sha256: string; bytes: number; filasDatos: number };
  analizadoEn: string;
  aplicadoEn?: string;
  resumen: ResumenCarga;
}

export interface ListaImportacionesResponse {
  cargas: ResumenCargaListado[];
  paginacion: Paginacion;
}

/** Extensiones que puede traer un ProblemDetail de este caso de uso. */
export interface ErrorHorario extends ErrorApi {
  codigo?: CodigoRechazo | CodigoConflicto
    | "ARCHIVO_DEMASIADO_GRANDE" | "CSV_MAL_FORMADO" | "PERIODO_INVALIDO"
    | "PERIODO_REQUERIDO" | "CATALOGO_TRUNCADO"
    | "TIENE_RECHAZOS" | "ESTADO_INVALIDO" | "ESTADO_CAMBIADO"
    | "ARCHIVO_YA_CARGADO";
  esperado?: string;
  recibido?: string;
  rechazos?: number;
  filas?: number;
  maxBytes?: number;
  maxFilas?: number;
  nuevoChoques?: number;
  nuevasReservasACancelar?: number;
  importacionExistenteId?: string;
}
```

`ReporteImportacion.tsx` decide qué mostrar con dos banderas, no leyendo textos: si `puedeAplicar` es `false` no aparece el botón de confirmar; y `resumen.rechazos > 0` pinta la tabla de rechazos primero, porque es lo que hay que corregir en el archivo. El `detalle` filtra por `resultado`, que es un campo del contrato y no un texto.

---

### 8. Fixtures compartidos

```text
src/test/resources/contratos/
├── plantilla-horarios.csv                   # la plantilla de §Decisiones
├── horarios-2026-2-valido.csv               # 401 filas, 7 desplazamientos, sin rechazos
├── horarios-2026-2-con-rechazo.csv          # 412 filas, una con inicio 25:00
├── horarios-2026-2-con-conflictos.csv       # bloqueo existente, activo entregado y en mantenimiento
├── horarios-2026-2-vacio.csv                # solo cabecera
├── api-horarios-analisis-201.json           # el sobre de §1, listo para aplicar
├── api-horarios-analisis-no-aplicable.json  # escenario 2
├── api-horarios-aplicada-200.json           # §2
├── api-horarios-409-estado-cambiado.json
├── api-horarios-403-rol.json
├── api-necesidad-extraordinaria-201.json    # §3, escenario 3
├── evento-cancelacion-prioridad.json        # lo que debe quedar en mensaje_saliente.payload
└── api-horarios-detalle.json                # una página de detalle con las cinco categorías
```

Los adaptadores falsos del perfil local sirven un catálogo coherente con `horarios-2026-2-valido.csv`: los `recursoId` del archivo existen, los activos tienen `plazoPrestamoDiasHabiles` y uno de ellos aparece prestado y entregado, que es lo que hace posible ver el `CONFLICTO` de FR-010 en la demo sin montar una base de reservas a mano.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Sumar a la base de UC1 y UC2 lo único que este caso de uso necesita de fuera: leer el archivo y sus topes.

- [ ] T001 Agregar a `build.gradle` `org.apache.commons:commons-csv` en la configuración principal, sin más dependencias: el XLSX se descarta (ver la decisión de diseño) y el backend no necesita nada más
- [ ] T002 [P] Configurar multipart en `application.properties` (`spring.servlet.multipart.max-file-size`, `max-request-size`) y los topes del negocio: `reservas.importacion.max-filas=5000`, `reservas.importacion.max-bytes=2MB`, `reservas.importacion.lote-escritura=500`
- [ ] T003 [P] Extender `PropiedadesReservas` con esos tres parámetros y validarlos al arrancar (positivos y con un tope máximo razonable, para que nadie deje el archivo unlimited por un typo)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Esquema, modelo de dominio, puertos y el analizador. Sin esto no hay ni una prueba que escribir.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T004 Escribir `src/main/resources/db/migration/V4__horario_semestral_y_bloqueo.sql`: a `horario_semestral` las columnas `estado`, `origen_carga`, `archivo_nombre`, `archivo_sha256`, `archivo_contenido` y `sha256_completo`; a `bloqueo_academico` la columna `clave`; y el índice único `bloqueo_academico_clave_unico` sobre ella, más un índice en `bloqueo_academico (horario_semestral_id)`. No se añade un índice por recurso en `bloqueo_academico` porque el recurso vive en `reserva`, y para cruzarlo por recurso ya está el índice GiST de `reserva_sin_cruces`. Justificar en el comentario del propio SQL por qué la clave va aquí y no en `reserva`
- [ ] T005 [P] Actualizar `modelo-datos-der.md` con las columnas nuevas de `horario_semestral` y `bloqueo_academico`, porque ese documento declara que no agrega nada que no esté en el plan de arquitectura: si no se actualiza, los dos quedan mintiendo
- [ ] T006 [P] Mover el tope de 2 horas fuera de `FranjaHoraria` y ponerlo en `ReservarRecursosUseCase`, aplicado solo con `origen = ESTUDIANTIL` (P-19). `FranjaHoraria` se queda con la ventana de 06:00–22:00, el mismo día y `inicio < fin`. Es un cambio en código de UC2 y tiene que quedar con su prueba de regresión en T012
- [ ] T007 [P] Crear `domain/model/bloqueo/`: `TipoBloqueo`, `DatosAcademicos`, `SesionDeClase` (con `clave()`, que usa `codigoSesion` si viene y si no arma la clave natural), `Hallazgo`, `ResultadoAnalisis` y `HorarioSemestral`. Sin Spring, sin JPA, sin nada del parser
- [ ] T008 [P] Crear `ImportacionInvalidaException` (400) y `CargaNoAplicableException` (409, con su código y el diff de `ESTADO_CAMBIADO`), y mapearlas en `ManejadorGlobalErrores` junto al `413` de archivo demasiado grande y al `403` de rol
- [ ] T009 [P] Definir los puertos de entrada `ImportarHorariosPort`, `RegistrarNecesidadExtraordinariaPort`, `CancelarReservaPort` y `ReportarCancelacionPort`, y los de salida `HorarioRepositoryPort` y `LectorArchivoPort`; extender `OcupacionRepositoryPort` con `ocupacionesQueSeCruzan(recursoIds, inicio, fin)` (incluidos los préstamos abiertos sin devolver) y `NotificadorModulo3Port` con la cancelación
- [ ] T010 [P] Implementar `LectorCsvHorario` en `infrastructure/adapter/out/archivo/`: lee con `commons-csv`, sin `BOM` de por medio, con cabecera obligatoria y las ocho columnas reconocidas, y devuelve `FilaArchivo` (número de fila y celdas crudas). Todo error de lectura es `ImportacionInvalidaException`
- [ ] T011 Implementar `ValidadorFila`: un rechazo por fila inválida con su código de la tabla de §1, sin preguntar nada al exterior —puro. Y `AnalizadorCarga`, que recibe el catálogo indexado y las ocupaciones y devuelve el `ResultadoAnalisis` completo (depende de T007, T009, T010)

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Cargar el horario del semestre (Priority: P2)

**Goal**: Dirección de Programa sube un CSV de un semestre, ve el reporte fila por fila antes de que se toque nada, y al confirmar quedan todos los bloqueos creados y las reservas desplazadas canceladas con su motivo y su aviso.

**Independent Test**: Con el perfil local, subir el CSV de prueba, revisar el reporte, confirmar y comprobar que los salones con clase aparecen `BLOQUEO_ACADEMICO` en la pantalla de UC1 y que la reserva del Auditorio Menor quedó `CANCELADA_POR_PRIORIDAD_ACADEMICA` con su evento en la *outbox*. No necesita que exista `Cancelar reserva` del titular.

### Tests for User Story 1

- [ ] T012 [P] [US1] Pruebas en `FranjaHorariaTest.java` (regresión de T006): una franja de 3 horas es válida; `ReservarRecursosUseCase` sigue rechazando con `DURACION_EXCEDIDA` una reserva estudiantil de 3 horas; y una clase fuera de la ventana de 06:00–22:00 es `FRANJA_FUERA_DE_VENTANA`
- [ ] T013 [P] [US1] Pruebas en `SesionDeClaseTest.java`: clave con `codigoSesion`, clave natural sin él, las dos dan el mismo resultado para la misma clase, y una clase movida de hora cambia la clave natural pero no la de `codigoSesion`
- [ ] T014 [P] [US1] Pruebas en `ValidadorFilaTest.java`: los nueve códigos de `RECHAZO`, incluido el `FILA_DUPLICADA` dentro del mismo archivo y el `CABECERA_INVALIDA` repetido en 400 filas
- [ ] T015 [P] [US1] Pruebas en `LectorCsvHorarioTest.java`: el CSV de ejemplo, cabeceras con comillas, un valor con coma y con punto y coma dentro, BOM UTF-8, línea en blanco al final, CSV mal formado (comillas desbalanceadas), archivo vacío y archivo sin cabecera
- [ ] T016 [P] [US1] Pruebas en `AnalizadorCargaTest.java` con el catálogo y las ocupaciones falsos: escenario 1 (todo aplicable), escenario 2 (un rechazo y nada aplicado), escenario 4 (choque con un bloqueo existente que **no** se cancela), el `CONFLICTO` de `EN_MANTENIMIENTO`, el `DESPLAZAMIENTO` de una reserva de espacio y el de un préstamo **sin recoger** (FR-010), el `CONFLICTO` de un préstamo **recogido**, el choque entre dos filas del mismo archivo, y una carga reenviada que sale con `YA_CARGADA`
- [ ] T017 [P] [US1] Pruebas en `ImportarHorariosUseCaseTest.java`: el análisis no escribe ni un bloqueo ni cancela nada, deja la carga en `ANALIZADA` y devuelve `puedeAplicar` según si hay rechazos; confirmar con rechazos falla y no escribe; el mismo archivo con el mismo `sha256` devuelve `409 ARCHIVO_YA_CARGADO` con el id de la carga anterior; confirmar una carga ya `APLICADA` falla con `ESTADO_INVALIDO`; y en la fase de aplicación **no** se llama al Módulo 1 ni al Módulo 3 por REST
- [ ] T018 [P] [US1] Pruebas en `CancelarPorPrioridadAcademicaUseCaseTest.java`: cancela con `CANCELADA_POR_PRIORIDAD_ACADEMICA` y su motivo, deja un evento por reserva, es idempotente sobre una reserva ya cancelada y **no** toca un `BLOQUEO_ACADEMICO` ni un préstamo ya entregado (FR-006, FR-010)
- [ ] T019 [P] [US1] Prueba de integración `AplicarCargaIT.java` con Testcontainers: una carga de 400 sesiones inserta 400 reservas `ACADEMICO` con su `bloqueo_academico` y 400 eventos en la misma transacción; si una fila revienta, **no** queda ni un bloqueo, ni una cancelación, ni un evento (todo o nada); reejecutar el mismo `codigoSesion` **actualiza** el bloqueo y no lo duplica (FR-007, SC-003); y la restricción `reserva_sin_cruces` sigue rechazando un solapamiento que se cuele
- [ ] T020 [P] [US1] Prueba de integración `ImportarConcurrenciaIT.java`: dos cargas simultáneas del mismo archivo dejan cero bloqueos repetidos y exactamente un conjunto de eventos; y dos necesidades extraordinarias simultáneas sobre la misma franja dejan un solo bloqueo y un `409` en la otra
- [ ] T021 [P] [US1] Guardar los JSON y CSV de [Contratos §8](#8-fixtures-compartidos) como *fixtures* y escribir con ellos `HorarioControllerTest.java` con `@WebMvcTest`: el `201` de análisis listo para aplicar y el que no se puede aplicar, el `200` de aplicada con sus tres campos de resumen, los cuatro `409` (`TIENE_RECHAZOS`, `ESTADO_INVALIDO`, `ESTADO_CAMBIADO`, `ARCHIVO_YA_CARGADO`), los `400` de cabecera y de CSV mal formado, el `413`, el `401`, el `403` de un `ESTUDIANTE`, el `503` con `MODULO_1` y `CATALOGO_TRUNCADO`, el detalle paginado con las cinco categorías, y que **ningún campo salga como `null`** salvo los cuatro del detalle
- [ ] T022 [P] [US1] Extender `PublicadorOutboxIT.java`: el evento de cancelación que llega al topic coincide con el *fixture* `evento-cancelacion-prioridad.json` —envoltorio, clave de partición `reserva_id`, `responsabilidad: NO_DEL_USUARIO` y sin `antelacionMinutos`—, se publica una vez por reserva aunque desplaza muchas, y el `eventoId` repetido se descarta

### Implementation for User Story 1

- [ ] T023 [P] [US1] Crear las entidades JPA `HorarioSemestralJpa` y `BloqueoAcademicoJpa` con sus repositorios, y el mapper dominio ↔ JPA, incluyendo el `resultado` jsonb que se lee y se escribe tal cual
- [ ] T024 [P] [US1] Extender `OutboxNotificadorAdapter` con el tipo `CANCELACION` y `EventoReservaCancelada` según [Contratos §6](#6-eventos-hacia-el-módulo-3), guardando el JSON completo como `payload` para republicar sin recomponer
- [ ] T025 [P] [US1] Implementar `HorarioPersistenceAdapter`: guardar el análisis, marcar `APLICADA` o `DESCARTADA`, y `aplicarEnUnaTransaccion(...)` con el `FOR UPDATE` ordenado, la revalidación que produce `ESTADO_CAMBIADO`, el `INSERT` masivo de `reserva` + `bloqueo_academico` con `ON CONFLICT (clave) DO UPDATE`, los `UPDATE` de cancelación y los `INSERT` masivos en `mensaje_saliente`; traducir la violación de `reserva_sin_cruces` a `CargaNoAplicableException` con `ESTADO_CAMBIADO`
- [ ] T026 [P] [US1] Ampliar `InventarioFakeAdapter` con un catálogo de varios cientos de recursos coherente con el CSV de prueba, para que la demo tenga choques de verdad que mostrar
- [ ] T027 [US1] Implementar `CancelarPorPrioridadAcademicaUseCase` y `ReportarCancelacionUseCase`: la parte de UC4 FR-004 y FR-005 que UC3 necesita, y el `<<include>>` a UC11 que se cumple siempre, con un evento por reserva y la `responsabilidad` que impide que el Módulo 3 penalice a nadie (depende de T024)
- [ ] T028 [US1] Implementar `ImportarHorariosUseCase` con las cuatro operaciones —`analizar`, `confirmar`, `descartar`, `listar`—, que es el que decide el `sha256` duplicado y el `403` nunca se decide aquí sino en el adaptador (depende de T011, T025, T027)
- [ ] T029 [US1] Registrar los beans y las transacciones de UC3 en `CasosDeUsoConfig`; `analizar` es de solo lectura salvo la fila de la carga, `confirmar` es la única escritura (depende de T023 a T028)
- [ ] T030 [US1] Implementar `HorarioController` exactamente como lo fija [Contratos §1 a §4](#1-post-apihorariosimportaciones--frontend--módulo-2): multipart validado, el sobre con los `null` solo en el detalle, `Location` en el `201`, `text/csv` en la plantilla, y la documentación OpenAPI con los mismos ejemplos de la sección
- [ ] T031 [P] [US1] Frontend: copiar los tipos de [Contratos §7](#7-tipos-del-frontend) a `frontend/src/features/horarios/tipos.ts` y escribir en `api.ts` el `FormData` de la subida, la confirmación, el descarte, el detalle paginado y la descarga de la plantilla
- [ ] T032 [P] [US1] Frontend: `CargarHorario.tsx` (arrastrar y soltar, botón de plantilla, `periodoAcademico`) y `ReporteImportacion.tsx`, que decide con `puedeAplicar` y `resumen.rechazos` y muestra los tres listados en pestañas
- [ ] T033 [P] [US1] Frontend: `DetalleFilas.tsx` con filtro por `resultado` y el mensaje de cada rechazo tal cual, `ConfirmarCarga.tsx` que exige marcar la casilla de que se cancelarán N reservas antes de habilitar el botón, y `ListaImportaciones.tsx` con el estado de cada carga
- [ ] T034 [US1] Frontend: `app/(direccion)/horarios/page.tsx`, que encadena subida → reporte → confirmación → resumen aplicado y deja entrar a la necesidad extraordinaria (depende de T031 a T033)

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: Necesidades extraordinarias (FR-005, FR-010)

**Purpose**: El segundo camino del spec: una actividad que no venía en el horario y que se impone sobre lo que ya estaba reservado. No es una historia aparte —el spec tiene una sola— pero se entrega y se prueba aparte, y puede quedar después de la demo sin romper la Phase 3.

- [ ] T035 [P] Pruebas de `NecesidadExtraordinariaUseCaseTest`: el escenario 3 completo (la reserva del Auditorio Menor queda `CANCELADA_POR_PRIORIDAD_ACADEMICA`, se crea el bloqueo y sale su evento), el `periodoAcademico` por defecto tomado de la última carga aplicada y el `400 PERIODO_REQUERIDO` cuando no hay ninguna, la franja de más de 2 horas aceptada, el `CONFLICTO` de un bloqueo ya existente (FR-006) y el de un activo ya entregado (FR-010)
- [ ] T036 Implementar `AnalizadorNecesidadExtraordinaria` y `RegistrarNecesidadExtraordinariaUseCase` reusando `ValidadorFila`, `AnalizadorCarga` y `CancelarPorPrioridadAcademicaUseCase`: una sesión, `tipo = EXTRAORDINARIO`, y el `bloqueo_academico` con `horario_semestral_id` nulo (depende de T025, T028)
- [ ] T037 Implementar los dos endpoints de necesidad extraordinaria en `HorarioController` según [Contratos §3](#3-necesidades-extraordinarias) y `NecesidadExtraordinaria.tsx` en el frontend, con el formulario y la misma pantalla de confirmación

**Checkpoint**: El spec de UC3 queda cubierto de punta a punta

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Mejoras que afectan a toda la funcionalidad

- [ ] T038 [P] Verificar SC-001 cargando un semestre sintético de 1.000 sesiones contra el Módulo 1 simulado, y comprobar que el análisis y la aplicación caben en los 5 minutos con el reporte del 100 % de los rechazos
- [ ] T039 [P] Verificar SC-002: tras una carga, ninguna de las franjas bloqueadas acepta una reserva y devuelve `RES-001`; y SC-004: las 7 reservas desplazadas quedaron `CANCELADA_POR_PRIORIDAD_ACADEMICA`, sin `denegacion` en su contra y con su evento en la cola
- [ ] T040 [P] Registrar en logs cada carga con su duración, el número de filas, rechazos, conflictos, desplazamientos y el resultado, y **sin** datos de titulares: quién fue desplazado vive en la base, no en el log
- [ ] T041 [P] Actualizar el README con cómo cargar un horario en local, dónde está la plantilla y cómo se ve la *outbox* cuando el broker está caído
- [ ] T042 Enviar al Módulo 1 la petición del catálogo completo que necesita el análisis y al Módulo 3 el evento `ReservaCancelada` de [Contratos §6](#6-eventos-hacia-el-módulo-3) para confirmarlo, y llevar a `pendientes-clarificacion.md` los NEEDS CLARIFICATION de este plan

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: depende de que los planes de UC1 y UC2 estén hechos; sin su Phase 2 no hay esquema, `FranjaHoraria`, `InventarioPort` ni *outbox*
- **Foundational (Phase 2)**: depende de Setup - BLOCKS la user story
- **User Story 1 (Phase 3)**: depende de Foundational
- **Necesidades extraordinarias (Phase 4)**: depende de la Phase 3, porque reusa el analizador, el aplicador y la cancelación por prioridad
- **Polish (Phase 5)**: depende de que las Phases 3 y 4 estén completas

### Dependencias con otros casos de uso

- **UC1 `Consultar recursos`**: es la base de la que sale el `InventarioPort` que valida las filas. Sus `YA_CARGADA` y `CONFLICTO` no le hacen nada: lo que UC3 escribe son `reserva` y `bloqueo_academico`, y UC1 los lee tal cual. **Lo que este plan le verifica es SC-002**: que un salón bloqueado salga `BLOQUEO_ACADEMICO` y no seleccionable.
- **UC2 `Reservar recursos`**: aporta `Reserva`, `EstadoReserva`, `OrigenReserva`, `SolicitudDeReserva.origen`, `ReservaRepositoryPort` y el camino de `ACADEMICO`. Este plan le cambia una cosa: **mueve el tope de 2 horas** de `FranjaHoraria` al caso de uso (T006), porque una clase de 3 horas tiene que entrar. También le implementa el evento académico que su plan dejó escrito "para no refactorizar luego".
- **UC4 `Cancelar reserva`**: este plan implementa **solo** la cancelación automática por prioridad académica (FR-004, FR-005) y el `estado` `CANCELADA_POR_PRIORIDAD_ACADEMICA`. Su propio plan añade la cancelación del titular, `CAN-001` y `CAN-002`, y la pantalla donde ese botón vive, que es la `mis-reservas` que UC2 ya creó.
- **UC7 `Actualizar estado de los recursos`**: igual que en UC2, lo que este plan hace es **dejar escrita la ocupación**; no le manda nada al Módulo 1 mientras P-20 esté abierto. El `bloqueo_academico` es la fila que hace que UC1 muestre `BLOQUEO_ACADEMICO`.
- **UC10 `Reportar información de la reserva`**: este plan produce la ficha académica que su plan esperaba (UC10 FR-008). Con esto UC10 queda completo, y con esto también el `sancionable: false` que UC9 FR-006 necesita.
- **UC11 `Reportar cancelación de reserva`**: este plan define su contrato de evento y produce el reporte de las cancelaciones por prioridad académica (FR-001, FR-006). Su plan añade la cancelación del titular con su antelación (FR-004) y el `CANCELACION` por recurso no disponible, que nace en UC4 FR-010.
- **UC9 `Recibir reporte de no ausencia`**: no lo necesita. Como la ficha académica va marcada `sancionable: false`, un reporte de ausencia sobre una clase se rechaza; su plan añade ese FR cuando P-19 se cierre.
- **Registro e inicio de sesión**: viene del login mínimo de UC1; aquí solo importa el rol `DIRECCION_PROGRAMA`, que no puede salir del autorregistro.

### Within User Story 1

- Modelo y puertos (T007, T009) → lector y validador (T010, T011) → casos de uso (T027 → T028) → adaptador de persistencia (T025)
- Los adaptadores (T023, T024, T026) y las pruebas (T012 a T022) solo dependen de los puertos, así que van en paralelo con los casos de uso
- Beans (T029) → controlador (T030) → frontend (T031 a T034)

### Parallel Opportunities

- En Setup: T002 y T003
- En Foundational: T005, T007, T008, T009 y T010
- En User Story 1: todas las pruebas (T012 a T022), las entidades y el *outbox* (T023, T024) y los adaptadores (T026)
- En Phase 4: T035
- En Polish: T038 a T041

## Notes

- La numeración `T0XX` es propia de este plan y empieza de nuevo; no continúa con la del plan de UC2
- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Verify tests pass
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- La sección **Contratos** es la única fuente del JSON de UC3: si algo cambia ahí, cambia en los *fixtures*, en el OpenAPI y en los tipos del frontend, no al revés. Las convenciones comunes y el contrato del Módulo 1 no se repiten: viven en el plan de UC1
- **Decisiones que el spec no fija y que este plan toma**:
  - **CSV y no XLSX**, con plantilla publicada. Nadie nos dio un archivo real del Sistema Académico
  - **Una fila por sesión con fecha concreta**, y no una clase recurrente de día de semana: no hay calendario de periodos ni de festivos que permita expandir bien
  - **Un `RECHAZO` aborta la carga y un `CONFLICTO` no.** Es la lectura del escenario 2 junto con FR-006 y FR-010
  - **La fase de aplicación no hace ninguna llamada HTTP**: el `<<include>>` a `Reservar recursos` se cumple reusando su camino de dominio y de persistencia, no llamándolo 400 veces
  - **El `detalle` viaja paginado y es el único JSON donde se permiten `null`** (SC-001 lo exige: el motivo del 100 % de las filas rechazadas)
  - **Solo `DIRECCION_PROGRAMA`** entra, y es el único que ve la lista de reservas desplazadas con su titular, porque FR-008 pide saber cuáles
  - **Un choque de FR-010 no borra un préstamo entregado** y **una clase que desaparece de una reimportación no borra su bloqueo**: las dos cosas las resuelve el equipo, no el sistema
- **Lo que este plan cambia en planes anteriores**:
  - **T006**: el tope de 2 horas sale de `FranjaHoraria` (T008 de UC2) y entra en `ReservarRecursosUseCase`, porque una clase de 3 horas tiene que poder bloquearse (P-19)
  - **T005**: `modelo-datos-der.md` gana las columnas de `V4`, o el diagrama y el esquema dejan de cuadrar
  - La Phase 4 del plan de UC2 (renovación de préstamos) quedó fuera de alcance el 2026-09-21; este plan no depende de ella
- **NEEDS CLARIFICATION abiertos en este plan**:
  - **P-19**: qué reglas de `Reservar recursos` se saltan cuando el origen es académico; el supuesto es sanción, cupo y tope de 2 horas. Y si una clase se retira del archivo, el bloqueo **no** se borra y no se reporta como cancelación al Módulo 3
  - **P-20 punto 5**: quién avisa al estudiante desplazado. Aquí la reserva queda marcada, visible en `mis-reservas`, y sale el evento al Módulo 3; el canal es otro
  - **P-20 punto 3**: si `EN_MANTENIMIENTO` trae fechas; mientras no lo traiga, un recurso en mantenimiento bloquea cualquier clase futura
  - **P-16**: sin el catálogo del Módulo 1 no se puede validar ni una fila; la carga entera se rechaza con `503` en vez de dar motivos inventados
  - **P-08**: un préstamo vencido y sin devolver sigue ocupando el activo; si una clase lo pisa, es `CONFLICTO`
  - **`codigoSesion`**: si el Sistema Académico tiene identificador de sesión, hay que pedirlo y volverlo obligatorio; es lo que hace que "actualizar lo que existe" sea de verdad una actualización y no una clase nueva
  - **Calendario de periodos académicos**: `2026-2` es una etiqueta; nadie nos dio las fechas de cada periodo, así que no se valida que una `fecha` pertenezca al periodo declarado
  - **`ReservaCancelada`**: los nombres de `origenCancelacion`, `responsabilidad` y `bloqueoOrigen`, y el hecho de que `antelacionMinutos` no viaje desde aquí, son propuesta de este plan para el Módulo 3
  - **Formato del archivo**: la plantilla de §Decisiones es propuesta; hay que confirmarla con quien produce el horario real