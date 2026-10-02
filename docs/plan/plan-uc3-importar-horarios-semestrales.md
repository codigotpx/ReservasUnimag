# Implementation Plan: Importar horarios semestrales (UC3)

**Date**: 2026-10-02
**Spec**: [spec-modulo2-uc3-importar-horarios-semestrales.md](../specs/spec-modulo2-uc3-importar-horarios-semestrales.md)
**Plan general**: [plan-arquitectura.md](./plan-arquitectura.md)
**Planes previos**: [plan-uc1-consultar-recursos.md](./plan-uc1-consultar-recursos.md) (base compartida) y [plan-uc2-reservar-recursos.md](./plan-uc2-reservar-recursos.md) — este plan **reusa `ReservarRecursosUseCase`** con `origen = ACADEMICO`, la *outbox* y `CalendarioHabil`, que UC2 dejó montados

## Summary

UC3 es el caso de uso que le dice al sistema qué le pertenece a la actividad docente. La Dirección de Programa carga el horario del semestre y cada clase queda apartada; después puede registrar una necesidad extraordinaria que **se impone** sobre las reservas estudiantiles ya confirmadas, cancelándolas con su motivo. De aquí sale toda la prioridad académica del módulo: es lo que alimenta el `BLOQUEO_ACADEMICO` que UC1 muestra y el `RES-001` que UC2 deniega.

**Enfoque técnico:**

1. La carga es un **CSV de clases semanales**, no de sesiones sueltas: una fila dice "Redes, laboratorio ESP-0412, lunes de 08:00 a 10:00". `ExpansorDeSesiones` la convierte en una sesión por semana dentro del periodo académico, omitiendo los días no hábiles con el `CalendarioHabil` que UC2 ya creó.
2. Todo pasa por **dos peticiones**: una valida y no cambia nada, la otra aplica. La primera devuelve el reporte fila por fila (FR-003) y **cuántas y cuáles reservas estudiantiles se cancelarían** (FR-008); la segunda aplica todo en una sola transacción. Sin esa confirmación explícita no se cancela la reserva de nadie.
3. La validación es **todo o nada** (FR-008, escenario 2): una sola fila con error —recurso inexistente, hora mal escrita, choque con otra clase (FR-006), activo ya entregado (FR-010) o recurso en mantenimiento— y no se carga nada.
4. Cada sesión que sobrevive entra por `ReservarRecursosUseCase` con `origen = ACADEMICO` (P-06), así que pasa igual por `Actualizar estado de los recursos` (UC7) y por `Reportar información de la reserva` (UC10) sin código nuevo.
5. Cuando una sesión desplaza una reserva estudiantil, la cancelación y el bloqueo ocurren en **la misma transacción**, de modo que la restricción de exclusión nunca vea las dos `CONFIRMADA` a la vez. Cada cancelación sale hacia el Módulo 3 por la *outbox* (UC11).
6. Recargar el mismo horario **no duplica nada** (FR-007, SC-003): cada sesión lleva una huella `clave_sesion`, y una recarga reconcilia —crea las nuevas, deja intactas las que no cambiaron y propone cancelar las que desaparecieron del archivo.

De UC4 y UC11 este plan implementa solo lo que necesita: la cancelación por prioridad académica y el evento de cancelación hacia el Módulo 3. El plan de UC4 completará la cancelación que hace el titular, con sus códigos `CAN-001` y `CAN-002`.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript con Next.js y React (frontend)
**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Validation, RestClient), Spring for Apache Kafka, **Apache Commons CSV** (nuevo en este plan), Flyway, springdoc-openapi
**Storage**: PostgreSQL con `btree_gist`. UC3 **escribe** `horario_semestral`, `carga_horario_fila`, `bloqueo_academico`, `reserva` y `mensaje_saliente`, y lee `prestamo` y `usuario`.
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
│   │   ├── bloqueo/
│   │   │   ├── ClaseSemanal.java                   # una fila del archivo, ya validada
│   │   │   ├── SesionDeClase.java                  # una ocurrencia concreta con fecha
│   │   │   ├── DatosAcademicos.java                # asignatura, código, grupo, programa, docente
│   │   │   ├── TipoBloqueo.java                    # REGULAR, EXTRAORDINARIO
│   │   │   ├── BloqueoAcademico.java
│   │   │   ├── ClaveSesion.java                    # la huella que hace idempotente la recarga
│   │   │   └── HorarioSemestral.java               # la carga y su resultado
│   │   ├── carga/
│   │   │   ├── CargaHorario.java                   # estado de la carga en curso
│   │   │   ├── EstadoCarga.java                    # VALIDADA, APLICADA, RECHAZADA, EXPIRADA
│   │   │   ├── FilaValidada.java                   # fila + dictamen + sesiones expandidas
│   │   │   ├── DictamenFila.java                   # los siete dictámenes de la tabla
│   │   │   ├── ReporteDeImportacion.java           # FR-003
│   │   │   └── ImpactoDeCarga.java                 # FR-008: qué se cancelaría
│   │   ├── reserva/
│   │   │   └── MotivoCancelacion.java              # nuevo: PRIORIDAD_ACADEMICA, TITULAR, ...
│   │   └── error/
│   │       ├── CargaNoAplicableException.java
│   │       └── ArchivoInvalidoException.java
│   ├── port/
│   │   ├── in/
│   │   │   ├── ValidarHorarioPort.java             # paso 1: no cambia nada
│   │   │   ├── AplicarHorarioPort.java             # paso 2: aplica
│   │   │   ├── RegistrarNecesidadExtraordinariaPort.java
│   │   │   └── CancelarReservaPort.java            # UC4: solo la rama académica
│   │   └── out/
│   │       ├── CargaHorarioRepositoryPort.java
│   │       ├── BloqueoRepositoryPort.java          # bloqueos por clave de sesión y por periodo
│   │       ├── LectorDeHorarios.java               # parsea el archivo → filas crudas
│   │       ├── ReservaRepositoryPort.java          # (existe) + cancelar en lote
│   │       ├── InventarioPort.java                 # (existe) usa estadoOperativo por lote
│   │       ├── NotificadorModulo3Port.java         # (existe) + evento de cancelación
│   │       └── CalendarioHabil.java                # (existe, de UC2)
│   └── usecase/
│       ├── importarhorarios/
│       │   ├── ValidarHorarioUseCase.java
│       │   ├── AplicarHorarioUseCase.java
│       │   ├── RegistrarNecesidadExtraordinariaUseCase.java
│       │   ├── ExpansorDeSesiones.java             # clase semanal → sesiones del periodo
│       │   ├── ClasificadorDeFilas.java            # asigna el dictamen a cada fila
│       │   └── ReconciliadorDeHorario.java         # FR-007: recarga sin duplicar
│       ├── cancelarreserva/
│       │   └── CancelarReservaUseCase.java         # solo prioridad académica (UC4 FR-004 a FR-006)
│       └── reservarrecursos/
│           └── ReservarRecursosUseCase.java        # (existe) se reusa con origen ACADEMICO
│
└── infrastructure/
    ├── adapter/
    │   ├── in/web/horario/
    │   │   ├── HorarioController.java              # los cuatro endpoints
    │   │   ├── ValidarHorarioRequest.java          # multipart: archivo + periodo
    │   │   ├── ReporteImportacionResponse.java
    │   │   ├── NecesidadExtraordinariaRequest.java
    │   │   └── CargaResponse.java
    │   └── out/
    │       ├── persistence/
    │       │   ├── entity/                         # + HorarioSemestralJpa, CargaHorarioFilaJpa,
    │       │   │                                   #   BloqueoAcademicoJpa
    │       │   ├── repository/                     # + los Spring Data de esas tres
    │       │   ├── CargaHorarioPersistenceAdapter.java
    │       │   └── BloqueoPersistenceAdapter.java
    │       ├── archivo/
    │       │   └── LectorCsvDeHorarios.java        # → LectorDeHorarios
    │       └── mensajeria/
    │           └── evento/EventoReservaCancelada.java   # UC11
    └── config/
        ├── PropiedadesReservas.java                # (existe) + vigencia de la carga validada
        └── CasosDeUsoConfig.java                   # (existe) + los beans de UC3

src/main/resources/db/migration/
└── V4__carga_horario.sql                           # horario_semestral, carga_horario_fila,
                                                    # bloqueo_academico y la clave de sesión

src/test/java/edu/unimagdalena/reservasunimag/
├── domain/usecase/importarhorarios/
│   ├── ExpansorDeSesionesTest.java
│   ├── ClasificadorDeFilasTest.java
│   ├── ValidarHorarioUseCaseTest.java
│   ├── AplicarHorarioUseCaseTest.java
│   └── ReconciliadorDeHorarioTest.java
├── domain/usecase/cancelarreserva/CancelarReservaUseCaseTest.java
└── infrastructure/adapter/
    ├── in/web/horario/HorarioControllerTest.java
    ├── out/archivo/LectorCsvDeHorariosTest.java
    ├── out/persistence/CargaHorarioPersistenceAdapterIT.java
    └── out/persistence/PrioridadAcademicaIT.java

