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

Ejecutar las pruebas:

```bash
./gradlew test
```

Compilar el empaquetado:

```bash
./gradlew clean build
```

En Windows, usar `gradlew.bat` en lugar de `./gradlew`.

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
