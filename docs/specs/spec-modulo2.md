# Módulo 2 — Operación de Reservas y Priorización Académica (índice de specs)

**Created**: 2026-08-24

Este documento es el índice del módulo. La especificación detallada está dividida en **un archivo por caso de uso** del diagrama (`Unimag3.png`, fuente en `unimag3.drawio`), cada uno con su propia historia de usuario, escenarios de aceptación, requisitos funcionales, entidades y criterios de éxito, siguiendo [spec-template.md](./spec-template.md).

## Contexto

Motor de reglas de negocio encargado de gestionar el uso de los recursos físicos de la universidad (salones, laboratorios, salas de estudio, equipos) y de resolver los conflictos de interés entre la actividad académica institucional y el apartado estudiantil.

## Actores

| Actor | Tipo | Descripción |
|---|---|---|
| Estudiante | Primario (humano) | Consulta recursos, reserva y cancela sus propias reservas. |
| Monitor | Primario (humano) | Especialización de Estudiante: hereda todas sus capacidades. |
| Dirección de Programa | Primario (humano) | Importa la carga académica semestral y consulta el catálogo de recursos. |
| Módulo 1 | Secundario (sistema) | Inventario físico: aporta el estado real de los recursos y recibe cada cambio de estado. |
| Módulo 3 | Secundario (sistema) | Control de uso, sanciones y analítica: recibe las ausencias, las cancelaciones, las devoluciones y el cierre de cada reserva, y **provee de vuelta** el reporte de cumplimiento con las sanciones vigentes. Es quien decide y aplica las sanciones. |

## Trazabilidad diagrama → specs

| Óvalo del diagrama de casos de uso | Prioridad | Archivo de especificación |
|---|---|---|
| Consultar recursos | P1 | [spec-modulo2-uc1-consultar-recursos.md](./spec-modulo2-uc1-consultar-recursos.md) |
| Reservar recursos `<<extend>>` | P1 | [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md) |
| Importar horarios semestrales | P2 | [spec-modulo2-uc3-importar-horarios-semestrales.md](./spec-modulo2-uc3-importar-horarios-semestrales.md) |
| Cancelar reserva `<<extend>>` | P2 | [spec-modulo2-uc4-cancelar-reserva.md](./spec-modulo2-uc4-cancelar-reserva.md) |
| Notificar estado de recursos al finalizar reserva | P3 | [spec-modulo2-uc5-notificar-estado-recursos.md](./spec-modulo2-uc5-notificar-estado-recursos.md) |
| Consultar reportes `<<include>>` | P1 | [spec-modulo2-uc6-consultar-reportes.md](./spec-modulo2-uc6-consultar-reportes.md) |
| Actualizar estado de los recursos `<<include>>` | P1 | [spec-modulo2-uc7-actualizar-estado-recursos.md](./spec-modulo2-uc7-actualizar-estado-recursos.md) |
| Consultar disponibilidad de los recursos | P1 | [spec-modulo2-uc8-consultar-disponibilidad-recursos.md](./spec-modulo2-uc8-consultar-disponibilidad-recursos.md) |
| Reportar no asistencia | P2 | [spec-modulo2-uc9-reportar-no-asistencia.md](./spec-modulo2-uc9-reportar-no-asistencia.md) |
| Reportar fecha y hora de entrega | P3 | [spec-modulo2-uc10-reportar-fecha-hora-entrega.md](./spec-modulo2-uc10-reportar-fecha-hora-entrega.md) |
| Reportar cancelación de reserva | P2 | [spec-modulo2-uc11-reportar-cancelacion-reserva.md](./spec-modulo2-uc11-reportar-cancelacion-reserva.md) |

## Orden de entrega sugerido

1. **P1 (MVP)**: Consultar recursos + Reservar recursos, con Consultar disponibilidad de los recursos, Actualizar estado de los recursos y Consultar reportes — consultar y apartar, el núcleo demostrable. Estos tres últimos no se ven por fuera, pero sin ellos el sistema muestra información falsa o deniega sin poder explicar por qué.
2. **P2**: Importar horarios semestrales + Cancelar reserva + Reportar cancelación de reserva + Reportar no asistencia — sostenibilidad de la carga académica, cierre del ciclo de vida de la reserva y control de las reservas fantasma. `Reportar cancelación de reserva` va pegado a `Cancelar reserva`: sin él, el Módulo 3 no puede distinguir a quien liberó a tiempo de quien no apareció.
3. **P3**: Notificar estado de recursos al finalizar reserva + Reportar fecha y hora de entrega — lo que termina de alimentar al Módulo 3 con el historial que dejan los anteriores.

## Diccionario de errores consolidado

| Código | Caso de uso | Causa |
|---|---|---|
| `RES-001` | Reservar recursos | Conflicto académico: el recurso está reservado para actividad docente. |
| `RES-002` | Reservar recursos | Límite máximo de préstamos vigentes alcanzado. |
| `RES-003` | Reservar recursos | Sanción activa sobre el usuario. |
| `RES-004` | Reservar recursos | El recurso acaba de ser tomado (conflicto de concurrencia). |
| `CAN-001` | Cancelar reserva | No autorizado sobre esta reserva (no es el titular). |
| `CAN-002` | Cancelar reserva | La reserva ya no es cancelable (ya inició o finalizó). |

## Puntos abiertos transversales

- El Monitor ya no tiene ninguna capacidad propia dentro del módulo: heredaba de Estudiante y lo que lo distinguía era consultar reportes, que ahora ejecuta el sistema. [NEEDS CLARIFICATION]
- Política de sanción retroactiva: ¿se cancelan las reservas ya confirmadas de un estudiante sancionado, o solo se le impide crear nuevas? [NEEDS CLARIFICATION]
- Umbral de no-show: definido en 10 minutos desde el inicio de la franja (ver `Reservar recursos`, FR-010). Queda abierto su encadenamiento con las sanciones. [NEEDS CLARIFICATION: cuántas ausencias acumuladas originan sanción y cuánto dura]
- Parámetros no definidos: límite máximo de préstamos simultáneos, duración máxima de una reserva y antelación mínima de cancelación. [NEEDS CLARIFICATION]
- ¿Se permiten reservas o franjas que crucen la medianoche? [NEEDS CLARIFICATION]
- Solapamiento entre `Reportar cancelación de reserva` (óvalo nuevo del diagrama 3) y los avisos `RESERVA_CANCELADA` / `RESERVA_CANCELADA_POR_PRIORIDAD` de `Notificar estado de recursos al finalizar reserva`. [NEEDS CLARIFICATION: ver P-12]