src/test/resources/contratos/                       # + los JSON y el CSV de la sección Contratos

frontend/src/
├── app/(direccion)/horarios/page.tsx               # cargar, revisar el reporte y confirmar
├── app/(direccion)/horarios/extraordinaria/page.tsx
└── features/horarios/
    ├── SubirArchivo.tsx
    ├── ReporteImportacion.tsx                      # tabla fila por fila
    ├── ConfirmarImpacto.tsx                        # las reservas que se cancelarían
    ├── FormularioExtraordinaria.tsx
    ├── api.ts
    └── tipos.ts
```

**Structure Decision**: se mantiene la estructura del plan general. Tres añadidos: el paquete `domain/model/carga`, porque la carga validada es un objeto de negocio con estado propio y no un detalle del controlador; el adaptador `out/archivo`, primera salida del hexágono que no es base de datos ni HTTP —el parseo del CSV es tecnología y no puede vivir en `domain`—; y el grupo `(direccion)` del frontend, que el plan general ya preveía y aquí se usa por primera vez.

### Decisiones de diseño de este caso de uso

**El archivo trae clases semanales, no sesiones.** Una fila dice "Redes, ESP-0412, lunes de 08:00 a 10:00" y el sistema la expande a una sesión por semana del periodo. La alternativa —una fila por sesión— obligaría a escribir a mano dieciséis filas por clase, y un horario de cientos de clases sería un archivo de miles de líneas mantenido a mano. La expansión es también lo que hace que SC-001 tenga sentido: el trabajo del sistema es grande, el del archivo es pequeño. El periodo (`periodoInicio` y `periodoFin`) va en la petición, no en el archivo, porque es el mismo para todas las filas. (NEEDS CLARIFICATION: el spec dice "qué asignatura, en qué salón, qué día y a qué hora" sin fijar el formato; si la Dirección de Programa exporta sesiones ya expandidas, cambia solo `LectorCsvDeHorarios`.)

**Los días no hábiles no tienen clase.** La expansión salta sábados, domingos y festivos usando el `CalendarioHabil` de UC2, así que una clase de lunes no genera sesión el lunes festivo. Es la misma lista de `application.properties` y arrastra el mismo pendiente: nadie nos ha dado el calendario de la universidad.

**Dos peticiones: validar y aplicar.** FR-008 pide mostrar el impacto y esperar antes de cancelar nada, así que no cabe en una sola llamada:

| Paso | Endpoint | Qué hace | Qué cambia |
|---|---|---|---|
| 1 | `POST /api/horarios/validaciones` | Lee el archivo, expande, clasifica cada fila y calcula qué reservas se cancelarían | Guarda la carga como `VALIDADA` y sus filas; **ninguna reserva se toca** |
| 2 | `POST /api/horarios/{cargaId}/confirmacion` | Vuelve a comprobar y aplica todo en una transacción | Crea los bloqueos y cancela las reservas desplazadas |

La carga validada se guarda —no se le pide al navegador que reenvíe el archivo— por dos razones: el reporte que la persona aprobó es exactamente lo que se aplica, y queda la constancia de FR-009 aunque nunca se confirme. Caduca a los `reservas.carga.vigencia` (30 minutos por defecto): pasado ese plazo queda `EXPIRADA` y hay que volver a validar, porque el impacto que se mostró ya no es de fiar.

**Ocho dictámenes por fila.** `ClasificadorDeFilas` le pone a cada fila uno de estos, y de ellos sale si la carga es aplicable:

| Dictamen | Cuándo | ¿Bloquea la carga? |
|---|---|---|
| `OK` | La franja está libre | No |
| `SIN_CAMBIO` | Ya existe un bloqueo con la misma `clave_sesion` (FR-007) | No |
| `DESPLAZA_RESERVAS` | Se cruza con reservas estudiantiles cancelables (FR-005) | No, pero exige la confirmación de FR-008 |
| `RECHAZADA_FORMATO` | Hora mal escrita, día inválido, columna faltante (escenario 2) | **Sí** |
| `RECHAZADA_RECURSO` | El `recurso_id` no existe en el Módulo 1 (escenario 2) | **Sí** |
| `CHOQUE_ACADEMICO` | Ya hay otro `BLOQUEO_ACADEMICO` cruzado (FR-006, escenario 4) | **Sí** |
| `CHOQUE_ACTIVO_ENTREGADO` | El activo ya está en manos de alguien (FR-010, segundo caso) | **Sí** |
| `CHOQUE_MANTENIMIENTO` | El Módulo 1 reporta `EN_MANTENIMIENTO` (edge case **Recurso dado de baja**) | **Sí** |

**Decisión: un choque bloquea la carga entera, igual que un error de formato.** El escenario 2 solo habla de filas con error, y FR-006 y FR-010 dicen "avisa del choque y pide que lo resuelvan las personas responsables". Aplicar las demás filas y callar el choque dejaría una clase sin espacio sin que nadie se enterara, que es justo lo que este caso de uso existe para evitar; y aplicar a medias contradice el todo-o-nada de FR-008. Así que la carga es aplicable **solo** si ninguna fila tiene un dictamen bloqueante. El reporte los lista todos de una vez —no se para en el primero— para poder corregir el archivo en una sola pasada.

**Un activo prestado se trata según si ya salió del inventario (FR-010).** Es la única regla donde el mismo choque tiene dos desenlaces:

| Situación del activo | Dictamen | Por qué |
|---|---|---|
| Apartado, sin recoger (`prestamo.entregado_en` nulo) | `DESPLAZA_RESERVAS` | Se cancela como cualquier reserva y el activo queda libre para la clase |
| Ya entregado y sin devolver | `CHOQUE_ACTIVO_ENTREGADO` | La prioridad académica desplaza lo que no ha salido; lo que ya salió hay que pedirlo de vuelta, y eso no lo puede hacer el sistema |

**La huella que hace idempotente la recarga (FR-007, SC-003).** Cada sesión lleva una `clave_sesion`: el hash de `periodo_academico`, `recurso_id`, `inicio`, `fin`, `codigo_asignatura` y `grupo`. Va en `bloqueo_academico` con un índice **único**, así que la base misma impide el duplicado. El truco para que funcione con las cancelaciones: al cancelar un bloqueo la columna se pone a `NULL`, y Postgres no considera iguales dos `NULL` en un índice único, de modo que la misma clase se puede volver a cargar después de retirarla.

```sql
ALTER TABLE bloqueo_academico ADD COLUMN clave_sesion varchar;
CREATE UNIQUE INDEX bloqueo_academico_clave_sesion ON bloqueo_academico (clave_sesion);
```

**Reconciliar una recarga.** Volver a cargar el mismo `periodo_academico` no es empezar de cero: `ReconciliadorDeHorario` compara las claves del archivo con los bloqueos vigentes del periodo y reparte en tres montones — las que ya están (`SIN_CAMBIO`, no se tocan), las nuevas (se crean) y las que **estaban y ya no vienen**, que se proponen para cancelar y entran en el impacto que hay que confirmar. Retirar una clase es una cancelación de una reserva de origen académico y se le reporta al Módulo 3 por la *outbox*, igual que cualquier otra. (NEEDS CLARIFICATION: P-19 pregunta exactamente esto —"si un cambio de horario retira una clase... no hay nada escrito sobre cómo se le avisa"—; aquí se asume que sí se le avisa, porque ya recibió su ficha.)

**Cancelar y bloquear en la misma transacción (escenario 3, FR-008).** La restricción `reserva_sin_cruces` no admite dos `CONFIRMADA` solapadas, así que el orden importa y no puede partirse:

1. `SELECT ... FOR UPDATE` de las reservas estudiantiles que se van a desplazar, por `recurso_id`, para que nadie confirme una nueva en medio.
2. Recomprobar que el impacto sigue siendo el que se mostró; si cambió, la carga se rechaza con `409` y hay que volver a validar.
3. Cancelar esas reservas con `CANCELADA_POR_PRIORIDAD_ACADEMICA` y su `cancelada_en`.
4. Crear cada sesión llamando a `ReservarRecursosUseCase` con `origen = ACADEMICO`.
5. Insertar en `mensaje_saliente` un evento de cancelación por cada reserva desplazada (UC11) —las fichas de los bloqueos las inserta UC2 por su cuenta.

Una carga grande hace esto en **una sola transacción**, que es lo que FR-008 exige. Para que quepa en el presupuesto de SC-001, los pasos 3 y 4 van por lotes y la comprobación de cruces es una sola consulta por lote de recursos, no una por fila.

**Qué reglas se salta el origen académico.** Las mismas que ya asumió el plan de UC2: sanción, cupo de 3 y tope de 2 horas —una clase de 3 horas es normal—. UC3 agrega que una sesión tampoco tiene titular, así que `usuario_id` va nulo y el `CHECK` de la tabla lo exige. (NEEDS CLARIFICATION: sigue siendo P-19.)

**Una sola llamada al Módulo 1 por carga.** El catálogo no se pide fila por fila: se juntan todos los `recurso_id` distintos del archivo y se usa la operación por lote de [UC1 § Contratos §2.2](./plan-uc1-consultar-recursos.md#2-módulo-1--inventarioport), que hasta ahora no tenía consumidor real. De ahí salen a la vez los `RECHAZADA_RECURSO` (los que vuelven en `noEncontrados`) y los `CHOQUE_MANTENIMIENTO`. Si el Módulo 1 no responde, la validación falla completa con `503`: no se puede bloquear un salón sin saber si existe.

**La necesidad extraordinaria es la misma maquinaria con una sola fila.** `RegistrarNecesidadExtraordinariaUseCase` no repite nada: arma una `SesionDeClase` suelta con `TipoBloqueo.EXTRAORDINARIO` y `horario_semestral_id` nulo, la pasa por el mismo clasificador y, si desplaza reservas, por la misma confirmación. La diferencia está en la respuesta: al ser una sola franja, el impacto se muestra en la misma petición y se aplica con `confirmar: true` en lugar de guardar una carga (ver [Contratos §3](#3-post-apihorariosextraordinarias)).

## Contratos

Aquí queda definido todo el JSON de UC3: los cuatro endpoints de Dirección de Programa, el formato del archivo, lo que se le pregunta al Módulo 1 y el evento de cancelación que sale hacia el Módulo 3. Los ejemplos son el contrato: las pruebas de T019, T020 y T023 se escriben contra ellos y los mismos JSON viven como *fixtures* en `src/test/resources/contratos/`.

Se aplican sin repetirlas las **convenciones comunes** de [plan-uc1-consultar-recursos.md § Contratos](./plan-uc1-consultar-recursos.md#contratos). Cuatro reglas propias de este caso de uso:

| Regla | Detalle |
|---|---|
| Solo Dirección de Programa | Los cuatro endpoints exigen el rol `DIRECCION_PROGRAMA`. Un `ESTUDIANTE` o `MONITOR` con sesión válida recibe `403`, no `401`. |
| Validar nunca cambia nada | `POST /api/horarios/validaciones` es seguro de repetir: guarda la carga y su reporte, pero no crea bloqueos ni cancela reservas. Lo único que cambia es `horario_semestral` y sus filas. |
| El reporte habla de filas, no de sesiones | Los dictámenes y los errores se reportan **por fila del archivo**, que es lo que la persona puede corregir. Las sesiones expandidas se cuentan, pero no se listan una por una: un semestre son miles. |
| `fila` es el número real del archivo | Empieza en 2, porque la 1 es la cabecera. Así el mensaje de error se puede buscar directamente en el CSV. |

---

### 1. `POST /api/horarios/validaciones`

Paso 1 de la carga. `multipart/form-data`, porque va un archivo:

| Parte | Tipo | Obligatorio | Nota |
|---|---|---|---|
| `archivo` | archivo CSV | sí | El formato está en [§5](#5-formato-del-archivo-csv). Máximo `reservas.carga.tamano-maximo` (2 MB por defecto). |
| `periodoAcademico` | texto | sí | Por ejemplo `2026-2`. Es la clave con la que se reconcilia una recarga (FR-007). |
| `periodoInicio` | `yyyy-MM-dd` | sí | Primer día de clases del periodo. |
| `periodoFin` | `yyyy-MM-dd` | sí | Último día; debe ser posterior a `periodoInicio`. |

**`200 OK`** — el archivo está limpio y hay reservas que se desplazarían (escenario 3 + edge case **Carga tardía del horario**)

```json
{
  "cargaId": "7e2a4c81-3f95-4d60-b8a7-1c9e5d0f2a63",
  "periodoAcademico": "2026-2",
  "periodo": { "inicio": "2026-08-10", "fin": "2026-12-05" },
  "estado": "VALIDADA",
  "validadaEn": "2026-10-02T15:40:11-05:00",
  "expiraEn": "2026-10-02T16:10:11-05:00",
  "aplicable": true,
  "requiereConfirmacion": true,
  "resumen": {
    "filasLeidas": 312,
    "filasOk": 300,
    "filasSinCambio": 12,
    "filasConDictamenBloqueante": 0,
    "sesionesPorCrear": 4680,
    "sesionesOmitidasPorDiaNoHabil": 96,
    "bloqueosPorRetirar": 0,
    "reservasPorCancelar": 3
  },
  "filas": [
    {
      "fila": 2,
      "recursoId": "ESP-0412",
      "nombreRecurso": "Laboratorio de Redes",
      "asignatura": "Redes de Computadores",
      "codigoAsignatura": "IS-402",
      "grupo": "1",
      "diaSemana": "LUNES",
      "inicio": "08:00",
      "fin": "10:00",
      "dictamen": "OK",
      "sesiones": 16
    },
    {
      "fila": 7,
      "recursoId": "ESP-0301",
      "nombreRecurso": "Auditorio Menor",
      "asignatura": "Seminario de Investigación",
      "codigoAsignatura": "IS-510",
      "grupo": "1",
      "diaSemana": "JUEVES",
      "inicio": "14:00",
      "fin": "16:00",
      "dictamen": "DESPLAZA_RESERVAS",
      "sesiones": 16,
      "reservasAfectadas": [
        {
          "reservaId": "a1b2c3d4-5e6f-4789-9abc-def012345678",
          "titular": { "codigo": "2019114045", "nombre": "Nombre del estudiante" },
          "inicio": "2026-09-10T14:00:00-05:00",
          "fin": "2026-09-10T16:00:00-05:00",
          "categoria": "ESPACIO"
        }
      ]
    },
    {
      "fila": 19,
      "recursoId": "ESP-0107",
      "asignatura": "Cálculo I",
      "codigoAsignatura": "MA-101",
      "grupo": "3",
      "diaSemana": "MARTES",
      "inicio": "06:00",
      "fin": "08:00",
      "dictamen": "SIN_CAMBIO",
      "sesiones": 16
    }
  ]
}
```

| Campo | Nota |
|---|---|
| `cargaId` | Lo que se le pasa al paso 2. Identifica esta validación, no el periodo. |
| `aplicable` | `false` si alguna fila tiene un dictamen bloqueante. El paso 2 lo rechaza. |
| `requiereConfirmacion` | `true` cuando `reservasPorCancelar` o `bloqueosPorRetirar` son mayores que 0 (FR-008). Si es `false`, el paso 2 sigue siendo obligatorio, pero la pantalla no tiene que advertir nada. |
| `expiraEn` | Pasado ese instante la carga queda `EXPIRADA` y hay que volver a validar, porque el impacto mostrado dejó de ser fiable. |
| `resumen.sesionesOmitidasPorDiaNoHabil` | Las sesiones que caían en sábado, domingo o festivo. Se informa para que nadie piense que se perdieron filas. |
| `filas[].sesiones` | Cuántas ocurrencias genera esa fila en el periodo. |
| `filas[].reservasAfectadas` | Solo en `DESPLAZA_RESERVAS`. Lleva el titular **con nombre**, porque FR-008 pide mostrar *cuáles* se cancelarían y quien decide es Dirección de Programa, que ya tiene esa información. Es la única respuesta del módulo que nombra al titular de una reserva, y por eso solo este rol la ve. |

**`200 OK`** — el archivo tiene problemas y la carga no se puede aplicar (escenario 2, 4 y edge cases)

```json
{
  "cargaId": "b4d9f0c2-7a16-4e38-95bd-2f8c1e0a7d45",
  "periodoAcademico": "2026-2",
  "periodo": { "inicio": "2026-08-10", "fin": "2026-12-05" },
  "estado": "RECHAZADA",
  "validadaEn": "2026-10-02T15:44:02-05:00",
  "aplicable": false,
  "requiereConfirmacion": false,
  "resumen": {
    "filasLeidas": 312,
    "filasOk": 307,
    "filasSinCambio": 0,
    "filasConDictamenBloqueante": 5,
    "sesionesPorCrear": 0,
    "sesionesOmitidasPorDiaNoHabil": 0,
    "bloqueosPorRetirar": 0,
    "reservasPorCancelar": 0
  },
  "filas": [
    {
      "fila": 23,
      "recursoId": "ESP-9999",
      "asignatura": "Física II",
      "codigoAsignatura": "FI-202",
      "grupo": "2",
      "diaSemana": "MIERCOLES",
      "inicio": "10:00",
      "fin": "12:00",
      "dictamen": "RECHAZADA_RECURSO",
      "sesiones": 0,
      "problema": "El recurso ESP-9999 no existe en el inventario."
    },
    {
      "fila": 41,
      "recursoId": "ESP-0412",
      "asignatura": "Sistemas Operativos",
      "codigoAsignatura": "IS-404",
      "grupo": "1",
      "diaSemana": "LUNES",
      "inicio": "9:3",
      "fin": "11:30",
      "dictamen": "RECHAZADA_FORMATO",
      "sesiones": 0,
      "problema": "La columna hora_inicio no tiene el formato HH:mm.",
      "columna": "hora_inicio"
    },
    {
      "fila": 58,
      "recursoId": "ESP-0412",
      "asignatura": "Arquitectura de Computadores",
      "codigoAsignatura": "IS-301",
      "grupo": "1",
      "diaSemana": "LUNES",
      "inicio": "09:00",
      "fin": "11:00",
      "dictamen": "CHOQUE_ACADEMICO",
      "sesiones": 0,
      "problema": "Se cruza con otra clase ya bloqueada en este recurso: IS-402 grupo 1, lunes de 08:00 a 10:00.",
      "bloqueoEnConflicto": { "asignatura": "Redes de Computadores", "codigoAsignatura": "IS-402", "grupo": "1" }
    },
    {
      "fila": 77,
      "recursoId": "ACT-004512",
      "asignatura": "Dibujo Técnico",
      "codigoAsignatura": "IC-110",
      "grupo": "1",
      "diaSemana": "VIERNES",
      "inicio": "14:00",
      "fin": "16:00",
      "dictamen": "CHOQUE_ACTIVO_ENTREGADO",
      "sesiones": 0,
      "problema": "El activo ya está en manos de una persona hasta el 2026-09-12 a las 22:00. Hay que pedirlo de vuelta antes de bloquearlo para la clase."
    },
    {
      "fila": 92,
      "recursoId": "ESP-0220",
      "asignatura": "Química General",
      "codigoAsignatura": "QU-101",
      "grupo": "1",
      "diaSemana": "MARTES",
      "inicio": "08:00",
      "fin": "10:00",
      "dictamen": "CHOQUE_MANTENIMIENTO",
      "sesiones": 0,
      "problema": "El recurso está en mantenimiento. Esta clase necesita otro espacio."
    }
  ]
}
```

`CHOQUE_ACTIVO_ENTREGADO` dice **hasta cuándo** está comprometido el activo, pero no quién lo tiene: ahí sí aplica UC8 FR-005, porque es información que no hace falta para decidir. `CHOQUE_ACADEMICO` nombra la asignatura en conflicto, no al docente.

El `estado: "RECHAZADA"` queda guardado: la constancia de FR-009 vale también para una carga que no se pudo aplicar.

**Errores**

| Código | Cuándo |
|---|---|
| `400` con `codigo: "ARCHIVO_VACIO"` | El CSV no trae ninguna fila de datos (edge case **Archivo vacío**). Nunca se responde con un resumen de éxito. |
| `400` con `codigo: "CABECERA_INVALIDA"` | Faltan columnas obligatorias o el archivo no es un CSV legible; lleva `columnasFaltantes`. |
| `400` con `codigo: "PERIODO_INVALIDO"` | `periodoFin` no es posterior a `periodoInicio`. |
| `401` / `403` | Sin sesión / con sesión pero sin el rol `DIRECCION_PROGRAMA`. |
| `413` | El archivo pasa de `reservas.carga.tamano-maximo`. |
| `503` con `modulo: "MODULO_1"` | El inventario no respondió y no se puede validar ni una fila. |

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errores/archivo-invalido",
  "title": "Archivo de horario inválido",
  "status": 400,
  "detail": "El archivo no trae ninguna clase. No se cargó nada.",
  "instance": "/api/horarios/validaciones",
  "codigo": "ARCHIVO_VACIO"
}
```

