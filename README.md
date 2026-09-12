# ReservasUnimag

Sistema de reservas de recursos físicos de la Universidad del Magdalena: salones,
laboratorios, salas de estudio y equipos.

Este repositorio corresponde al **Módulo 2 — Operación de Reservas y Priorización
Académica**, el motor de reglas de negocio que gestiona el uso de los recursos y
resuelve los conflictos entre la actividad académica institucional y el apartado
estudiantil.

> **Estado actual:** el proyecto está en fase de especificación. El código es todavía
> el esqueleto generado por Spring Initializr; la documentación funcional es la parte
> viva del repositorio.

## Documentación

| Documento | Contenido |
|---|---|
| [docs/modulo2.md](docs/modulo2.md) | Resumen funcional del módulo. |
| [docs/unimag.png](docs/unimag.png) | Diagrama de casos de uso. |
| [docs/specs/](docs/specs/) | Especificaciones detalladas, una por caso de uso. |
| [docs/specs/spec-template.md](docs/specs/spec-template.md) | Plantilla base de las especificaciones. |

### Casos de uso

| # | Caso de uso | Prioridad | Especificación |
|---|---|---|---|
| UC1 | Consultar recursos | P1 | [spec-modulo2-uc1-consultar-recursos.md](docs/specs/spec-modulo2-uc1-consultar-recursos.md) |
| UC2 | Reservar recursos `<<extend>>` | P1 | [spec-modulo2-uc2-reservar-recursos.md](docs/specs/spec-modulo2-uc2-reservar-recursos.md) |
| UC3 | Importar horarios semestrales | P2 | [spec-modulo2-uc3-importar-horarios-semestrales.md](docs/specs/spec-modulo2-uc3-importar-horarios-semestrales.md) |
| UC4 | Cancelar reserva `<<extend>>` | P2 | [spec-modulo2-uc4-cancelar-reserva.md](docs/specs/spec-modulo2-uc4-cancelar-reserva.md) |
| UC6 | Consultar sanciones `<<include>>` | P1 | [spec-modulo2-uc6-consultar-sanciones.md](docs/specs/spec-modulo2-uc6-consultar-sanciones.md) |
| UC7 | Actualizar estado de los recursos `<<include>>` | P1 | [spec-modulo2-uc7-actualizar-estado-recursos.md](docs/specs/spec-modulo2-uc7-actualizar-estado-recursos.md) |
| UC8 | Consultar disponibilidad de los recursos | P1 | [spec-modulo2-uc8-consultar-disponibilidad-recursos.md](docs/specs/spec-modulo2-uc8-consultar-disponibilidad-recursos.md) |
| UC9 | Recibir reporte de no asistencia | P2 | [spec-modulo2-uc9-recibir-reporte-no-asistencia.md](docs/specs/spec-modulo2-uc9-recibir-reporte-no-asistencia.md) |
| UC10 | Reportar información de la reserva `<<include>>` | P3 | [spec-modulo2-uc10-reportar-informacion-reserva.md](docs/specs/spec-modulo2-uc10-reportar-informacion-reserva.md) |
| UC11 | Reportar cancelación de reserva `<<include>>` | P2 | [spec-modulo2-uc11-reportar-cancelacion-reserva.md](docs/specs/spec-modulo2-uc11-reportar-cancelacion-reserva.md) |
| UC12 | Recibir check-out | P3 | [spec-modulo2-uc12-recibir-check-out.md](docs/specs/spec-modulo2-uc12-recibir-check-out.md) |

## Actores

| Actor | Tipo | Descripción |
|---|---|---|
| Estudiante | Primario (humano) | Consulta recursos, reserva y cancela sus propias reservas. |
| Monitor | Primario (humano) | Especialización de Estudiante: hereda sus capacidades y consulta reportes de uso. |
| Dirección de Programa | Primario (humano) | Importa la carga académica semestral y consulta reportes institucionales. |
| Módulo 1 | Secundario (sistema) | Identidad, recursos y notificaciones; recibe los eventos de cambio de estado. |
| Módulo 3 | Secundario (sistema) | Analítica y reportería; consume la información consolidada de uso. |

## Stack

- Java 17
- Spring Boot 4.1.1 (Web MVC, Data JPA, RestClient, Web Services)
- PostgreSQL
- Testcontainers para las pruebas de integración
- Maven (con wrapper incluido)

## Requisitos

- JDK 17 o superior
- Docker en ejecución (lo usan Testcontainers y el arranque de desarrollo)

## Cómo ejecutarlo

Levantar la aplicación en desarrollo, con la base de datos PostgreSQL en un contenedor
gestionado automáticamente por Testcontainers:

```bash
./mvnw spring-boot:test-run
```

Ejecutar las pruebas:

```bash
./mvnw test
```

Compilar el empaquetado:

```bash
./mvnw clean package
```

En Windows, usar `mvnw.cmd` en lugar de `./mvnw`.

## Estructura del repositorio

```
docs/
  modulo2.md              Resumen funcional del módulo
  unimag.png              Diagrama de casos de uso
  specs/                  Especificaciones por caso de uso
src/
  main/java/edu/unimagdalena/reservasunimag/
  main/resources/
  test/java/edu/unimagdalena/reservasunimag/
pom.xml
```
