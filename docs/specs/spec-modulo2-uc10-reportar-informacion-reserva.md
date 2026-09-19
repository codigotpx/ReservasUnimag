# Feature Specification: Reportar información de la reserva

**Created**: 2026-09-03
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Reportar información de la reserva` — `<<include>>` de `Reservar recursos`, que informa al Módulo 3
**Prioridad global**: P3

## Contexto

El Módulo 3 no hace reservas ni las ve: las hace este módulo. Si nosotros no se la entregamos, no sabe qué se apartó, quién lo apartó, ni para cuándo. Y sin eso no puede hacer su trabajo: no puede ir a comprobar si la persona se presentó a la hora que dijo, no sabe cuándo vence un préstamo para reclamarlo, y no tiene contra qué comparar el check-out cuando el recurso vuelve.

Por eso este caso de uso es un `<<include>>` de `Reservar recursos`: no es un paso opcional ni posterior, ocurre **siempre y como parte de reservar**. En cuanto una reserva queda confirmada, su ficha sale hacia el Módulo 3.

Lo que va en esa ficha es lo que solo nosotros tenemos: la reserva, su titular, el recurso, y el tiempo que quedó apartado —la franja horaria si es un espacio, o el periodo de préstamo con su fecha de vencimiento si es un activo—. A partir de ahí el Módulo 3 vigila por su cuenta y nos devuelve lo que solo él puede ver: la ausencia que constata en el sitio (`Recibir reporte de no asistencia`) y el check-out cuando el recurso regresa (`Recibir check-out`).

Este caso de uso no informa cómo termina la reserva, y no le falta nada por eso. El Módulo 3 ya tiene con qué saberlo: si nos reporta una ausencia, la reserva no se usó; si le reportamos una cancelación (`Reportar cancelación de reserva`), se deshizo; si nos manda un check-out, el préstamo se cerró; y si no pasa ninguna de las tres, la reserva transcurrió con normalidad hasta el final de su franja. El silencio también es información, y no hace falta un aviso de cierre que lo repita.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Módulo 3 | Secundario | Destinatario de la ficha. Con ella supervisa el uso, comprueba la asistencia y arma la matriz de cumplimiento. |
| Estudiante / Monitor | Indirectos | Son los titulares de las reservas que se informan; no ejecutan este caso de uso. |

**Casos de uso relacionados**

- `Reservar recursos` — **caso base al que incluye** (`<<include>>`): sin reserva confirmada no hay nada que informar, y toda reserva confirmada se informa; ver [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md)
- `Importar horarios semestrales` — incluye a `Reservar recursos`, y por eso cada bloqueo académico también genera ficha, marcada con origen académico; ver [spec-modulo2-uc3-importar-horarios-semestrales.md](./spec-modulo2-uc3-importar-horarios-semestrales.md)
- `Recibir reporte de no asistencia` — la respuesta del Módulo 3 cuando comprueba, sobre la ficha que le mandamos, que el titular no apareció; ver [spec-modulo2-uc9-recibir-reporte-no-asistencia.md](./spec-modulo2-uc9-recibir-reporte-no-asistencia.md)
- `Recibir check-out` — la otra respuesta: el recurso volvió, y el Módulo 3 lo compara contra la fecha de vencimiento que le dimos aquí; ver [spec-modulo2-uc12-recibir-check-out.md](./spec-modulo2-uc12-recibir-check-out.md)
- `Reportar cancelación de reserva` — lo que sale hacia el Módulo 3 cuando la reserva informada aquí se deshace; ver [spec-modulo2-uc11-reportar-cancelacion-reserva.md](./spec-modulo2-uc11-reportar-cancelacion-reserva.md)
- `Consultar sanciones` — el camino de vuelta: las sanciones que el Módulo 3 arma con estas fichas son las que después se leen ahí; ver [spec-modulo2-uc6-consultar-sanciones.md](./spec-modulo2-uc6-consultar-sanciones.md)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Contarle al Módulo 3 qué se acaba de apartar (Priority: P3)

Como sistema, quiero enviarle al Módulo 3 la ficha de cada reserva en cuanto se confirma, para que pueda vigilar su uso, comprobar la asistencia y aplicar las sanciones que correspondan.

**Why this priority**: Es P3 porque reservar funciona sin ella: la reserva queda hecha y el recurso queda apartado igual. Pero sin esta ficha el Módulo 3 trabaja a ciegas —no sabe a quién ir a buscar ni qué esperar de vuelta— y toda la matriz de cumplimiento se queda sin entrada.

**Independent Test**: Se puede probar sola confirmando una reserva y verificando que salió la ficha con los datos completos y que quedó constancia del envío. No necesita que las sanciones ni los check-out estén implementados.

**Acceptance Scenarios**:

1. **Scenario**: Reserva de un espacio
   - **Given** un Estudiante confirma el "Salón 201" para el 2026-09-15 de 14:00 a 16:00
   - **When** la reserva queda `CONFIRMADA`
   - **Then** el sistema le envía al Módulo 3 la ficha con la reserva, el titular, el recurso y esa franja horaria

2. **Scenario**: Préstamo de un activo
   - **Given** un Estudiante confirma el préstamo del "Microscopio 07" para recogerlo el 2026-09-15 a las 10:00
   - **When** la reserva queda `CONFIRMADA`
   - **Then** la ficha incluye además la fecha y hora de recogida y el vencimiento calculado, que es contra lo que el Módulo 3 medirá el check-out

3. **Scenario**: El Módulo 3 no responde
   - **Given** el Módulo 3 está caído temporalmente
   - **When** un Estudiante confirma una reserva
   - **Then** la reserva se confirma igual y el recurso queda apartado igual; la ficha queda pendiente y se reintenta hasta entregarse

5. **Scenario**: Bloqueo académico que entra por la carga del semestre
   - **Given** la Dirección de Programa carga el horario 2026-2 y el "Salón 201" queda en `BLOQUEO_ACADEMICO` el lunes 2026-09-14 de 08:00 a 10:00 para Cálculo I
   - **When** se crea el bloqueo
   - **Then** el sistema le envía al Módulo 3 la ficha con el recurso, la franja, la asignatura, el programa y el docente, marcada con origen académico y sin titular sancionable

### Edge Cases

- **Reserva denegada**: si la reserva no llega a confirmarse —por conflicto académico, cupo o sanción— no hay ficha que enviar. Solo se informa lo que quedó `CONFIRMADA`.
- **Fichas repetidas**: si un envío se reintenta, el Módulo 3 no puede terminar contando dos veces la misma reserva ni vigilando dos veces al mismo titular.
- **Reserva de origen académico**: en el diagrama, `Importar horarios semestrales` incluye a `Reservar recursos`, así que cada bloqueo académico pasa por reservar y, con eso, también por este caso de uso: su ficha sale igual que la de cualquier reserva. Lo que cambia es lo que lleva: no tiene titular estudiantil sino la asignatura, el programa y el docente de la clase, y va marcada con origen académico para que el Módulo 3 no le atribuya ausencias ni sanciones a nadie. Volver a cargar el mismo horario no crea bloqueos repetidos (UC3 FR-007) y tampoco debe repetir fichas (FR-006).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE enviarle al Módulo 3 la ficha de cada reserva en el momento en que queda confirmada, como parte del flujo de `Reservar recursos`.
- **FR-002**: Cada ficha DEBE indicar el identificador de la reserva, su titular (o, si es un bloqueo académico, lo que pide FR-009), el recurso apartado y el tiempo que ocupa: la franja horaria cuando es un espacio, y el periodo de préstamo con su fecha y hora de vencimiento cuando es un activo.
- **FR-003**: El sistema NO DEBE decidir ni aplicar sanciones; solo entrega la información, y el Módulo 3 saca las consecuencias.
- **FR-004**: Si el Módulo 3 no está disponible, la reserva DEBE confirmarse igualmente y la ficha DEBE reintentarse hasta entregarse.
- **FR-005**: Reenviar una ficha NO DEBE producir una segunda reserva contabilizada para el Módulo 3.
- **FR-006**: El sistema DEBE conservar el registro de cada ficha enviada y de si llegó o no.
- **FR-007**: El sistema NO DEBE enviar ficha de una reserva que no llegó a confirmarse.
- **FR-008**: El sistema DEBE enviar también la ficha de cada bloqueo académico que entre por `Importar horarios semestrales`. Esa ficha DEBE ir marcada con origen académico, llevar la asignatura, el programa y el docente en lugar del titular, e indicar que no es sancionable.

### Key Entities

- **FichaDeReserva**: lo que se le manda al Módulo 3 cuando una reserva se confirma. Atributos: reserva, origen (estudiantil o académico), titular —o asignatura, programa y docente si el origen es académico—, recurso, franja horaria o periodo de préstamo, fecha y hora del envío, resultado del envío.
- **Reserva**: el apartado confirmado que da origen a la ficha.
- **PeriodoDePrestamo**: lo que se informa cuando el recurso es un activo, con su fecha y hora de vencimiento; es la referencia contra la que el Módulo 3 mide el check-out.
- **Usuario**: el titular de una reserva estudiantil, siempre un Estudiante o Monitor, sobre el que el Módulo 3 lleva el cumplimiento. Los bloqueos académicos no tienen Usuario titular.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las reservas confirmadas, incluidos los bloqueos académicos, quedan informadas al Módulo 3 con su titular —o su asignatura, programa y docente—, su recurso y su tiempo apartado.
- **SC-002**: La ficha sale en menos de 5 segundos desde que la reserva queda confirmada.
- **SC-003**: Cero reservas contabilizadas de más por un reenvío.
- **SC-004**: Cero fichas perdidas ante una caída del Módulo 3 de hasta 30 minutos.
- **SC-005**: Cero reservas denegadas informadas como si fueran confirmadas.
- **SC-006**: Cero fichas de origen académico que el Módulo 3 pueda tomar por reservas sancionables.
