# ReservasUnimag

Sistema de reservas de recursos físicos de la Universidad del Magdalena: salones,
laboratorios, salas de estudio y activos como libros, microscopios o videobeams.

Este repositorio corresponde al **Módulo 2 — Operación de Reservas y Priorización
Académica**, el motor de reglas de negocio que gestiona el uso de los recursos y
resuelve los conflictos entre la actividad académica institucional y el apartado
estudiantil.

> **Estado actual:** la especificación y los planes técnicos están cerrados y empezó la
> implementación. Ya está la base del backend —arquitectura hexagonal, esquema inicial,
> sesión con JWT y manejo de errores—; los casos de uso se van sumando uno por uno.

## Documentación

| Documento | Contenido |
|---|---|
| [docs/modulo2.md](docs/modulo2.md) | Resumen funcional del módulo. |
| [docs/unimag.png](docs/unimag.png) | Diagrama de casos de uso. |
| [docs/specs/](docs/specs/) | Especificaciones detalladas, una por caso de uso. |
| [docs/specs/spec-template.md](docs/specs/spec-template.md) | Plantilla base de las especificaciones. |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Cómo trabajamos: GitFlow, ramas, Pull Requests y revisión. |

### Casos de uso

| # | Caso de uso | Prioridad | Especificación |
|---|---|---|---|
| UC1 | Consultar recursos | P1 | [spec-modulo2-uc1-consultar-recursos.md](docs/specs/spec-modulo2-uc1-consultar-recursos.md) |
| UC2 | Reservar recursos `<<extend>>` | P1 | [spec-modulo2-uc2-reservar-recursos.md](docs/specs/spec-modulo2-uc2-reservar-recursos.md) |
| UC3 | Importar horarios semestrales | P2 | [spec-modulo2-uc3-importar-horarios-semestrales.md](docs/specs/spec-modulo2-uc3-importar-horarios-semestrales.md) |
| UC4 | Cancelar reserva `<<extend>>` | P2 | [spec-modulo2-uc4-cancelar-reserva.md](docs/specs/spec-modulo2-uc4-cancelar-reserva.md) |
| UC6 | Consultar sanciones `<<include>>` | P1 | [spec-modulo2-uc6-consultar-sanciones.md](docs/specs/spec-modulo2-uc6-consultar-sanciones.md) |
| UC7 | Actualizar estado de los recursos `<<include>>` | P1 | [spec-modulo2-uc7-actualizar-estado-recursos.md](docs/specs/spec-modulo2-uc7-actualizar-estado-recursos.md) |
| UC8 | Consultar disponibilidad de los recursos `<<include>>` | P1 | [spec-modulo2-uc8-consultar-disponibilidad-recursos.md](docs/specs/spec-modulo2-uc8-consultar-disponibilidad-recursos.md) |
| UC9 | Recibir reporte de no asistencia `<<extend>>` | P2 | [spec-modulo2-uc9-recibir-reporte-no-asistencia.md](docs/specs/spec-modulo2-uc9-recibir-reporte-no-asistencia.md) |
| UC10 | Reportar información de la reserva `<<include>>` | P3 | [spec-modulo2-uc10-reportar-informacion-reserva.md](docs/specs/spec-modulo2-uc10-reportar-informacion-reserva.md) |
| UC11 | Reportar cancelación de reserva `<<include>>` | P2 | [spec-modulo2-uc11-reportar-cancelacion-reserva.md](docs/specs/spec-modulo2-uc11-reportar-cancelacion-reserva.md) |
| UC12 | Recibir check-out | P3 | [spec-modulo2-uc12-recibir-check-out.md](docs/specs/spec-modulo2-uc12-recibir-check-out.md) |

## Actores

| Actor | Tipo | Descripción |
|---|---|---|
| Estudiante | Primario (humano) | Consulta recursos, reserva y cancela sus propias reservas. |
| Monitor | Primario (humano) | Especialización de Estudiante: hereda todas sus capacidades. |
| Dirección de Programa | Primario (humano) | Importa la carga académica semestral y consulta el catálogo de recursos. |
| Módulo 1 | Secundario (sistema) | Inventario físico: es dueño del catálogo y del estado operativo de cada recurso (`DISPONIBLE`, `EN_USO`, `EN_MANTENIMIENTO`). El Módulo 2 lo consulta y guarda por su cuenta las reservas y los bloqueos. |
| Módulo 3 | Secundario (sistema) | Control de uso, sanciones y analítica: recibe la ficha de cada reserva y las cancelaciones, nos reporta las ausencias y los check-out, y provee las sanciones vigentes. |