---

### 2. `POST /api/horarios/{cargaId}/confirmacion`

Paso 2. Sin cuerpo: lo que se aplica es exactamente la carga que se validó y que la persona acaba de revisar.

**`200 OK`**

```json
{
  "cargaId": "7e2a4c81-3f95-4d60-b8a7-1c9e5d0f2a63",
  "periodoAcademico": "2026-2",
  "estado": "APLICADA",
  "aplicadaEn": "2026-10-02T15:47:35-05:00",
  "aplicadaPor": { "usuarioId": "3c7d1e9a-4b72-4f08-a561-8d2e0c9f4b13", "nombre": "Dirección de Programa" },
  "resultado": {
    "bloqueosCreados": 4680,
    "bloqueosSinCambio": 192,
    "bloqueosRetirados": 0,
    "reservasCanceladas": 3,
    "eventosEncolados": 3
  }
}
```

`eventosEncolados` son los de cancelación que quedaron en la *outbox* (UC11); las fichas de los bloqueos las encola `ReservarRecursosUseCase` por su cuenta y no se cuentan aquí. El `resultado` se guarda en `horario_semestral.resultado`, que es la columna `jsonb` que el plan general ya preveía, así que esta misma respuesta es lo que queda como constancia de FR-009.

**Errores**

| Código | `codigo` | Cuándo |
|---|---|---|
| `404` | — | El `cargaId` no existe. |
| `409` | `CARGA_NO_APLICABLE` | La validación había dado `aplicable: false`. Lleva `filasConDictamenBloqueante`. |
| `409` | `CARGA_EXPIRADA` | Pasó la vigencia; hay que volver a validar. Lleva `expiroEn`. |
| `409` | `CARGA_YA_APLICADA` | Ya se confirmó. Lleva `aplicadaEn`, y la petición es inocua: no se aplica dos veces. |
| `409` | `IMPACTO_CAMBIO` | Entre validar y confirmar aparecieron o desaparecieron reservas afectadas. Lleva `reservasPorCancelarAhora` frente a `reservasPorCancelarAlValidar`. |
| `403` | — | Otro rol, o un usuario distinto del que validó. |
| `503` | — | El Módulo 1 no respondió en la recomprobación. |

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errores/carga-no-aplicable",
  "title": "La carga no se puede aplicar",
  "status": 409,
  "detail": "El impacto cambió desde que se validó: ahora se cancelarían 5 reservas en vez de 3. Vuelve a validar el archivo.",
  "instance": "/api/horarios/7e2a4c81-3f95-4d60-b8a7-1c9e5d0f2a63/confirmacion",
  "codigo": "IMPACTO_CAMBIO",
  "reservasPorCancelarAlValidar": 3,
  "reservasPorCancelarAhora": 5
}
```

**Decisión: `IMPACTO_CAMBIO` rechaza en vez de aplicar.** FR-008 dice que se muestra lo que se cancelaría y *luego* se aplica; si entre las dos cosas el impacto creció, lo que se aplicaría no es lo que se aprobó. Se prefiere la molestia de revalidar a cancelarle la reserva a alguien que no estaba en la lista.

---

### 3. `POST /api/horarios/extraordinarias`

La necesidad extraordinaria: una sola franja, sin archivo. Como es una, el impacto se devuelve en la misma petición y se aplica con `confirmar`.

```json
{
  "recursoId": "ESP-0301",
  "fecha": "2026-09-10",
  "inicio": "14:00",
  "fin": "16:00",
  "asignatura": "Visita de pares académicos",
  "codigoAsignatura": "EXT-001",
  "grupo": "1",
  "programa": "Ingeniería de Sistemas",
  "docente": "Nombre del docente",
  "confirmar": false
}
```

Con `confirmar: false` (el valor por defecto) **no cambia nada** y responde `200` con lo que pasaría:

```json
{
  "aplicado": false,
  "dictamen": "DESPLAZA_RESERVAS",
  "recursoId": "ESP-0301",
  "nombreRecurso": "Auditorio Menor",
  "inicio": "2026-09-10T14:00:00-05:00",
  "fin": "2026-09-10T16:00:00-05:00",
  "reservasAfectadas": [
    {
      "reservaId": "a1b2c3d4-5e6f-4789-9abc-def012345678",
      "titular": { "codigo": "2019114045", "nombre": "Nombre del estudiante" },
      "inicio": "2026-09-10T14:00:00-05:00",
      "fin": "2026-09-10T16:00:00-05:00",
      "categoria": "ESPACIO"
    }
  ]
}
```

Con `confirmar: true` se aplica y responde `201` (escenario 3):

```http
HTTP/1.1 201 Created
Location: /api/reservas/d5e8f1a2-6b94-4c07-8d31-0f7a2e9c5b48
```

```json
{
  "aplicado": true,
  "dictamen": "DESPLAZA_RESERVAS",
  "bloqueo": {
    "reservaId": "d5e8f1a2-6b94-4c07-8d31-0f7a2e9c5b48",
    "recursoId": "ESP-0301",
    "tipo": "EXTRAORDINARIO",
    "inicio": "2026-09-10T14:00:00-05:00",
    "fin": "2026-09-10T16:00:00-05:00",
    "asignatura": "Visita de pares académicos",
    "programa": "Ingeniería de Sistemas",
    "docente": "Nombre del docente"
  },
  "reservasCanceladas": 1,
  "eventosEncolados": 1
}
```

Un bloqueo extraordinario es una reserva como las demás, así que su `reservaId` sirve en `GET /api/reservas/{id}` y su `Location` apunta ahí, no a una ruta de horarios.

**Errores**: `400` si la franja cae fuera de 06:00–22:00 o cruza la medianoche —el tope de 2 horas **no** aplica, por ser académica—, `404` si el recurso no existe, `409` con `dictamen` `CHOQUE_ACADEMICO`, `CHOQUE_ACTIVO_ENTREGADO` o `CHOQUE_MANTENIMIENTO` cuando la franja no se puede tomar (FR-006, FR-010), `403` si el rol no es Dirección de Programa y `503` si el Módulo 1 no responde.

```json
{
  "type": "https://reservasunimag.unimagdalena.edu.co/errores/bloqueo-en-conflicto",
  "title": "La franja ya tiene actividad docente",
  "status": 409,
  "detail": "Se cruza con la clase IS-402 grupo 1, de 08:00 a 10:00. Una clase no desplaza a otra: resuélvanlo entre los responsables.",
  "instance": "/api/horarios/extraordinarias",
  "dictamen": "CHOQUE_ACADEMICO",
  "bloqueoEnConflicto": { "asignatura": "Redes de Computadores", "codigoAsignatura": "IS-402", "grupo": "1" }
}
```

---

### 4. `GET /api/horarios`  y  `GET /api/horarios/{cargaId}`

El histórico, que es la cara consultable de la constancia de FR-009.

```json
{
  "cargas": [
    {
      "cargaId": "7e2a4c81-3f95-4d60-b8a7-1c9e5d0f2a63",
      "periodoAcademico": "2026-2",
      "estado": "APLICADA",
      "nombreArchivo": "horario-2026-2.csv",
      "cargadoPor": { "usuarioId": "3c7d1e9a-4b72-4f08-a561-8d2e0c9f4b13", "nombre": "Dirección de Programa" },
      "validadaEn": "2026-10-02T15:40:11-05:00",
      "aplicadaEn": "2026-10-02T15:47:35-05:00",
      "resultado": { "bloqueosCreados": 4680, "bloqueosSinCambio": 192, "bloqueosRetirados": 0, "reservasCanceladas": 3 }
    }
  ],
  "paginacion": { "pagina": 1, "tamanoPagina": 20, "totalPaginas": 1, "total": 1 }
}
```

`GET /api/horarios/{cargaId}` devuelve ese mismo objeto más el `filas[]` de la validación, con la misma forma de [§1](#1-post-apihorariosvalidaciones), para poder volver a mirar un reporte sin repetir la carga.

---

### 5. Formato del archivo CSV

CSV con cabecera, UTF-8, separador coma, comillas dobles para los campos que contengan comas. **Una fila es una clase semanal**, no una sesión.

```csv
codigo_asignatura,asignatura,grupo,programa,docente,recurso_id,dia_semana,hora_inicio,hora_fin
IS-402,Redes de Computadores,1,Ingeniería de Sistemas,Nombre del docente,ESP-0412,LUNES,08:00,10:00
IS-510,Seminario de Investigación,1,Ingeniería de Sistemas,Nombre del docente,ESP-0301,JUEVES,14:00,16:00
IC-110,"Dibujo Técnico, taller",1,Ingeniería Civil,Nombre del docente,ESP-0150,VIERNES,14:00,16:00
```

| Columna | Obligatoria | Validación |
|---|---|---|
| `codigo_asignatura` | sí | No vacía. Entra en la `clave_sesion`. |
| `asignatura` | sí | No vacía. |
| `grupo` | sí | No vacía. Entra en la `clave_sesion`: dos grupos de la misma asignatura son clases distintas. |
| `programa` | sí | No vacía. |
| `docente` | no | Puede ir vacía si todavía no se asignó. |
| `recurso_id` | sí | Debe existir en el Módulo 1. |
| `dia_semana` | sí | `LUNES` a `SABADO`; `DOMINGO` se rechaza. Sin tildes y en mayúsculas. |
| `hora_inicio`, `hora_fin` | sí | `HH:mm`, dentro de 06:00–22:00, `hora_fin > hora_inicio`, el mismo día. |

El `periodoAcademico` y las fechas del periodo **no** van en el archivo: viajan en la petición, porque son iguales para todas las filas y así el mismo archivo sirve para otro periodo.

Una fila con `dia_semana: SABADO` es válida —hay clases los sábados—, pero sus sesiones se omiten si el sábado está en la lista de días no hábiles. Una columna extra en el CSV se ignora; una obligatoria que falte es `CABECERA_INVALIDA` y la carga no empieza.

**Decisión: CSV y no Excel.** Un `.xlsx` obligaría a sumar Apache POI y a tratar con celdas con formato de hora, que es donde aparecen los errores difíciles de explicar. Excel exporta a CSV en dos clics, y el reporte de [§1](#1-post-apihorariosvalidaciones) señala la fila y la columna exactas. (NEEDS CLARIFICATION: si la Dirección de Programa solo puede entregar `.xlsx`, cambia `LectorCsvDeHorarios` y nada más; el puerto `LectorDeHorarios` existe para eso.)

---

### 6. Módulo 1 — estado operativo por lote

UC3 no define nada nuevo: usa la operación `POST /api/v1/recursos/estado-operativo` de [UC1 § Contratos §2.2](./plan-uc1-consultar-recursos.md#2-módulo-1--inventarioport), y es su primer consumidor real. Se le manda **una sola vez** la lista de `recurso_id` distintos del archivo:

```json
{ "recursoIds": ["ESP-0412", "ESP-0301", "ESP-0150", "ESP-9999"] }
```

```json
{
  "estados": [
    { "recursoId": "ESP-0412", "estadoOperativo": "DISPONIBLE" },
    { "recursoId": "ESP-0301", "estadoOperativo": "DISPONIBLE" },
    { "recursoId": "ESP-0150", "estadoOperativo": "EN_MANTENIMIENTO", "motivoEstado": "Gotera en el techo" }
  ],
  "noEncontrados": ["ESP-9999"]
}
```

De esa única respuesta salen tres cosas: los `RECHAZADA_RECURSO` (lo que viene en `noEncontrados`), los `CHOQUE_MANTENIMIENTO` y el `nombreRecurso` del reporte. Si el archivo trae más recursos distintos que `reservas.modulo1.lote-estado` (200 por defecto), se parte en varias llamadas y se juntan las respuestas; una sola que falle invalida la validación completa con `503`.

> `motivoEstado` sigue sin propagarse al frontend, igual que en UC1: el texto del `problema` lo escribe el Módulo 2.

---

### 7. Evento de cancelación — `modulo2.reserva.cancelacion.v1`

El `<<include>>` a UC11 que UC3 estrena. Un evento por cada reserva desplazada, insertado en `mensaje_saliente` dentro de la misma transacción que la cancelación, con la clave de partición `reserva_id` y el envoltorio del plan general.

```json
{
  "eventoId": "f2b8c4d1-6e39-4a70-95c8-3d1e7a0f2b56",
  "tipo": "ReservaCancelada",
  "version": 1,
  "ocurridoEn": "2026-10-02T15:47:35-05:00",
  "datos": {
    "reservaId": "a1b2c3d4-5e6f-4789-9abc-def012345678",
    "estado": "CANCELADA_POR_PRIORIDAD_ACADEMICA",
    "origenCancelacion": "PRIORIDAD_ACADEMICA",
    "motivo": "La franja se asignó a actividad docente: IS-510 grupo 1.",
    "imputableALaPersona": false,
    "canceladaEn": "2026-10-02T15:47:35-05:00",
    "recurso": {
      "id": "ESP-0301",
      "nombre": "Auditorio Menor",
      "categoria": "ESPACIO"
    },
    "titular": {
      "usuarioId": "5f1b9c2d-7a34-4e81-b0f6-3c8d1e9a4b72",
      "codigo": "2019114045",
      "nombre": "Nombre del estudiante",
      "rol": "ESTUDIANTE"
    },
    "tiempoLiberado": {
      "inicio": "2026-09-10T14:00:00-05:00",
      "fin": "2026-09-10T16:00:00-05:00"
    }
  }
}
```

| Campo | Por qué está | FR |
|---|---|---|
| `origenCancelacion` | Distingue las tres procedencias: `TITULAR`, `PRIORIDAD_ACADEMICA` y `RECURSO_NO_DISPONIBLE`. UC3 solo produce la segunda. | UC11 FR-003 |
| `imputableALaPersona` | `false` siempre en UC3: una cancelación por prioridad académica no puede computar en contra de nadie. Es el campo del que depende que el Módulo 3 no sancione. | UC4 FR-005, UC11 FR-006 |
| `tiempoLiberado` | La franja completa si era un espacio, y el periodo de préstamo entero si era un activo apartado y sin recoger —nunca un trozo—. | UC11 FR-002 |
| `motivo` | Texto legible que nombra la clase que desplazó la reserva, sin el docente. | UC11 FR-002 |
| `eventoId` | El `mensaje_saliente.id`. Es lo que permite al Módulo 3 descartar una reentrega y lo que cumple "una misma cancelación no se reporta dos veces". | UC11 FR-007 |

`antelacionMinutos`, que UC11 FR-004 pide para la cancelación del titular, **no va** en estos eventos: no tiene sentido en una cancelación que la persona no pidió. Lo agrega el plan de UC4 cuando implemente esa rama.

Si Kafka está caído la carga se aplica igual y los eventos salen después (UC11 FR-008): están en la *outbox*, que es la misma de UC2.

**Retirar una clase también emite este evento**, con `estado: "CANCELADA_POR_PRIORIDAD_ACADEMICA"` y un `motivo` que dice que la clase salió del horario. (NEEDS CLARIFICATION: P-19 no cierra si el Módulo 3 espera este aviso; se emite porque ya recibió la ficha del bloqueo.)

---

### 8. Tipos del frontend

En `frontend/src/features/horarios/tipos.ts`. Reusa `Categoria` y `ErrorApi` de `features/recursos/tipos.ts`.

```ts
import type { Categoria, ErrorApi } from "../recursos/tipos";

