# Feature Specification: Consultar reportes

**Created**: 2026-08-24
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Consultar reportes` (lo ejecuta el propio sistema; le pide la información al Módulo 3)
**Prioridad global**: P1

## Contexto

Este caso de uso **obtiene** el reporte de cumplimiento de una persona desde el Módulo 3. No lo produce ni lo muestra en pantalla: lo consulta para saber si esa persona está sancionada y, si lo está, por qué y hasta cuándo.

Existe por una razón muy concreta: cuando alguien intenta reservar y no puede, el sistema tiene que poder decirle exactamente qué se lo impide. Si el motivo es una sanción, esa información no vive en el Módulo 2 —aquí no se sanciona a nadie— sino en el Módulo 3, que es el que lleva la matriz de cumplimiento. Por eso hay que ir a buscarla.

Con eso se cierra el círculo del módulo: el Módulo 2 le reporta al Módulo 3 las ausencias, las devoluciones y el cierre de cada reserva; el Módulo 3 con eso arma el cumplimiento y decide las sanciones; y el Módulo 2 vuelve a leerlas aquí para poder explicárselas a la persona en el momento en que intenta reservar.

**Ninguna persona ejecuta este caso de uso.** Ni el Estudiante, ni el Monitor, ni la Dirección de Programa entran a consultar reportes: lo hace el sistema, por dentro, y lo único que la persona llega a ver es el motivo por el que no pudo reservar.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Módulo 3 | Secundario | Provee el reporte de cumplimiento: sanciones vigentes, ausencias y devoluciones con retraso. |
| Estudiante / Monitor | Indirectos | No lo ejecutan; solo reciben, traducido a un mensaje claro, el motivo por el que no pueden reservar. |

**Casos de uso relacionados**

- `Reservar recursos` — usa lo que se obtiene aquí para denegar con el error `RES-003 — Sanción activa` e informar la fecha en que termina; ver [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md)
- `Consultar recursos` — puede avisarle a la persona que está sancionada antes de que intente apartar algo; ver [spec-modulo2-uc1-consultar-recursos.md](./spec-modulo2-uc1-consultar-recursos.md)
- `Reportar no asistencia` — el camino de ida: las ausencias que este módulo reporta son parte de lo que después se lee aquí; ver [spec-modulo2-uc9-reportar-no-asistencia.md](./spec-modulo2-uc9-reportar-no-asistencia.md)
- `Reportar fecha y hora de entrega` — igual con las devoluciones a tiempo y con retraso; ver [spec-modulo2-uc10-reportar-fecha-hora-entrega.md](./spec-modulo2-uc10-reportar-fecha-hora-entrega.md)
- `Notificar estado de recursos al finalizar reserva` — completa el camino de ida contándole al Módulo 3 cómo terminó cada reserva; ver [spec-modulo2-uc5-notificar-estado-recursos.md](./spec-modulo2-uc5-notificar-estado-recursos.md)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Saber si una persona está sancionada y por qué (Priority: P1)

Como sistema, quiero obtener del Módulo 3 el reporte de cumplimiento de una persona antes de dejarla reservar, para poder decirle con exactitud que tiene una sanción vigente y hasta cuándo dura, en vez de negarle la reserva sin explicación.

**Why this priority**: Es P1 porque `Reservar recursos` no puede cumplir su promesa sin esto. Una de sus tres denegaciones, `RES-003 — Sanción activa`, depende enteramente de la información que se obtiene aquí, y el diccionario de errores es justamente lo que evita que la persona quede sin saber qué le pasó.

**Independent Test**: Se puede probar sola, con un simulador del Módulo 3 que devuelva distintas situaciones —sin sanción, con sanción vigente, con sanción vencida, sin responder— y comprobando que el sistema interpreta cada caso correctamente. No necesita que existan reservas reales.

**Acceptance Scenarios**:

1. **Scenario**: La persona está al día
   - **Given** un Estudiante sin sanciones registradas en el Módulo 3
   - **When** el sistema consulta su reporte de cumplimiento
   - **Then** obtiene que no tiene ninguna sanción vigente y la reserva puede continuar

2. **Scenario**: La persona tiene una sanción vigente
   - **Given** un Estudiante con una sanción registrada en el Módulo 3 hasta el 2026-09-15
   - **When** el sistema consulta su reporte de cumplimiento
   - **Then** obtiene el motivo de la sanción y su fecha de finalización, y con eso `Reservar recursos` deniega con `RES-003` explicando ambas cosas

3. **Scenario**: La sanción ya venció
   - **Given** un Estudiante cuya sanción terminó el 2026-08-30
   - **When** el sistema consulta su reporte el 2026-09-03
   - **Then** obtiene que no tiene sanciones vigentes y la reserva puede continuar, sin que nadie tenga que levantarle la sanción a mano

4. **Scenario**: El Módulo 3 no responde
   - **Given** el Módulo 3 está caído temporalmente
   - **When** el sistema intenta consultar el reporte de una persona
   - **Then** no da por sentado que la persona está al día: informa que no se pudo comprobar su situación y la reserva no se confirma [NEEDS CLARIFICATION: confirmar con el equipo si ante una caída del Módulo 3 se bloquea la reserva o se permite y se revisa después]

5. **Scenario**: Persona sin historial
   - **Given** un Estudiante que nunca ha reservado nada
   - **When** el sistema consulta su reporte
   - **Then** obtiene un reporte vacío, que se interpreta como "sin sanciones", no como un error

### Edge Cases

- **La sanción vence en mitad de la franja pedida**: hay que definir si cuenta la situación al momento de reservar o al momento en que empieza la franja. El criterio debe ser el mismo siempre.
- **Sanción que aparece justo después de consultar**: entre la consulta y la confirmación pueden pasar segundos; por eso la comprobación se hace en el momento de confirmar, no al abrir la pantalla.
- **Respuesta del Módulo 3 incompleta**: si llega una sanción sin fecha de finalización, el sistema no puede inventarla; debe tratarla como vigente y dejar constancia de que faltó el dato.
- **Datos de otras personas**: la consulta pide el reporte de una sola persona y nunca debe exponer el historial de nadie más.
- **Sanciones que no aplican a lo que se está pidiendo**: según [gestionunimag.md](../gestionunimag.md), hay castigos que bloquean solo la reserva de espacios. Un bloqueo de espacios no debería impedir el préstamo de un equipo. [NEEDS CLARIFICATION: confirmar si las sanciones distinguen entre espacios y equipos]

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE obtener del Módulo 3 el reporte de cumplimiento de una persona: sanciones vigentes, ausencias y devoluciones con retraso.
- **FR-002**: El sistema DEBE ejecutar esta consulta por sí solo, sin que ninguna persona tenga que solicitarla, y NO DEBE ofrecerla como una pantalla de consulta.
- **FR-003**: El sistema DEBE consultar el reporte antes de confirmar una reserva, para poder aplicar la denegación `RES-003 — Sanción activa`, y también antes de aceptar la **renovación** de un préstamo, que `Reservar recursos` FR-017 deniega a quien tenga una sanción vigente.
- **FR-004**: Cuando exista una sanción vigente, el sistema DEBE obtener su motivo y su fecha de finalización, y trasladárselos a la persona en el mensaje de denegación.
- **FR-005**: El sistema NO DEBE calcular, decidir ni almacenar sanciones por su cuenta: la única fuente válida es el Módulo 3.
- **FR-006**: El sistema NO DEBE dar por buena una situación que no pudo comprobar; si el Módulo 3 no responde, DEBE informarlo en vez de asumir que la persona está al día.
- **FR-007**: La consulta DEBE pedir el reporte de una sola persona y NO DEBE exponer información de terceros.
- **FR-008**: Un reporte vacío DEBE interpretarse como "sin sanciones", no como un fallo.
- **FR-009**: El sistema DEBE dejar registro de cada consulta realizada y de su resultado, para poder auditar por qué se denegó una reserva.
- **FR-010**: La consulta NO DEBE modificar nada en el Módulo 3: solo lee.

### Key Entities

- **ReporteDeCumplimiento**: lo que devuelve el Módulo 3 sobre una persona. Atributos: persona, sanciones vigentes, ausencias acumuladas, devoluciones con retraso, fecha y hora de la consulta.
- **Sanción**: restricción vigente sobre una persona. Atributos: motivo, fecha de inicio, fecha de finalización, alcance.
- **Usuario**: la persona por la que se pregunta.
- **Denegación**: el rechazo que se produce cuando el reporte indica sanción vigente; guarda la causa consultada.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las denegaciones por sanción indican el motivo y la fecha en que la sanción termina.
- **SC-002**: La consulta al Módulo 3 responde en menos de 2 segundos, sin que la persona note demora al reservar.
- **SC-003**: Cero reservas confirmadas a personas con sanción vigente.
- **SC-004**: Cero casos en que el sistema dé por buena la situación de una persona sin haberla podido comprobar.
- **SC-005**: Cero consultas que devuelvan información de una persona distinta de la solicitada.
