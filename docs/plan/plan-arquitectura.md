# Plan técnico general: arquitectura y organización

**Fecha**: 2026-09-19
**Specs**: [docs/specs/](../specs/) — índice en [spec-modulo2.md](../specs/spec-modulo2.md)
**Pendientes**: [pendientes-clarificacion.md](../specs/pendientes-clarificacion.md)

## Resumen

El Módulo 2 se construye con **arquitectura hexagonal (puertos y adaptadores)** organizada en dos capas: `domain`, que guarda las entidades, las reglas de negocio, los puertos y los casos de uso, e `infrastructure`, que guarda todo lo tecnológico (REST, JPA, clientes HTTP, seguridad). La regla que no se rompe es que **`domain` no conoce a `infrastructure`**.

El repositorio contiene el backend (Spring Boot, en la raíz) y el frontend (Next.js, en `frontend/`). Los datos viven en **PostgreSQL**, elegido porque la regla central del proyecto —que dos ocupaciones de un mismo recurso no se crucen— se puede garantizar en la propia base con rangos de tiempo y restricciones de exclusión.

Este documento llega hasta la organización de carpetas y el modelo de datos. Las tareas de implementación de cada caso de uso van en un plan por caso de uso, más adelante.

## Contexto técnico

| | |
|---|---|
| **Lenguaje backend** | Java 21 |
| **Framework backend** | Spring Boot 4.1.1: Web MVC, Data JPA, Security, Validation, RestClient |
| **Frontend** | Next.js (App Router) con React y TypeScript |
| **Base de datos** | PostgreSQL, con la extensión `btree_gist` |
| **Migraciones** | Flyway, con SQL versionado |
| **Autenticación** | Login propio: usuarios en nuestra base, contraseñas con BCrypt y sesión por JWT |
| **Documentación de la API** | OpenAPI (springdoc) |
| **Pruebas** | JUnit 5, Testcontainers (PostgreSQL), WireMock para los Módulos 1 y 3, ArchUnit para la regla de dependencias |
| **Build** | Gradle con Groovy DSL (backend) y npm (frontend) |
| **Zona horaria** | `America/Bogota`. Todas las fechas se guardan como `timestamptz`. |
| **Ventana de operación** | 06:00 a 22:00, sin franjas que crucen la medianoche |
| **Metas de rendimiento** | NEEDS CLARIFICATION: depende de P-17 (los tiempos de respuesta entre módulos no cuadran). |
| **Escala** | NEEDS CLARIFICATION: cuántos usuarios concurrentes y cuántos recursos. |

## Arquitectura

### Por qué hexagonal

El profesor trabaja con arquitectura hexagonal, y además encaja con cómo está hecho el Módulo 2:

- **Dependemos de dos sistemas que no controlamos.** El Módulo 1 (inventario) y el Módulo 3 (cumplimiento y sanciones) todavía tienen el contrato abierto (P-16, P-20). Con un puerto en `domain`, los casos de uso se programan y se prueban con un adaptador falso, y cuando el otro módulo entregue su API solo se escribe el adaptador real.
- **El Módulo 3 está en los dos lados del hexágono.** Nos llama (no asistencia en UC9, check-out en UC12) y lo llamamos (sanciones en UC6, ficha en UC10, cancelación en UC11). En hexagonal son adaptadores distintos sobre el mismo dominio.
- **Las caídas de otros módulos (P-10, P-14) se resuelven en el adaptador**, con timeouts, reintentos y respuestas por defecto, sin tocar el caso de uso.
- **Las reglas de negocio cambian mientras se cierran los pendientes**, y al vivir en Java puro se prueban en milisegundos, sin levantar Spring ni la base de datos.

### Capas y regla de dependencia

```text
   QUIÉN NOS LLAMA (entrada)                         A QUIÉN LLAMAMOS (salida)
   Frontend (Estudiante, Monitor,  ─┐              ┌─→ PostgreSQL
             Dirección de Programa) ├─→ [DOMAIN] ──┼─→ Módulo 1 (inventario, estado operativo)
   Módulo 3 (UC9, UC12)            ─┘              └─→ Módulo 3 (sanciones, fichas, cancelaciones)
```

