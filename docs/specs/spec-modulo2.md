# Módulo 2 — Operación de Reservas y Priorización Académica (índice de specs)

**Created**: 2026-08-24

Este documento es el índice del módulo. La especificación detallada está dividida en **un archivo por caso de uso** del diagrama (`Unimag.drawio2.png`), cada uno con su propia historia de usuario, escenarios de aceptación, requisitos funcionales, entidades y criterios de éxito, siguiendo [spec-template.md](./spec-template.md).

## Contexto

Motor de reglas de negocio encargado de gestionar el uso de los recursos físicos de la universidad (salones, laboratorios, salas de estudio, equipos) y de resolver los conflictos de interés entre la actividad académica institucional y el apartado estudiantil.

## Actores

| Actor | Tipo | Descripción |
|---|---|---|
| Estudiante | Primario (humano) | Consulta recursos, reserva y cancela sus propias reservas. |
| Monitor | Primario (humano) | Especialización de Estudiante: hereda todas sus capacidades y además consulta reportes de uso. |
| Dirección de Programa | Primario (humano) | Importa la carga académica semestral y consulta reportes institucionales. |
| Módulo 1 | Secundario (sistema) | Módulo de identidad/recursos y notificaciones; recibe los eventos de cambio de estado. |
| Módulo 3 | Secundario (sistema) | Módulo de analítica/reportería; consume y provee la información consolidada de uso. |

## Trazabilidad diagrama → specs

| Óvalo del diagrama de casos de uso | Prioridad | Archivo de especificación |
|---|---|---|
| Consultar recursos | P1 | [spec-modulo2-uc1-consultar-recursos.md](./spec-modulo2-uc1-consultar-recursos.md) |
| Reservar recursos `<<extend>>` | P1 | [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md) |
| Importar horarios semestrales | P2 | [spec-modulo2-uc3-importar-horarios-semestrales.md](./spec-modulo2-uc3-importar-horarios-semestrales.md) |
| Cancelar reserva `<<extend>>` | P2 | [spec-modulo2-uc4-cancelar-reserva.md](./spec-modulo2-uc4-cancelar-reserva.md) |
| Notificar estado de recursos `<<include>>` | P3 | [spec-modulo2-uc5-notificar-estado-recursos.md](./spec-modulo2-uc5-notificar-estado-recursos.md) |
| Consultar reportes `<<include>>` | P3 | [spec-modulo2-uc6-consultar-reportes.md](./spec-modulo2-uc6-consultar-reportes.md) |

## Orden de entrega sugerido

1. **P1 (MVP)**: Consultar recursos + Reservar recursos — consultar y apartar, el núcleo demostrable.
2. **P2**: Importar horarios semestrales + Cancelar reserva — sostenibilidad de la carga académica y cierre del ciclo de vida de la reserva.
3. **P3**: Notificar estado de recursos + Consultar reportes — transversales que se apoyan en el historial y en los Módulos 1 y 3.

## Diccionario de errores consolidado

| Código | Caso de uso | Causa |
|---|---|---|
| `RES-001` | Reservar recursos | Conflicto académico: el recurso está reservado para actividad docente. |
| `RES-002` | Reservar recursos | Límite máximo de préstamos vigentes alcanzado. |
| `RES-003` | Reservar recursos | Sanción activa sobre el usuario. |
| `RES-004` | Reservar recursos | El recurso acaba de ser tomado (conflicto de concurrencia). |
| `CAN-001` | Cancelar reserva | No autorizado sobre esta reserva (no es el titular). |
| `CAN-002` | Cancelar reserva | La reserva ya no es cancelable (ya inició o finalizó). |
| `REP-001` | Consultar reportes | No autorizado: el rol no tiene acceso a los reportes. |

## Puntos abiertos transversales

- Política de sanción retroactiva: ¿se cancelan las reservas ya confirmadas de un estudiante sancionado, o solo se le impide crear nuevas? [NEEDS CLARIFICATION]
- Umbral de no-show y su relación con las sanciones. [NEEDS CLARIFICATION]
- Parámetros no definidos: límite máximo de préstamos simultáneos, duración máxima de una reserva y antelación mínima de cancelación. [NEEDS CLARIFICATION]
- ¿Se permiten reservas o franjas que crucen la medianoche? [NEEDS CLARIFICATION]
