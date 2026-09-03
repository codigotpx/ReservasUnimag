# Feature Specification: Notificar estado de recursos al finalizar reserva

**Created**: 2026-08-24
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Notificar estado de recursos al finalizar reserva` (se conecta directamente con el Módulo 3, sin depender de ningún otro caso de uso)
**Prioridad global**: P3

## Contexto

Cuando una reserva termina, alguien tiene que contar cómo terminó. Este caso de uso toma el cierre de cada reserva —se acabó la franja, la persona canceló, una clase la desplazó, nadie se presentó, el equipo volvió dañado— y se lo informa al Módulo 3, que es el que lleva el control de cumplimiento y la analítica de uso.

En el diagrama no cuelga de ningún otro óvalo: no lo incluye ni lo extiende nadie. Se dispara solo, por el final de la reserva, y su única conexión hacia afuera es el Módulo 3.

Conviene no confundirlo con `Actualizar estado de los recursos`, que es distinto y va por otro lado: aquel cambia el estado del recurso y se lo informa al **Módulo 1**, que es el dueño del inventario, cada vez que algo pasa. Este solo habla al final de la reserva, y le habla al **Módulo 3**.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Módulo 3 | Secundario | Destinatario del aviso; con él alimenta la matriz de cumplimiento y la analítica de uso. |
| Estudiante / Monitor | Indirectos | Son los titulares de las reservas que se cierran; no ejecutan este caso de uso. |

**Casos de uso relacionados**

- `Reservar recursos` — crea las reservas cuyo cierre se informa aquí; ver [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md)
- `Cancelar reserva` — una de las formas en que una reserva puede terminar; ver [spec-modulo2-uc4-cancelar-reserva.md](./spec-modulo2-uc4-cancelar-reserva.md)
- `Actualizar estado de los recursos` — el otro camino, el que habla con el Módulo 1 y mantiene el inventario al día; ver [spec-modulo2-uc7-actualizar-estado-recursos.md](./spec-modulo2-uc7-actualizar-estado-recursos.md)
- `Reportar no asistencia` — cuando la reserva termina porque nadie llegó; ver [spec-modulo2-uc9-reportar-no-asistencia.md](./spec-modulo2-uc9-reportar-no-asistencia.md)
- `Reportar fecha y hora de entrega` — cuando lo que termina es un préstamo de equipo; ver [spec-modulo2-uc10-reportar-fecha-hora-entrega.md](./spec-modulo2-uc10-reportar-fecha-hora-entrega.md)
- `Consultar reportes` — el camino de vuelta: con todo lo que se le informa aquí, el Módulo 3 arma las sanciones que después se leen desde allí; ver [spec-modulo2-uc6-consultar-reportes.md](./spec-modulo2-uc6-consultar-reportes.md)

**Catálogo de avisos**

| Aviso | Se envía cuando |
|---|---|
| `RESERVA_FINALIZADA` | La franja llegó a su fin con normalidad y el recurso quedó libre. |
| `RESERVA_CANCELADA` | El titular deshizo la reserva antes de que empezara. |
| `RESERVA_CANCELADA_POR_PRIORIDAD` | Una actividad docente desplazó la reserva del estudiante. |
| `RESERVA_CERRADA_POR_AUSENCIA` | Nadie se presentó y el recurso se liberó solo. |
| `RECURSO_CON_NOVEDAD` | El recurso volvió con un daño o una incidencia. |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Contarle al Módulo 3 cómo terminó cada reserva (Priority: P3)

Como sistema, quiero avisarle al Módulo 3 cómo terminó cada reserva y en qué estado quedó el recurso, para que pueda llevar el control de cumplimiento de cada persona y construir la analítica de uso de la universidad sin tener que adivinar nada.

**Why this priority**: Es P3 porque el módulo entrega valor sin ella: se puede consultar, reservar y cancelar igual. Pero sin este aviso el Módulo 3 se queda ciego, y toda la matriz de sanciones y de "score" de confianza se queda sin datos con qué trabajar.

**Independent Test**: Se puede probar sola provocando cada forma de terminar una reserva y comprobando, contra un simulador del Módulo 3, que llegó el aviso correcto con la reserva, la persona, el recurso, la franja y el motivo del cierre. No necesita que existan sanciones ni reportes.

**Acceptance Scenarios**:

1. **Scenario**: La reserva terminó con normalidad
   - **Given** un Estudiante usó la "Sala de Estudio 3" el 2026-09-01 de 10:00 a 12:00
   - **When** llegan las 12:00 y la reserva se cierra
   - **Then** el sistema le envía al Módulo 3 el aviso `RESERVA_FINALIZADA` con la persona, el recurso, la franja y el estado en que quedó el recurso

2. **Scenario**: La persona canceló a tiempo
   - **Given** un Estudiante tenía reservado el "Auditorio Menor" y lo canceló una hora antes
   - **When** se registra la cancelación
   - **Then** el sistema le envía al Módulo 3 el aviso `RESERVA_CANCELADA`, dejando claro que la cancelación fue del titular y no un incumplimiento

3. **Scenario**: Una clase desplazó la reserva
   - **Given** una reserva estudiantil fue cancelada automáticamente porque entró una actividad docente
   - **When** se ejecuta esa cancelación
   - **Then** el sistema le envía al Módulo 3 el aviso `RESERVA_CANCELADA_POR_PRIORIDAD`, indicando expresamente que **no** es responsabilidad de la persona

4. **Scenario**: Nadie se presentó
   - **Given** pasaron los 10 minutos de plazo sin que nadie usara el recurso
   - **When** la reserva se cierra por ausencia
   - **Then** el sistema le envía al Módulo 3 el aviso `RESERVA_CERRADA_POR_AUSENCIA` con la persona, el recurso y la franja

5. **Scenario**: El equipo volvió dañado
   - **Given** un Estudiante devuelve el "Videobeam 12" y quien lo recibe reporta un daño
   - **When** se cierra ese préstamo
   - **Then** el sistema le envía al Módulo 3 el aviso `RECURSO_CON_NOVEDAD` junto con la descripción del daño

6. **Scenario**: El Módulo 3 no responde
   - **Given** el Módulo 3 está caído temporalmente
   - **When** una reserva termina
   - **Then** la reserva se cierra igual y el recurso se libera igual; el aviso queda pendiente y se vuelve a intentar hasta que llegue

### Edge Cases

- **Cierre masivo por importación**: una carga de horarios que desplaza decenas de reservas debe generar un aviso por cada una, sin agruparlas de forma que se pierda de vista a quién le tocó.
- **Avisos repetidos**: si un aviso se reintenta, el Módulo 3 no puede terminar contando dos veces el mismo cierre y sancionando dos veces a la misma persona.
- **Orden de los avisos**: si sobre el mismo recurso se cierran dos reservas seguidas, los avisos deben llegar en el orden en que ocurrieron.
- **Reserva que termina y préstamo que sigue abierto**: si la franja se acabó pero el equipo no ha vuelto, el aviso debe reflejar que el recurso no quedó libre.
- **Motivo del cierre siempre presente**: ningún aviso puede salir sin decir por qué terminó la reserva; de eso depende que el Módulo 3 distinga a quien cumplió de quien no.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE enviarle al Módulo 3 un aviso cada vez que una reserva termina, cualquiera que sea la forma en que terminó.
- **FR-002**: Cada aviso DEBE indicar el tipo de cierre, la reserva, la persona, el recurso, la franja, el motivo y la fecha y hora.
- **FR-003**: El aviso DEBE distinguir con claridad los cierres que son responsabilidad de la persona (ausencia, devolución tardía, daño) de los que no lo son (cancelación a tiempo, desplazamiento por prioridad académica).
- **FR-004**: El sistema NO DEBE decidir ni aplicar sanciones; solo informa el hecho, y el Módulo 3 saca las consecuencias.
- **FR-005**: Si el Módulo 3 no está disponible, la reserva DEBE cerrarse igualmente y el aviso DEBE reintentarse hasta entregarse.
- **FR-006**: Reenviar un aviso NO DEBE producir un segundo cierre contabilizado para la misma reserva.
- **FR-007**: El sistema DEBE conservar el registro de cada aviso enviado y de si llegó o no.
- **FR-008**: Cuando el recurso vuelva con una novedad, el aviso DEBE incluir su descripción.

### Key Entities

- **AvisoDeCierre**: mensaje que se le manda al Módulo 3 cuando una reserva termina. Atributos: tipo de cierre, reserva, persona, recurso, franja, motivo, novedad si la hubo, fecha y hora, resultado del envío.
- **Reserva**: el apartado que termina y da origen al aviso.
- **Recurso**: el espacio o equipo cuyo estado final se informa.
- **Usuario**: la persona titular de la reserva que se cierra.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las reservas que terminan generan exactamente un aviso al Módulo 3.
- **SC-002**: El aviso llega al Módulo 3 dentro de los 5 minutos siguientes al cierre de la reserva.
- **SC-003**: Cero avisos perdidos ante una caída del Módulo 3 de hasta 30 minutos.
- **SC-004**: Cero cierres contabilizados dos veces por culpa de un reenvío.
- **SC-005**: Ninguna reserva se queda sin cerrar por culpa de un error al avisar al Módulo 3.