| Capa | Contiene | Puede importar |
|---|---|---|
| `domain.model` | Entidades, objetos de valor y reglas: franja de máximo 2 horas, ventana de 06:00 a 22:00, vencimiento a las 22:00 del día hábil, renovación única, cupo de 3, transiciones de estado de la reserva | Solo Java estándar |
| `domain.port.in` | Una interfaz por caso de uso: lo que la aplicación ofrece | `domain.model` |
| `domain.port.out` | Lo que la aplicación necesita del exterior: repositorios, Módulo 1, Módulo 3, reloj | `domain.model` |
| `domain.usecase` | La implementación de cada caso de uso, que orquesta el modelo usando los puertos de salida | `domain.*` |
| `infrastructure.adapter.in` | Controladores REST para el frontend y para el Módulo 3 | `domain.port.in`, `domain.model` |
| `infrastructure.adapter.out` | Persistencia JPA y clientes HTTP; cada uno implementa un puerto de salida | `domain.port.out`, `domain.model` |
| `infrastructure.config` | Beans de Spring, seguridad, propiedades | Todo |

**Regla**: `domain` no importa nada de `infrastructure`, ni de Spring, JPA, Jackson o HTTP. Una prueba de ArchUnit lo verifica en cada build.

Decisiones que se derivan de la regla:

- **El dominio es puro.** Las entidades de `domain.model` no llevan `@Entity`. Las entidades JPA viven en `infrastructure.adapter.out.persistence`, y un mapper traduce entre las dos.
- **Los casos de uso no llevan `@Service`.** Se registran como beans en `infrastructure.config`, y las transacciones (`@Transactional`) se abren en esa misma capa, envolviendo el caso de uso.
- **La hora se inyecta con un puerto `Reloj`.** Casi todas las reglas dependen de "ahora" (10 minutos de no-show, vencimiento a las 22:00, antelación de cancelación), y las pruebas necesitan controlarlo.
- **Los `<<include>>` del diagrama son llamadas entre casos de uso.** Por ejemplo, `ReservarRecursosUseCase` usa `ConsultarDisponibilidad`, `ConsultarSanciones`, `ActualizarEstadoRecursos` y `ReportarInformacionReserva` a través de sus puertos de entrada.
- **El diccionario de errores es parte del dominio** (`CodigoDenegacion`). El adaptador web lo traduce a respuestas `ProblemDetail` (RFC 9457).

### Casos de uso y dónde viven

| UC | Caso de uso | Puerto de entrada | Lo dispara |
|---|---|---|---|
| UC1 | Consultar recursos | `ConsultarRecursosPort` | Frontend |
| UC2 | Reservar recursos | `ReservarRecursosPort` | Frontend, UC3 |
| UC3 | Importar horarios semestrales | `ImportarHorariosPort` | Frontend (Dirección) |
| UC4 | Cancelar reserva | `CancelarReservaPort` | Frontend, UC3 |
| UC6 | Consultar sanciones | `ConsultarSancionesPort` | UC1, UC2 |
| UC7 | Actualizar estado de los recursos | `ActualizarEstadoRecursosPort` | UC2, UC4, UC9, UC12 |
| UC8 | Consultar disponibilidad de los recursos | `ConsultarDisponibilidadPort` | UC1, UC2 |
| UC9 | Recibir reporte de no asistencia | `RecibirNoAsistenciaPort` | Módulo 3 |
| UC10 | Reportar información de la reserva | `ReportarInformacionReservaPort` | UC2 |
| UC11 | Reportar cancelación de reserva | `ReportarCancelacionPort` | UC4 |
| UC12 | Recibir check-out | `RecibirCheckOutPort` | Módulo 3 |
| — | Registrarse | `RegistrarUsuarioPort` | Frontend |
| — | Iniciar sesión | `IniciarSesionPort` | Frontend |

