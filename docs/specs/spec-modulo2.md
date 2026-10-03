# Módulo 2 — Operación de Reservas y Priorización Académica (índice de specs)

**Created**: 2026-08-24

Este documento es el índice del módulo. La especificación detallada está dividida en **un archivo por caso de uso** del diagrama vigente (`unimag.png`, fuente en `unimag.drawio`), cada uno con su propia historia de usuario, escenarios de aceptación, requisitos funcionales, entidades y criterios de éxito, siguiendo [spec-template.md](./spec-template.md).

## Contexto

Motor de reglas de negocio encargado de gestionar el uso de los recursos físicos de la universidad —espacios (salones, laboratorios, salas de estudio) y activos (libros, microscopios, videobeams)— y de resolver los conflictos de interés entre la actividad académica institucional y el apartado estudiantil.

## Actores

| Actor | Tipo | Descripción |
|---|---|---|
| Estudiante | Primario (humano) | Consulta recursos, reserva y cancela sus propias reservas. |
| Monitor | Primario (humano) | Especialización de Estudiante: hereda todas sus capacidades. |
| Dirección de Programa | Primario (humano) | Importa la carga académica semestral y consulta el catálogo de recursos. |
| Módulo 1 | Secundario (sistema) | Inventario físico: es dueño del catálogo y del estado operativo de cada recurso (`DISPONIBLE`, `EN_USO`, `EN_MANTENIMIENTO`). El Módulo 2 lo consulta y no le guarda reservas ni bloqueos. |
| Módulo 3 | Secundario (sistema) | Control de uso, sanciones y analítica: **recibe** de nosotros la ficha de cada reserva confirmada y las cancelaciones; **nos reporta** lo que solo él ve, las ausencias que constata y el check-out de cada activo que vuelve; y **provee de vuelta** el reporte de cumplimiento con las sanciones vigentes. Es quien decide y aplica las sanciones. |

## Reparto de estados entre módulos

[gestionunimag.md](../gestionunimag.md) lista juntos los cinco estados de un recurso, pero no todos son del mismo módulo. Según lo que aclaró el profesor en clase, cada módulo es independiente y maneja solo lo suyo: el Módulo 1 lleva cómo está el recurso físicamente, y el Módulo 2, que es el de reservas, lleva todo lo que sale de reservar. Funciona como Rappi con un restaurante: el restaurante sabe si está abierto o si tiene el plato; Rappi guarda los pedidos, y le pregunta al restaurante antes de cruzarlo con lo suyo.

| Estado | Dueño | Qué significa |
|---|---|---|
| `DISPONIBLE`, `EN_USO`, `EN_MANTENIMIENTO` | Módulo 1 | Estado operativo del recurso. El Módulo 2 lo consulta; no lo guarda ni lo decide. |
| `RESERVADO`, `BLOQUEO_ACADEMICO` | Módulo 2 | Ocupación de una franja por una reserva o una clase, o de un periodo por un préstamo. Vive en la base del Módulo 2, y el Módulo 1 no la necesita. |
| `CONFIRMADA`, `CANCELADA`, `CANCELADA_POR_PRIORIDAD_ACADEMICA`, `CANCELADA_POR_RECURSO_NO_DISPONIBLE`, `FINALIZADA` | Módulo 2 | Estados de la reserva. |

Al estudiante se le sigue mostrando una sola etiqueta por recurso, que arma el Módulo 2 juntando las dos fuentes. Si en una franja aplica más de una, se muestra la de mayor prioridad: `EN_MANTENIMIENTO`, luego `BLOQUEO_ACADEMICO`, luego `EN_USO` o `RESERVADO`, y por último `DISPONIBLE`.

Lo que el Módulo 3 constata en el sitio sigue la misma regla: los daños de un recurso se los reporta directamente al Módulo 1, y al Módulo 2 solo le llega lo que toca a la reserva, es decir, la ausencia y el check-out que cierra el préstamo. Si el Módulo 2 le sigue avisando algo al Módulo 1, y cómo se entera de que un recurso entró a mantenimiento, está en P-20 de [pendientes-clarificacion.md](./pendientes-clarificacion.md).

## Trazabilidad diagrama → specs