export type DiaSemana =
  | "LUNES" | "MARTES" | "MIERCOLES" | "JUEVES" | "VIERNES" | "SABADO";

export type Dictamen =
  | "OK" | "SIN_CAMBIO" | "DESPLAZA_RESERVAS"
  | "RECHAZADA_FORMATO" | "RECHAZADA_RECURSO"
  | "CHOQUE_ACADEMICO" | "CHOQUE_ACTIVO_ENTREGADO" | "CHOQUE_MANTENIMIENTO";

export type EstadoCarga = "VALIDADA" | "APLICADA" | "RECHAZADA" | "EXPIRADA";

/** Los dictámenes que impiden aplicar la carga. */
export const DICTAMENES_BLOQUEANTES: Dictamen[] = [
  "RECHAZADA_FORMATO", "RECHAZADA_RECURSO",
  "CHOQUE_ACADEMICO", "CHOQUE_ACTIVO_ENTREGADO", "CHOQUE_MANTENIMIENTO",
];

export interface ReservaAfectada {
  reservaId: string;
  titular: { codigo: string; nombre: string };
  inicio: string;
  fin: string;
  categoria: Categoria;
}

export interface FilaValidada {
  fila: number;
  recursoId: string;
  nombreRecurso?: string;
  asignatura: string;
  codigoAsignatura: string;
  grupo: string;
  diaSemana: DiaSemana;
  inicio: string;
  fin: string;
  dictamen: Dictamen;
  sesiones: number;
  problema?: string;
  columna?: string;
  bloqueoEnConflicto?: { asignatura: string; codigoAsignatura: string; grupo: string };
  reservasAfectadas?: ReservaAfectada[];
}