Registrarse e iniciar sesión no están en el diagrama de casos de uso. Aparecen porque el equipo decidió tener login propio con autorregistro, y necesitan su propio spec (ver [Pendientes](#pendientes-que-afectan-la-arquitectura)).

### Integraciones

| Dirección | Con quién | Cómo | Puerto |
|---|---|---|---|
| Salida | Módulo 1: catálogo y estado operativo | REST síncrono con RestClient, con timeout; la política ante caídas depende de P-14 | `InventarioPort` |
| Salida | Módulo 3: sanciones vigentes | REST síncrono con timeout; la política ante caídas depende de P-10 | `CumplimientoPort` |
| Salida | Módulo 3: ficha de reserva (UC10) y cancelación (UC11) | *Outbox*: el mensaje se guarda en la misma transacción que la reserva y un proceso programado lo envía y reintenta | `NotificadorModulo3Port` |
| Entrada | Módulo 3: no asistencia (UC9) y check-out (UC12) | Endpoints REST bajo `/api/integracion/modulo3/**`, autenticados con API key | `RecibirNoAsistenciaPort`, `RecibirCheckOutPort` |
| Entrada | Frontend | Endpoints REST bajo `/api/**`, autenticados con JWT | Los demás puertos de entrada |

La *outbox* existe porque la ficha y la cancelación no se pueden perder si el Módulo 3 está caído en el momento de reservar o cancelar. Además, las entidades `FichaDeReserva` y `ReporteDeCancelación` de UC10 y UC11 ya piden guardar el resultado del envío.

### Seguridad

- **Autorregistro** con correo institucional. El rol por defecto es `ESTUDIANTE`.
- Contraseñas con **BCrypt**. El login devuelve un **JWT** firmado por el backend, con el id y el rol del usuario.
- El JWT viaja en una **cookie `httpOnly`**. El frontend llama al backend a través de un *rewrite* de Next.js (`/api/*` → Spring), para que todo quede en el mismo origen sin configurar CORS y el token no quede expuesto a JavaScript.
- **Roles**: `ESTUDIANTE`, `MONITOR` (hereda todo lo de `ESTUDIANTE`) y `DIRECCION_PROGRAMA`. La autorización por rol se aplica en el adaptador web; el caso de uso recibe el usuario ya identificado.
- El Módulo 3 no hace login: usa una **API key** propia, limitada a sus endpoints de integración.

## Organización de carpetas

### Repositorio

```text
ReservasUnimag/
├── build.gradle, settings.gradle, gradlew   backend (Spring Boot), en la raíz
├── src/                                     código y pruebas del backend
├── frontend/                                frontend (Next.js), con su propio package.json
├── docs/                                    specs, planes y diagramas
├── README.md
└── CONTRIBUTING.md
```

El backend se queda en la raíz para no mover lo que ya existe ni romper el README y los comandos de Gradle.

### Backend

```text
src/main/java/edu/unimagdalena/reservasunimag/
├── ReservasUnimagApplication.java
│
├── domain/                              sin Spring, JPA ni HTTP
│   ├── model/
│   │   ├── reserva/                     Reserva, EstadoReserva, OrigenReserva,
│   │   │                                FranjaHoraria, PeriodoDePrestamo, Prestamo
│   │   ├── bloqueo/                     DatosAcademicos, TipoBloqueo, HorarioSemestral
│   │   ├── cumplimiento/                Ausencia, CheckOut, Dictamen, Sancion,
│   │   │                                ReporteDeCumplimiento
│   │   ├── recurso/                     RecursoRef, CategoriaRecurso (ESPACIO/ACTIVO),
│   │   │                                EstadoOperativo, EstadoVisible
│   │   ├── usuario/                     Usuario, Rol
│   │   └── error/                       CodigoDenegacion, ReservaDenegadaException
│   ├── port/
│   │   ├── in/                          ReservarRecursosPort, CancelarReservaPort, ...
│   │   └── out/                         ReservaRepositoryPort, HorarioRepositoryPort,
│   │                                    UsuarioRepositoryPort, InventarioPort,
│   │                                    CumplimientoPort, NotificadorModulo3Port,
│   │                                    CifradorContrasenaPort, Reloj
│   └── usecase/                         ReservarRecursosUseCase, CancelarReservaUseCase,
│                                        ImportarHorariosUseCase, ...
│
└── infrastructure/
    ├── adapter/
    │   ├── in/
    │   │   └── web/
    │   │       ├── recurso/             controladores y DTO de UC1 y UC8
    │   │       ├── reserva/             UC2 y UC4
    │   │       ├── horario/             UC3
    │   │       ├── auth/                registro e inicio de sesión
    │   │       ├── modulo3/             endpoints de UC9 y UC12
    │   │       └── error/               manejador global → ProblemDetail
    │   └── out/
    │       ├── persistence/
    │       │   ├── entity/              entidades JPA
    │       │   ├── repository/          interfaces Spring Data
    │       │   ├── mapper/              dominio ↔ JPA
    │       │   └── *Adapter.java        implementan los *RepositoryPort
    │       ├── modulo1/                 cliente RestClient → InventarioPort
    │       ├── modulo3/                 cliente RestClient → CumplimientoPort
    │       │                            y envío de la outbox → NotificadorModulo3Port
    │       └── security/                BCrypt → CifradorContrasenaPort, emisión de JWT
    └── config/                          beans de casos de uso, transacciones, seguridad,
                                         propiedades (cupo, ventana, plazos), Reloj

src/main/resources/
├── application.properties
└── db/migration/                        V1__esquema_inicial.sql, V2__..., ...

src/test/java/edu/unimagdalena/reservasunimag/
├── domain/model/                        pruebas unitarias de las reglas
├── domain/usecase/                      casos de uso con puertos falsos
├── infrastructure/adapter/              @WebMvcTest, Testcontainers y WireMock
└── ArquitecturaTest.java                ArchUnit: domain no depende de infrastructure
```

### Frontend

```text
frontend/
├── package.json, next.config.ts, tsconfig.json
└── src/
    ├── app/                             rutas (App Router)
    │   ├── (auth)/login/, registro/
    │   ├── (estudiante)/recursos/       UC1, UC8 y reservar (UC2)
    │   ├── (estudiante)/mis-reservas/   ver y cancelar (UC4)
    │   └── (direccion)/horarios/        importar horarios (UC3)
    ├── components/                      componentes visuales reutilizables
    ├── features/                        lógica por funcionalidad: recursos, reservas,
    │                                    horarios, auth (hooks, formularios, tipos)
    └── lib/api/                         cliente HTTP hacia /api del backend
```

El frontend no tiene reglas de negocio: valida formularios por usabilidad, pero la decisión final la toma siempre el backend.

## Base de datos

### Por qué PostgreSQL

1. **Garantiza que no haya cruces.** Con rangos de tiempo (`tstzrange`) y una restricción de exclusión, la propia base impide que dos reservas confirmadas del mismo recurso se solapen, aunque dos estudiantes den clic al mismo tiempo. Sirve igual para una franja de 2 horas de un espacio y para un préstamo de varios días de un activo, que es el cruce "franja contra periodo" de UC1, UC2 y UC8.
2. **Transacciones ACID.** UC3 cancela reservas estudiantiles y crea bloqueos, todo o nada. UC2 cuenta el cupo e inserta la reserva en una sola operación. La *outbox* se escribe en la misma transacción.
3. **Fechas con zona horaria** (`timestamptz`), necesarias porque todas las reglas están en hora de Bogotá.
4. **Los datos son relacionales, con reglas fuertes.** Necesitamos restricciones, no un esquema flexible, así que una base NoSQL no aporta.
5. **Ya está en el stack**, y Testcontainers lo levanta en las pruebas con el mismo motor de producción.

Se usa **Flyway** en vez de dejar que Hibernate genere el esquema (`ddl-auto=validate`), porque la restricción de exclusión y la extensión `btree_gist` solo se pueden escribir en SQL.

### Qué guardamos y qué no

Según el reparto de estados que aclaró el profesor ([spec-modulo2.md](../specs/spec-modulo2.md)):

| Guardamos (el Módulo 2 es dueño) | No guardamos (solo referenciamos o consultamos) |
|---|---|
| Usuarios y credenciales (login propio) | **Recurso** y su estado operativo (`DISPONIBLE`, `EN_USO`, `EN_MANTENIMIENTO`): son del Módulo 1. Guardamos solo `recurso_id` y la categoría. |
| Reservas y bloqueos académicos (`RESERVADO`, `BLOQUEO_ACADEMICO` y los estados de la reserva) | **Sanciones**: son del Módulo 3. Se consultan en cada reserva y no se copian. |
| Préstamos, ausencias, check-out, denegaciones | |
| Cargas de horario semestral | |
| Mensajes pendientes hacia el Módulo 3 (*outbox*) | |

### Modelo

```text
usuario 1───* reserva *───(recurso_id: Módulo 1)
                 │
                 ├── 0..1 prestamo           (si el recurso es un activo)
                 ├── 0..1 bloqueo_academico  (si el origen es ACADEMICO) *───1 horario_semestral
                 ├── 0..1 ausencia
                 └── 0..1 check_out
usuario 1───* denegacion
mensaje_saliente  (outbox: ficha y cancelación hacia el Módulo 3)
```

**`usuario`**
| Columna | Tipo | Nota |
|---|---|---|
| id | uuid PK | |
| codigo | varchar, único | código estudiantil o institucional |
| nombre | varchar | |
| correo | varchar, único | dominio institucional |
| contrasena_hash | varchar | BCrypt |
| rol | varchar | `ESTUDIANTE`, `MONITOR`, `DIRECCION_PROGRAMA` |
| activo | boolean | |
| creado_en | timestamptz | |

**`reserva`**: una sola tabla para espacios y activos, estudiantiles y académicas.
| Columna | Tipo | Nota |
|---|---|---|
| id | uuid PK | |
| usuario_id | uuid FK, nulo | nulo solo si el origen es `ACADEMICO` |
| recurso_id | varchar | identificador del Módulo 1 |
| categoria_recurso | varchar | `ESPACIO`, `ACTIVO` |
| origen | varchar | `ESTUDIANTIL`, `ACADEMICO` |
| estado | varchar | `CONFIRMADA`, `CANCELADA`, `CANCELADA_POR_PRIORIDAD_ACADEMICA`, `CANCELADA_POR_RECURSO_NO_DISPONIBLE`, `FINALIZADA` |
| inicio | timestamptz | inicio de la franja, o entrega del préstamo |
| fin | timestamptz | fin de la franja, o vencimiento del préstamo |
| ocupacion | tstzrange, generada | `tstzrange(inicio, fin, '[)')` |
| motivo_cancelacion | varchar, nulo | |
| cancelada_en | timestamptz, nulo | |
| creada_en | timestamptz | |
| version | bigint | bloqueo optimista |

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE reserva ADD CONSTRAINT reserva_sin_cruces
  EXCLUDE USING gist (recurso_id WITH =, ocupacion WITH &&)
  WHERE (estado = 'CONFIRMADA');

ALTER TABLE reserva ADD CONSTRAINT reserva_titular_segun_origen
  CHECK ((origen = 'ACADEMICO') = (usuario_id IS NULL));
```

El bloqueo académico es una reserva con `origen = 'ACADEMICO'`, porque cada clase importada pasa por `Reservar recursos` (P-06). Así una sola restricción protege contra los cruces entre clases, reservas estudiantiles y préstamos. Cuando UC3 desplaza una reserva estudiantil, la cancela y crea el bloqueo en la misma transacción, y la restricción nunca ve los dos confirmados a la vez.

**`prestamo`**: el detalle de una reserva de activo.
| Columna | Tipo | Nota |
|---|---|---|
| reserva_id | uuid PK, FK | |
| plazo_dias_habiles | int | según el tipo del activo (Módulo 1) |
| entregado_en | timestamptz, nulo | cuándo se recogió |
| renovado | boolean | una sola renovación (UC2 FR-016) |
| devuelto_en | timestamptz, nulo | llega por check-out (UC12) |

Al renovar se actualiza `reserva.fin` al nuevo vencimiento, y la restricción de exclusión rechaza la renovación si invade la reserva de otra persona.

**`bloqueo_academico`**
| Columna | Tipo | Nota |
|---|---|---|
| reserva_id | uuid PK, FK | |
| horario_semestral_id | uuid FK, nulo | nulo si es una necesidad extraordinaria |
| tipo | varchar | `REGULAR`, `EXTRAORDINARIO` |
| asignatura | varchar | |
| programa | varchar | |
| docente | varchar | |

**`horario_semestral`**
| Columna | Tipo | Nota |
|---|---|---|
| id | uuid PK | |
| periodo_academico | varchar | p. ej. `2026-2` |
| cargado_por | uuid FK → usuario | Dirección de Programa |
| cargado_en | timestamptz | |
| resultado | jsonb | resumen de la carga: creados, choques, rechazos |

**`ausencia`** (UC9)
| Columna | Tipo | Nota |
|---|---|---|
| id | uuid PK | |
| reserva_id | uuid FK, único | |
| reportada_en | timestamptz | cuándo llegó el aviso del Módulo 3 |
| anulada | boolean | anulación de un reporte enviado por error |

**`check_out`** (UC12)
| Columna | Tipo | Nota |
|---|---|---|
| id | uuid PK | |
| reserva_id | uuid FK, único | |
| ocurrido_en | timestamptz | devolución o revisión real |
| dictamen | varchar, nulo | solo espacios: `SIN_NOVEDAD`, `REQUIERE_MANTENIMIENTO` |
| recibido_en | timestamptz | |

**`denegacion`** (UC2)
| Columna | Tipo | Nota |
|---|---|---|
| id | uuid PK | |
| usuario_id | uuid FK | |
| recurso_id | varchar | |
| codigo | varchar | del diccionario de errores |
| ocurrida_en | timestamptz | |

**`mensaje_saliente`** (*outbox* de UC10 y UC11)
| Columna | Tipo | Nota |
|---|---|---|
| id | uuid PK | |
| tipo | varchar | `FICHA_RESERVA`, `CANCELACION` |
| reserva_id | uuid FK | |
| payload | jsonb | lo que se envía al Módulo 3 |
| estado | varchar | `PENDIENTE`, `ENVIADO`, `FALLIDO` |
| intentos | int | |
| creado_en, enviado_en | timestamptz | |

Los parámetros del negocio (cupo de 3, ventana de 06:00 a 22:00, 10 minutos de no-show, 2 horas por franja) van en `application.properties`, no en tablas, mientras nadie necesite cambiarlos en caliente.

## Pendientes que afectan la arquitectura

| Pendiente | Qué afecta |
|---|---|
| P-10, P-14 | Política de los adaptadores del Módulo 3 y del Módulo 1 cuando no responden: bloquear o permitir, y qué mostrar |
| P-16, P-20 | Contrato del `InventarioPort` con el Módulo 1 |
| P-17 | Timeouts de los clientes y metas de rendimiento |
| P-08 | Qué pasa con un préstamo que nunca se devuelve: hoy la ocupación dura hasta el check-out |
| UC9 | En qué estado queda una reserva con ausencia. La lista de estados de UC2 no tiene uno propio: NEEDS CLARIFICATION |
| UC7 | Tabla de historial de cambios de estado (`CambioDeEstado`): se diseña cuando se responda P-20 |
| Registro | Spec de registro e inicio de sesión: dominio de correo permitido, datos obligatorios y cómo se asignan los roles `MONITOR` y `DIRECCION_PROGRAMA`, que no pueden salir del autorregistro |
| Escala | Usuarios concurrentes y volumen de recursos esperados |