| Óvalo del diagrama de casos de uso | Prioridad | Archivo de especificación |
|---|---|---|
| Consultar recursos | P1 | [spec-modulo2-uc1-consultar-recursos.md](./spec-modulo2-uc1-consultar-recursos.md) |
| Reservar recursos `<<extend>>` | P1 | [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md) |
| Importar horarios semestrales | P2 | [spec-modulo2-uc3-importar-horarios-semestrales.md](./spec-modulo2-uc3-importar-horarios-semestrales.md) |
| Cancelar reserva `<<extend>>` | P2 | [spec-modulo2-uc4-cancelar-reserva.md](./spec-modulo2-uc4-cancelar-reserva.md) |
| Consultar sanciones `<<include>>` | P1 | [spec-modulo2-uc6-consultar-sanciones.md](./spec-modulo2-uc6-consultar-sanciones.md) |
| Actualizar estado de los recursos `<<include>>` | P1 | [spec-modulo2-uc7-actualizar-estado-recursos.md](./spec-modulo2-uc7-actualizar-estado-recursos.md) |
| Consultar disponibilidad de los recursos `<<include>>` | P1 | [spec-modulo2-uc8-consultar-disponibilidad-recursos.md](./spec-modulo2-uc8-consultar-disponibilidad-recursos.md) |
| Recibir reporte de no asistencia `<<extend>>` | P2 | [spec-modulo2-uc9-recibir-reporte-no-asistencia.md](./spec-modulo2-uc9-recibir-reporte-no-asistencia.md) |
| Reportar información de la reserva `<<include>>` | P3 | [spec-modulo2-uc10-reportar-informacion-reserva.md](./spec-modulo2-uc10-reportar-informacion-reserva.md) |
| Reportar cancelación de reserva `<<include>>` | P2 | [spec-modulo2-uc11-reportar-cancelacion-reserva.md](./spec-modulo2-uc11-reportar-cancelacion-reserva.md) |
| Recibir check-out | P3 | [spec-modulo2-uc12-recibir-check-out.md](./spec-modulo2-uc12-recibir-check-out.md) |

### Especificaciones que no son casos de uso

Reglas de negocio que no tienen óvalo propio en el diagrama y se resuelven con casos de uso que ya existen.

| Regla | Prioridad | Archivo de especificación |
|---|---|---|
| Cancelar reservas por mantenimiento de un espacio (sale de `Recibir check-out`) | P3 | [spec-modulo2-cancelar-por-mantenimiento.md](spec-modulo2-13-cancelar-por-mantenimiento.md) |

## Orden de entrega sugerido

1. **P1 (MVP)**: Consultar recursos + Reservar recursos, con Consultar disponibilidad de los recursos, Actualizar estado de los recursos y Consultar sanciones — consultar y apartar, el núcleo demostrable. Estos tres últimos no se ven por fuera, pero sin ellos el sistema muestra información falsa o deniega sin poder explicar por qué.
2. **P2**: Importar horarios semestrales + Cancelar reserva + Reportar cancelación de reserva + Recibir reporte de no asistencia — sostenibilidad de la carga académica, cierre del ciclo de vida de la reserva y control de las reservas fantasma. `Reportar cancelación de reserva` va pegado a `Cancelar reserva`: sin él, el Módulo 3 no puede distinguir a quien liberó a tiempo de quien no apareció.
3. **P3**: Reportar información de la reserva + Recibir check-out — la conversación completa con el Módulo 3: le mandamos la ficha de lo que se aparta y recibimos de vuelta el cierre de cada préstamo. Sin `Recibir check-out` ningún activo prestado vuelve nunca a estar disponible.

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
- Parámetros ya definidos: **límite máximo de préstamos simultáneos, 3 reservas vigentes por persona** (`Reservar recursos`, FR-008) y **antelación mínima de cancelación, 10 minutos** (`Cancelar reserva`, FR-007). La **duración máxima de la reserva de un espacio** quedó en **2 horas continuas** (`Reservar recursos`, FR-009), y el **plazo de préstamo de un activo** depende de su tipo y viene como atributo del Módulo 1, en días hábiles (`Reservar recursos`, FR-012).
- **Horario de reservas y cambio de día**: Se definió que la ventana de operación es de 06:00 a 22:00 del mismo día (hora Colombia, `America/Bogota`). No se permiten reservas nocturnas (de 10:00 p. m. a 06:00 a. m. del día siguiente) ni franjas que crucen la medianoche.
