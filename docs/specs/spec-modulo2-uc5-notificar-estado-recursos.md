# Feature Specification: Notificar estado de recursos al finalizar reserva

**Created**: 2026-08-24
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Notificar estado de recursos al finalizar reserva` (se conecta directamente con el Módulo 3, sin depender de ningún otro caso de uso)
**Prioridad global**: P3

## Contexto

Cuando una reserva termina, alguien tiene que contar en qué estado terminó el recurso después del uso que se le dio. Este caso de uso toma el cierre de cada reserva y se lo informa al Módulo 3, que es el que lleva el control de sanciones y la analítica de uso.

En el diagrama no cuelga de ningún otro óvalo: Se dispara solo, por el final de la reserva, y su única conexión hacia afuera es el Módulo 3.

Conviene no confundirlo con `Actualizar estado de los recursos`, que es distinto y va por otro lado: aquel cambia el estado del recurso y se lo informa al **Módulo 1**, que es el dueño del inventario, cada vez que algo pasa. Este solo habla al final de la reserva, y le habla al **Módulo 3**.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Módulo 3 | Secundario | Destinatario del aviso; con él alimenta la matriz de cumplimiento y la analítica de uso. |
| Estudiante / Monitor | Indirectos | Son los titulares de las reservas que se cierran; no ejecutan este caso de uso. |

**Casos de uso relacionados**

- `Reservar recursos` — crea las reservas cuyo cierre se informa aquí; ver [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md)
- `Reportar fecha y hora de entrega` — índica la fecha y hora en la que se entregó un recurso, marcando la finalización de la reserva; ver [spec-modulo2-uc10-reportar-fecha-hora-entrega.md](./spec-modulo2-uc10-reportar-fecha-hora-entrega.md)
- `Consultar reportes` — el camino de vuelta: con todo lo que se le informa en "notificar estado de recursos al finalizar reserva", el Módulo 3 arma las sanciones que después se consultan; ver [spec-modulo2-uc6-consultar-reportes.md](./spec-modulo2-uc6-consultar-reportes.md)

**Catálogo de avisos**

| Aviso                 | Se envía cuando                           |
|-----------------------|-------------------------------------------|
| `RECURSO_SIN_NOVEDAD` | El recurso quedó sin daños o incidencias. |
| `RECURSO_CON_NOVEDAD` | El recurso quedó con daños o incidencias. |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Contarle al Módulo 3 cómo terminó cada reserva (Priority: P3)

Como sistema, quiero avisarle al Módulo 3 en qué estado quedó el recurso, para que pueda llevar el control del buen uso de los recursos de cada persona y construir las sanciones pertinentes de los usuarios que así lo requieran.

**Why this priority**: Es P3 porque el módulo entrega valor sin ella: se puede consultar, reservar y cancelar igual. Pero sin este aviso el Módulo 3 y toda la matriz de sanciones y de "score" de confianza queda incompleta sin los datos que aquí notificamos.

**Independent Test**: Se puede probar sola provocando la finalización de una reserva y comprobando, contra un simulador del Módulo 3, que llegó el aviso correcto con la reserva, la persona, el recurso y la franja. No necesita que existan sanciones ni reportes.

**Acceptance Scenarios**:

1. **Scenario**: La reserva terminó con normalidad
   - **Given** un Estudiante usó la "Sala de Estudio 3" el 2026-09-01 de 10:00 a 12:00
   - **When** llegan las 12:00 y la reserva se cierra
   - **Then** el sistema le envía al Módulo 3 la notificación con la persona, el recurso, la franja y el estado en que quedó el recurso

2. **Scenario**: Una clase desplazó la reserva
   - **Given** una reserva estudiantil fue cancelada automáticamente porque entró una actividad docente
   - **When** se ejecuta esa cancelación
   - **Then** el sistema no ejecuta la notificación, ya que el recurso no se llegó a utilizar

3. **Scenario**: Nadie se presentó
   - **Given** pasaron los 10 minutos de plazo sin que nadie usara el recurso
   - **When** la reserva se cierra por ausencia
   - **Then** el sistema no ejecuta la notificación, ya que, de nuevo, el recurso no se llegó a utilizar

4. **Scenario**: El recurso volvió sano y salvo
   - **Given** un Estudiante devuelve el "Videobeam 12" y quien lo recibe no nota ningún tipo de daños
   - **When** se cierra esa reserva
   - **Then** el sistema le envía al Módulo 3 el aviso `RECURSO_SIN_NOVEDAD`

5. **Scenario**: El equipo volvió dañado
   - **Given** un Estudiante finaliza su reserva de la "Sala de Estudio 2" y el encargado de esta nota un daño
   - **When** finaliza esa reserva
   - **Then** el sistema le envía al Módulo 3 el aviso `RECURSO_CON_NOVEDAD` junto con la descripción del daño

6. **Scenario**: El Módulo 3 no responde
   - **Given** el Módulo 3 está caído temporalmente
   - **When** una reserva termina
   - **Then** la reserva se cierra igual y el recurso se libera igual; el aviso queda pendiente y se vuelve a intentar hasta que llegue

### Edge Cases

- **Cierre masivo por importación**: una carga de horarios que desplaza decenas de reservas debe generar un aviso por cada una, sin agruparlas de forma que se pierda de vista a quién le tocó.
- **Avisos repetidos**: si un aviso se reintenta, el Módulo 3 no puede terminar contando dos veces el mismo cierre y sancionando dos veces a la misma persona.
- **Reserva que termina y préstamo que sigue abierto**: si la franja se acabó pero el recurso no ha sido devuelto, no se puede notificar ningún aviso, cuando sea entregado sí.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE enviarle al Módulo 3 un aviso cada vez que una reserva en la que se utilizó el recurso termina.
- **FR-002**: Cada aviso DEBE indicar la reserva, la persona, el recurso, la franja, y la fecha y hora.
- **FR-003**: El sistema NO DEBE decidir ni aplicar sanciones; solo informa el hecho, y el Módulo 3 saca las consecuencias.
- **FR-004**: Si el Módulo 3 no está disponible, la reserva DEBE cerrarse igualmente y el aviso DEBE reintentarse hasta entregarse.
- **FR-005**: Reenviar un aviso NO DEBE producir un segundo cierre contabilizado para la misma reserva.
- **FR-006**: El sistema DEBE conservar el registro de cada aviso enviado y de si llegó o no.
- **FR-007**: Cuando el recurso vuelva con una novedad, el aviso DEBE incluir su descripción.

### Key Entities

- **AvisoDeCierre**: mensaje que se le manda al Módulo 3 cuando una reserva termina. Atributos: aviso, reserva, persona, recurso, franja, fecha y hora, resultado del envío.
- **Reserva**: el apartado que termina y da origen al aviso.
- **Recurso**: el espacio o equipo cuyo estado final se informa.
- **Usuario**: la persona titular de la reserva que se cierra.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El aviso llega al Módulo 3 dentro de los 5 minutos siguientes al cierre de la reserva.
- **SC-002**: Cero avisos perdidos ante una caída del Módulo 3 de hasta 30 minutos.
- **SC-003**: Cero cierres contabilizados dos veces por culpa de un reenvío.
- **SC-004**: Ninguna reserva se queda sin cerrar por culpa de un error al avisar al Módulo 3.