## Stack

- Java 21
- Spring Boot 4.1.1 (Web MVC, Data JPA, Security con JWT propio, Validation, RestClient)
- PostgreSQL con Flyway para las migraciones
- Apache Kafka para los eventos hacia el Módulo 3, detrás de una *outbox* en PostgreSQL
- springdoc-openapi: la API se documenta en `/swagger-ui.html`
- JUnit 5, Testcontainers, ArchUnit y WireMock para las pruebas
- Gradle con Groovy DSL (con wrapper incluido)

La arquitectura, las capas y el modelo de datos están en
[docs/plan/plan-arquitectura.md](docs/plan/plan-arquitectura.md).

## Requisitos

- JDK 21
- Docker en ejecución (lo usan Testcontainers y el arranque de desarrollo)

## Cómo ejecutarlo

Levantar la aplicación en desarrollo, con la base de datos PostgreSQL en un contenedor
gestionado automáticamente por Testcontainers:

```bash
./gradlew bootTestRun
```

Con el perfil `local` se siembran además tres usuarios de prueba, uno por rol, todos
con la contraseña `unimag2026`:

```bash
./gradlew bootTestRun --args='--spring.profiles.active=local'
```

| Correo | Rol |
|---|---|
| `estudiante@unimagdalena.edu.co` | `ESTUDIANTE` |
| `monitor@unimagdalena.edu.co` | `MONITOR` |
| `direccion@unimagdalena.edu.co` | `DIRECCION_PROGRAMA` |

La sesión se abre con `POST /api/auth/login` y queda en una cookie `httpOnly`. El JWT
solo se usa entre el frontend y nuestra API: las llamadas a los Módulos 1 y 3 van sin
autenticación.

### Kafka y la outbox

Todo lo que sale del módulo —la ficha de la reserva, la cancelación, los acuses al Módulo 3 y el
inicio de uso al Módulo 1— se guarda en la tabla `outbox_message` dentro de la misma transacción que
la operación, y un publicador (`@Scheduled`, cada 5 s) lo lleva a su topic. **Reservar, cancelar y
registrar una llegada funcionan aunque Kafka esté caído**: los eventos quedan `PENDING` y salen
cuando el broker vuelve, con espera creciente entre reintentos (5 s, 30 s, 2 min, 10 min) hasta
quedar `FAILED` tras `reservations.outbox.max-attempts`. Además del reloj, el publicador se despierta
en cuanto confirma una transacción que encoló algo (`reservations.outbox.publish-on-commit`), así
la ficha de una reserva sale en menos de 5 s.

Lo que se le reporta al Módulo 3 deja constancia propia, que no se purga con la outbox:
`reservation_record` para las fichas (UC10) y `cancellation_report` para las cancelaciones (UC11).
Dirección de Programa las audita en `GET /api/reservation-records` y `GET /api/cancellation-reports`,
filtrando por `delivery=FAILED`. Para reencolar una que quedó `FAILED`, se devuelve su mensaje a
`PENDING` y el siguiente turno la publica con el mismo `eventId`:

```sql
UPDATE outbox_message SET status = 'PENDING', attempts = 0, next_attempt_at = now()
WHERE id = '<eventId>';
```

Cada consulta de sanciones al Módulo 3 (UC6) también queda registrada, con lo que contestó, en
`sanction_check`. Dirección de Programa la audita en `GET /api/sanction-checks`; con
`outcome=NOT_VERIFIED` se ve cuántas veces no se pudo comprobar. El cliente del Módulo 3 corta a los
1500 ms (`reservations.module3.timeout`), por debajo de los 2 s que permite UC6 SC-002.

Lo que llega del Módulo 3 (la no asistencia y el check-out) se consume con una *inbox*
(`inbox_message`) que descarta los eventos repetidos. Un evento ilegible, o que apunta a una reserva
que no existe, va a `module2.dlt.<topic de origen>` con la causa en las cabeceras.