export interface ResumenValidacion {
  filasLeidas: number;
  filasOk: number;
  filasSinCambio: number;
  filasConDictamenBloqueante: number;
  sesionesPorCrear: number;
  sesionesOmitidasPorDiaNoHabil: number;
  bloqueosPorRetirar: number;
  reservasPorCancelar: number;
}

export interface ValidacionResponse {
  cargaId: string;
  periodoAcademico: string;
  periodo: { inicio: string; fin: string };
  estado: EstadoCarga;
  validadaEn: string;
  expiraEn?: string;
  aplicable: boolean;
  requiereConfirmacion: boolean;
  resumen: ResumenValidacion;
  filas: FilaValidada[];
}

export interface ConfirmacionResponse {
  cargaId: string;
  periodoAcademico: string;
  estado: "APLICADA";
  aplicadaEn: string;
  aplicadaPor: { usuarioId: string; nombre: string };
  resultado: {
    bloqueosCreados: number;
    bloqueosSinCambio: number;
    bloqueosRetirados: number;
    reservasCanceladas: number;
    eventosEncolados: number;
  };
}

export interface NecesidadExtraordinariaRequest {
  recursoId: string;
  fecha: string;
  inicio: string;
  fin: string;
  asignatura: string;
  codigoAsignatura: string;
  grupo: string;
  programa: string;
  docente?: string;
  confirmar?: boolean;
}