Para tener un broker en desarrollo:

```bash
docker compose up -d kafka        # o `docker compose up -d` para PostgreSQL y Kafka
```

Para ver qué está esperando o falló:

```sql
SELECT type, status, attempts, next_attempt_at, last_error
FROM outbox_message WHERE status <> 'SENT' ORDER BY created_at;
```

Si no quieres el publicador (por ejemplo, sin Docker para Kafka), arranca con
`--reservations.outbox.enabled=false`. Las pruebas ya lo desactivan, salvo `OutboxPublisherIT`,
que levanta su propio Kafka con Testcontainers.

Ejecutar las pruebas:

```bash
./gradlew test
```

Compilar el empaquetado:

```bash
./gradlew clean build
```

En Windows, usar `gradlew.bat` en lugar de `./gradlew`.

### Cargar un horario de prueba

Con el perfil local, la Dirección de Programa (`direccion@unimagdalena.edu.co`, contraseña
`unimag2026`) carga el horario en dos pasos. El archivo de ejemplo está en
`src/test/resources/contracts/schedule-valid.csv` y usa recursos del inventario falso:

```bash
# Iniciar sesión y guardar la cookie
curl -c cookies.txt -H 'Content-Type: application/json' \
     -d '{"email":"direccion@unimagdalena.edu.co","password":"unimag2026"}' \
     http://localhost:8080/api/auth/login

# Paso 1: validar. No cambia ninguna reserva; devuelve el reporte por fila y el uploadId
curl -b cookies.txt -F file=@src/test/resources/contracts/schedule-valid.csv \
     -F academicTerm=2026-2 -F termStart=2026-11-02 -F termEnd=2026-11-30 \
     http://localhost:8080/api/schedules/validations

# Paso 2: confirmar, dentro de los 30 minutos siguientes
curl -b cookies.txt -X POST http://localhost:8080/api/schedules/<uploadId>/confirmation
```

Recargar el mismo archivo no duplica nada: las clases que ya estaban quedan `UNCHANGED`, y las que
desaparecieron del archivo se proponen para retirar.

### El estado de un recurso no es una columna

El Módulo 2 no guarda en qué estado está un recurso: lo deduce de las ocupaciones que tiene
escritas. Una reserva `CONFIRMADA` que cruza la franja es `RESERVADO` (o `BLOQUEO_ACADEMICO` si es
una clase), y cuando la franja termina el recurso vuelve a estar libre sin que corra ninguna tarea.
Un préstamo entregado sigue ocupando hasta su check-out, aunque esté vencido. `EN_MANTENIMIENTO` lo
dice el Módulo 1.

Para preguntar por un solo recurso está `GET /api/resources/{resourceId}/availability?date=…&start=…&end=…`
(UC8): dice si está libre y, si no, el motivo y hasta cuándo, con la misma regla que la lista. Es una ayuda para
revalidar antes de confirmar; la decisión final la toma la reserva dentro de su transacción.

Cada cambio de ocupación deja una fila en `status_change`, que se consulta con
`GET /api/resources/{resourceId}/status-changes`. Lo único que le avisamos al Módulo 1 es el **inicio
de uso** (`module2.resource.status.v1`): cuando la persona registra su llegada o cuando una clase
llega a su hora. Las dos válvulas para apagarlo son `reservations.module1.notify-use-started` y
`reservations.academic.use-start-enabled`.

## Estructura del repositorio

```
docs/
  modulo2.md              Resumen funcional del módulo
  unimag.png              Diagrama de casos de uso
  specs/                  Especificaciones por caso de uso
  plan/                   Planes técnicos: arquitectura y uno por caso de uso
src/
  main/java/edu/unimagdalena/reservasunimag/
    domain/               Modelo, puertos y casos de uso, sin Spring ni JPA
    infrastructure/       Adaptadores (REST, JPA, seguridad) y configuración
  main/resources/
    db/migration/         Migraciones de Flyway
    db/local/             Datos que solo se cargan con el perfil local
  test/java/edu/unimagdalena/reservasunimag/
build.gradle
```