export interface NecesidadExtraordinariaResponse {
  aplicado: boolean;
  dictamen: Dictamen;
  recursoId?: string;
  nombreRecurso?: string;
  inicio?: string;
  fin?: string;
  reservasAfectadas?: ReservaAfectada[];
  bloqueo?: {
    reservaId: string;
    recursoId: string;
    tipo: "REGULAR" | "EXTRAORDINARIO";
    inicio: string;
    fin: string;
    asignatura: string;
    programa: string;
    docente?: string;
  };
  reservasCanceladas?: number;
  eventosEncolados?: number;
}

export interface ErrorHorario extends ErrorApi {
  codigo?: "ARCHIVO_VACIO" | "CABECERA_INVALIDA" | "PERIODO_INVALIDO"
         | "CARGA_NO_APLICABLE" | "CARGA_EXPIRADA" | "CARGA_YA_APLICADA" | "IMPACTO_CAMBIO";
  dictamen?: Dictamen;
  columnasFaltantes?: string[];
  reservasPorCancelarAlValidar?: number;
  reservasPorCancelarAhora?: number;
  expiroEn?: string;
  aplicadaEn?: string;
}
```

`ReporteImportacion.tsx` agrupa `filas` por `dictamen` y muestra primero los bloqueantes, que son los que hay que corregir. `ConfirmarImpacto.tsx` solo se muestra cuando `requiereConfirmacion` es `true`, y lista las `reservasAfectadas` de todas las filas juntas: eso es lo que FR-008 pide aprobar.

---

### 9. Fixtures compartidos

Se suman a los de UC1 y UC2 en la misma carpeta:

```text
src/test/resources/contratos/
├── horario-valido.csv                      # 3 clases limpias
├── horario-con-errores.csv                 # una fila por cada dictamen bloqueante
├── horario-vacio.csv                       # solo la cabecera
├── horario-cabecera-invalida.csv           # sin la columna recurso_id
├── api-horario-validacion-aplicable.json   # el 200 con DESPLAZA_RESERVAS
├── api-horario-validacion-rechazada.json   # el 200 con aplicable: false
├── api-horario-confirmacion.json
├── api-horario-409-impacto-cambio.json
├── api-extraordinaria-previa.json          # confirmar: false
├── api-extraordinaria-201.json
├── modulo1-estado-operativo-lote.json      # con noEncontrados y un EN_MANTENIMIENTO
└── evento-reserva-cancelada.json           # lo que debe quedar en mensaje_saliente.payload
```

Los CSV son también la entrada de `LectorCsvDeHorariosTest` (T021), así que el formato de [§5](#5-formato-del-archivo-csv) se verifica contra los mismos archivos que usan las pruebas del controlador.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Sumar a la base de UC1 y UC2 lo único que falta: leer archivos y los parámetros de la carga.

- [ ] T001 Agregar `org.apache.commons:commons-csv` a `build.gradle`
- [ ] T002 [P] Extender `application.properties` con los parámetros de este caso de uso: `reservas.carga.vigencia=30m`, `reservas.carga.tamano-maximo=2MB`, `reservas.carga.lote-escritura=500`, `reservas.modulo1.lote-estado=200`, y el `spring.servlet.multipart.max-file-size` acorde
- [ ] T003 [P] Extender `PropiedadesReservas` con esos parámetros y validarlos al arrancar (vigencia > 0, lotes > 0)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Esquema, modelo de dominio y la parte de UC4 y UC11 que UC3 necesita.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T004 Escribir `V4__carga_horario.sql`: tabla `horario_semestral` con `estado`, `nombre_archivo`, `periodo_inicio`, `periodo_fin` y `resultado jsonb`; tabla `carga_horario_fila` con la fila cruda, su dictamen y su problema; tabla `bloqueo_academico` del plan general con la columna `clave_sesion` y su índice único; e índice por `(periodo_academico, estado)` para la reconciliación
- [ ] T005 [P] Crear el modelo de `domain/model/bloqueo/`: `ClaseSemanal`, `SesionDeClase`, `DatosAcademicos`, `TipoBloqueo`, `BloqueoAcademico`, `ClaveSesion` (con el hash de periodo, recurso, franja, código de asignatura y grupo) y `HorarioSemestral`
- [ ] T006 [P] Crear el modelo de `domain/model/carga/`: `CargaHorario`, `EstadoCarga`, `FilaValidada`, `DictamenFila` con los ocho dictámenes, `ReporteDeImportacion` e `ImpactoDeCarga`, con el método que decide si la carga es aplicable
- [ ] T007 [P] Crear `MotivoCancelacion` en `domain/model/reserva/` y extender `Reserva` con la transición a `CANCELADA_POR_PRIORIDAD_ACADEMICA`, que no penaliza (UC4 FR-004, FR-005)
- [ ] T008 [P] Definir los puertos de salida `CargaHorarioRepositoryPort`, `BloqueoRepositoryPort` y `LectorDeHorarios` en `domain/port/out/`, y extender `ReservaRepositoryPort` con la cancelación en lote por prioridad académica
- [ ] T009 [P] Definir los puertos de entrada `ValidarHorarioPort`, `AplicarHorarioPort`, `RegistrarNecesidadExtraordinariaPort` y `CancelarReservaPort` en `domain/port/in/`
- [ ] T010 Implementar `CancelarReservaUseCase` con **solo la rama académica**: cancela sin penalizar, deja la auditoría con autor, motivo y marca de tiempo, y encola el evento de cancelación (UC4 FR-002, FR-004 a FR-006, FR-009). La rama del titular, con `CAN-001` y `CAN-002`, queda para el plan de UC4
- [ ] T011 [P] Implementar `EventoReservaCancelada` y extender `OutboxNotificadorAdapter` para encolarlo en el topic de cancelación según [Contratos §7](#7-evento-de-cancelación--modulo2reservacancelacionv1), con `imputableALaPersona` en `false` (UC11 FR-002, FR-003, FR-006)
- [ ] T012 [P] Mapear `ArchivoInvalidoException` y `CargaNoAplicableException` a `400` y `409` en `ManejadorGlobalErrores`, con los `codigo` de [Contratos §1 y §2](#1-post-apihorariosvalidaciones)
- [ ] T013 [P] Exigir el rol `DIRECCION_PROGRAMA` en `/api/horarios/**` en `SeguridadConfig`, y comprobar que un `ESTUDIANTE` autenticado recibe `403` y no `401`

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Cargar el horario del semestre (Priority: P2)

**Goal**: La Dirección de Programa carga el archivo del semestre, ve un reporte fila por fila y, cuando hay reservas que se desplazarían, las aprueba antes de que se cancelen. Al confirmar, cada clase queda en `BLOQUEO_ACADEMICO` y ningún estudiante puede reservar esos recursos en esas franjas.

**Independent Test**: Con el perfil local, cargar `horario-valido.csv` y comprobar en la consulta de UC1 que las franjas de clase aparecen como `BLOQUEO_ACADEMICO` y no son seleccionables; cargar `horario-con-errores.csv` y comprobar que no se creó ni un bloqueo y que el reporte señala las cinco filas; volver a cargar el mismo archivo válido y comprobar que no se duplicó nada. No necesita que exista UC4 ni que Kafka esté levantado: los eventos quedan en la *outbox*.

### Tests for User Story 1

- [ ] T014 [P] [US1] Pruebas en `ExpansorDeSesionesTest.java`: una clase de lunes en un periodo de 16 semanas da 16 sesiones; un festivo configurado en lunes la deja en 15 y lo informa; `SABADO` genera sesiones si el sábado es hábil; un periodo que no contiene ningún día de esa clase da 0 sesiones y la fila lo dice
- [ ] T015 [P] [US1] Pruebas en `ClasificadorDeFilasTest.java`: un dictamen por cada fila de la tabla, el cruce parcial de 09:00–11:00 contra una clase de 08:00–10:00 como `CHOQUE_ACADEMICO`, el activo apartado sin recoger como `DESPLAZA_RESERVAS` y el ya entregado como `CHOQUE_ACTIVO_ENTREGADO` (FR-010), y que una fila con dos problemas reporta el bloqueante
- [ ] T016 [P] [US1] Pruebas en `ValidarHorarioUseCaseTest.java`: el archivo limpio deja la carga `VALIDADA` y `aplicable: true` sin crear bloqueos ni cancelar reservas; una sola fila bloqueante deja `aplicable: false` y **cero** bloqueos (escenario 2); el reporte lista **todas** las filas con problema y no se para en la primera; el archivo vacío responde `ARCHIVO_VACIO`; y el Módulo 1 caído da `503`
- [ ] T017 [P] [US1] Pruebas en `ReconciliadorDeHorarioTest.java`: recargar el mismo archivo deja todas las filas en `SIN_CAMBIO` y cero bloqueos nuevos (FR-007, SC-003); una clase nueva se crea; una clase que desapareció del archivo entra como `bloqueosPorRetirar` y exige confirmación; y una clase retirada y vuelta a cargar funciona, porque la `clave_sesion` cancelada quedó en `NULL`
- [ ] T018 [P] [US1] Pruebas en `AplicarHorarioUseCaseTest.java`: la confirmación crea los bloqueos y cancela las reservas desplazadas; una carga `aplicable: false` responde `CARGA_NO_APLICABLE`; una vencida, `CARGA_EXPIRADA`; confirmar dos veces no aplica dos veces y responde `CARGA_YA_APLICADA`; y un impacto que creció entre validar y confirmar responde `IMPACTO_CAMBIO` sin cancelar nada
- [ ] T019 [P] [US1] Prueba `PrioridadAcademicaIT.java` con Testcontainers: la cancelación de la reserva estudiantil y la creación del bloqueo ocurren en la misma transacción y la restricción de exclusión nunca ve las dos `CONFIRMADA` (escenario 3); un fallo a mitad de la carga no deja ni un bloqueo ni una reserva cancelada (FR-008); y por cada cancelación queda un `mensaje_saliente` que coincide con `evento-reserva-cancelada.json`
- [ ] T020 [P] [US1] Prueba `CargaHorarioPersistenceAdapterIT.java`: el índice único de `clave_sesion` rechaza el duplicado, cancelar un bloqueo pone la clave en `NULL`, y la escritura por lotes de miles de sesiones termina dentro del presupuesto de SC-001
- [ ] T021 [P] [US1] Pruebas `LectorCsvDeHorariosTest.java` con los CSV de [Contratos §9](#9-fixtures-compartidos): archivo válido, campo con comas entre comillas, cabecera sin una columna obligatoria, hora mal escrita, `DOMINGO` rechazado, columna extra ignorada y archivo con solo la cabecera
- [ ] T022 [P] [US1] Prueba `HorarioControllerTest.java` con `@WebMvcTest` y `MockMultipartFile`, comparando contra los *fixtures* `api-horario-*.json`: el `200` aplicable y el `200` rechazado, los tres `400`, `401` sin sesión, `403` con rol `ESTUDIANTE`, `413` con un archivo grande, los cuatro `409` de la confirmación y el `503` del Módulo 1

### Implementation for User Story 1

- [ ] T023 [P] [US1] Implementar `LectorCsvDeHorarios` con Commons CSV según [Contratos §5](#5-formato-del-archivo-csv): valida la cabecera, devuelve las filas crudas con su número real empezando en 2, y nunca lanza por una fila mal formada —el problema va en el dictamen
- [ ] T024 [US1] Implementar `ExpansorDeSesiones` sobre `CalendarioHabil`: de `ClaseSemanal` y el periodo a las `SesionDeClase`, contando las omitidas por día no hábil (depende de T005)
- [ ] T025 [US1] Implementar `ClasificadorDeFilas`: una llamada por lote al Módulo 1 para los recursos distintos, una consulta por lote de cruces contra `reserva` y `prestamo`, y el dictamen de cada fila con su `problema` (depende de T005, T006, T024)
- [ ] T026 [US1] Implementar `ReconciliadorDeHorario` con la `clave_sesion`: reparte en `SIN_CAMBIO`, nuevas y por retirar (FR-007) (depende de T025)
- [ ] T027 [US1] Implementar `ValidarHorarioUseCase`: lee, expande, clasifica, reconcilia, calcula el `ImpactoDeCarga` y guarda la carga con sus filas, **sin tocar ninguna reserva** (depende de T023 a T026)
- [ ] T028 [US1] Implementar `AplicarHorarioUseCase`: comprueba estado y vigencia, recomprueba el impacto, cancela las desplazadas por `CancelarReservaPort` y crea cada sesión por `ReservarRecursosPort` con `origen = ACADEMICO`, todo en una transacción y por lotes (depende de T010, T027)
- [ ] T029 [P] [US1] Implementar `CargaHorarioPersistenceAdapter` y `BloqueoPersistenceAdapter`, con la consulta de cruces por lote, la búsqueda por `clave_sesion` y la escritura por lotes
- [ ] T030 [US1] Registrar los beans y las transacciones de UC3 en `CasosDeUsoConfig`, con la validación en `readOnly` y la aplicación en una sola transacción de escritura (depende de T027 a T029)
- [ ] T031 [US1] Implementar `HorarioController` con `POST /api/horarios/validaciones`, `POST /api/horarios/{cargaId}/confirmacion`, `GET /api/horarios` y `GET /api/horarios/{cargaId}`, exactamente como los fija [Contratos §1, §2 y §4](#1-post-apihorariosvalidaciones), documentado con OpenAPI
- [ ] T032 [P] [US1] Frontend: copiar los tipos de [Contratos §8](#8-tipos-del-frontend) a `frontend/src/features/horarios/tipos.ts` y escribir `api.ts` con la subida `multipart` y el mapeo a `ErrorHorario`
- [ ] T033 [US1] Frontend: `SubirArchivo.tsx` (archivo, periodo académico y fechas), `ReporteImportacion.tsx` (tabla por fila, agrupada por dictamen y con los bloqueantes primero) y `ConfirmarImpacto.tsx`, que solo aparece si `requiereConfirmacion` y lista todas las `reservasAfectadas` juntas
- [ ] T034 [US1] Frontend: `app/(direccion)/horarios/page.tsx`, que encadena validar → revisar → confirmar, y muestra el resultado, el archivo vacío, la carga expirada y el `IMPACTO_CAMBIO` que obliga a revalidar

**Checkpoint**: At this point, the semester upload works end to end and can be demoed on its own

---

## Phase 4: Necesidad extraordinaria (FR-005, FR-006, FR-010)

**Purpose**: La otra mitad del spec. Reusa todo lo de la Phase 3 con una sola fila, así que se entrega después sin romper nada.

- [ ] T035 [P] Pruebas en `RegistrarNecesidadExtraordinariaUseCaseTest.java`: con `confirmar: false` no cambia nada y devuelve el impacto; con `confirmar: true` cancela la reserva estudiantil y crea el bloqueo `EXTRAORDINARIO` (escenario 3); una franja con otra clase responde `CHOQUE_ACADEMICO` sin cancelar el bloqueo que ya estaba (escenario 4, FR-006); un activo ya entregado responde `CHOQUE_ACTIVO_ENTREGADO` (FR-010); y una franja de 3 horas se acepta, porque el tope de 2 horas no aplica a lo académico
- [ ] T036 Implementar `RegistrarNecesidadExtraordinariaUseCase` reusando `ClasificadorDeFilas` y `CancelarReservaUseCase`, con `TipoBloqueo.EXTRAORDINARIO` y `horario_semestral_id` nulo (depende de T025, T010)
- [ ] T037 Implementar `POST /api/horarios/extraordinarias` en `HorarioController` según [Contratos §3](#3-post-apihorariosextraordinarias), con el `Location` apuntando a `/api/reservas/{id}`
- [ ] T038 Frontend: `FormularioExtraordinaria.tsx` y `app/(direccion)/horarios/extraordinaria/page.tsx`, con la previsualización del impacto antes de confirmar

**Checkpoint**: El spec de UC3 queda cubierto de punta a punta

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Mejoras que afectan a toda la funcionalidad

- [ ] T039 [P] Verificar SC-001 con un archivo de un semestre real —del orden de 300 clases y 4500 sesiones— y comprobar que validar y aplicar terminan en menos de 5 minutos, con el Módulo 1 simulado con su latencia prometida
- [ ] T040 [P] Verificar SC-002 de punta a punta: después de una carga, la consulta de UC1 no deja seleccionable ningún recurso con clase, y UC2 responde `RES-001` al intentar reservarlo
- [ ] T041 [P] Registrar en logs cada carga con su autor, su periodo, el conteo de cada dictamen y su duración, sin nombres de estudiantes
- [ ] T042 [P] Actualizar [modelo-datos-der.md](./modelo-datos-der.md) con las columnas nuevas de `horario_semestral`, la tabla `carga_horario_fila` y la `clave_sesion` de `bloqueo_academico`, y volver a generar el PNG
- [ ] T043 [P] Actualizar el README con cómo cargar un horario de prueba en el perfil local
- [ ] T044 Enviar a `pendientes-clarificacion.md` lo que este plan deja abierto, y llevarle a la Dirección de Programa el formato de [Contratos §5](#5-formato-del-archivo-csv) para confirmar que puede exportarlo

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: depende de que los planes de UC1 y UC2 estén hechos; sin ellos no hay esquema, seguridad, reloj, *outbox* ni `CalendarioHabil`
- **Foundational (Phase 2)**: depende de Setup - BLOCKS la user story
- **User Story 1 (Phase 3)**: depende de Foundational
- **Necesidad extraordinaria (Phase 4)**: depende de la Phase 3, porque reusa el clasificador y la cancelación
- **Polish (Phase 5)**: depende de que las Phases 3 y 4 estén completas

### Dependencias con otros casos de uso

- **UC2 `Reservar recursos`**: es la dependencia fuerte. UC3 **no inserta reservas por su cuenta**: cada sesión entra por `ReservarRecursosPort` con `origen = ACADEMICO`, que es la flecha `<<include>>` del diagrama (P-06). De ahí hereda gratis los `<<include>>` a UC7 y UC10.
- **UC1 `Consultar recursos`**: consume lo que este plan produce. Los bloqueos aparecen como `BLOQUEO_ACADEMICO`, que es el estado de mayor prioridad después del mantenimiento, sin tocar nada de UC1.
- **UC4 `Cancelar reserva`**: este plan implementa **solo** la rama de prioridad académica (UC4 FR-002, FR-004 a FR-006, FR-009). Su plan añadirá la cancelación del titular con `CAN-001` y `CAN-002`, la antelación de 10 minutos, la prohibición de cancelar un activo ya entregado (UC4 FR-008) y la cancelación por recurso no disponible (UC4 FR-010).
- **UC11 `Reportar cancelación de reserva`**: este plan monta el evento y lo encola. Su plan añadirá `antelacionMinutos` para la cancelación del titular (UC11 FR-004) y el historial de envíos (UC11 FR-010).
- **UC7 `Actualizar estado de los recursos`**: entra por UC2 y sigue con el alcance vigente —dejar escrita la ocupación— mientras P-20 esté abierto.
- **UC10 `Reportar información de la reserva`**: la ficha de cada bloqueo sale por la *outbox* de UC2, marcada con `origen: ACADEMICO` y `sancionable: false`, que es la forma ya definida en [UC2 § Contratos §7](./plan-uc2-reservar-recursos.md#7-evento-hacia-el-módulo-3--modulo2reservafichav1).
- **UC9 `Recibir reporte de no asistencia`**: no se toca aquí, pero este plan crea la situación que P-19 señala —nada impide hoy que el Módulo 3 reporte una ausencia sobre una clase—. El rechazo le toca a UC9.

### Within User Story 1

- Esquema y modelo (T004 a T007) → puertos (T008, T009) → `CancelarReservaUseCase` (T010)
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
- **Decisiones de los contratos que el spec no fija**: el archivo trae clases semanales y el sistema las expande a sesiones; la carga va en dos peticiones, validar y confirmar, con una vigencia de 30 minutos; un choque bloquea la carga entera igual que un error de formato; `IMPACTO_CAMBIO` rechaza la confirmación en vez de aplicar lo que nadie aprobó; el formato es CSV y no `.xlsx`; y el reporte de validación es la única respuesta del módulo que nombra al titular de una reserva, porque FR-008 exige mostrar cuáles se cancelarían y solo Dirección de Programa la ve
- **NEEDS CLARIFICATION abiertos en este plan**:
  - **P-19**: qué reglas de `Reservar recursos` se salta una reserva de origen académico. Aquí se asume que se saltan la sanción, el cupo y el tope de 2 horas, igual que en el plan de UC2
  - **P-19**: si retirar una clase se le reporta al Módulo 3 como cancelación. Aquí se asume que sí, porque ya recibió su ficha
  - **P-19**: nada impide que el Módulo 3 reporte una ausencia sobre una clase; el rechazo le corresponde a UC9
  - **P-20 punto 3**: si `EN_MANTENIMIENTO` trae fechas. Mientras no las traiga, un recurso en mantenimiento hoy bloquea la carga de todas sus clases del semestre, que es conservador pero puede ser excesivo
  - **P-20 punto 5**: quién le avisa al estudiante desplazado por una clase. El Módulo 2 cancela y se lo reporta al Módulo 3; el aviso a la persona no tiene dueño escrito
  - **Formato del archivo**: el spec no lo fija. Se asume CSV de clases semanales; si llega `.xlsx` o sesiones ya expandidas, cambia solo `LectorCsvDeHorarios`
  - **Calendario de festivos**: el mismo pendiente que UC2. La expansión de sesiones depende de una lista mantenida a mano en `application.properties`
  - **Vigencia de la carga validada**: los 30 minutos son una decisión de este plan; el spec no dice cuánto puede tardar la Dirección de Programa en confirmar
