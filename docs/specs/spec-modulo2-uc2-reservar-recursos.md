# Feature Specification: Reservar recursos

**Created**: 2026-08-24
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Reservar recursos` (continuación opcional de `Consultar recursos`)
**Prioridad global**: P1

## Contexto

Es lo que le da sentido al módulo: aquí el estudiante aparta el recurso que encontró libre. Llega desde la lista de `Consultar recursos`, aunque puede quedarse solo consultando y no reservar nada; por eso esta es una continuación opcional y no un paso obligado.

Al recibir la solicitud, el sistema revisa las reglas de la universidad y decide si la reserva se confirma o se rechaza. Cuando la rechaza, nunca dice solo "no se pudo": siempre entrega el motivo exacto, tomado del diccionario de errores de abajo.

### Glosario del documento

| Palabra | Qué significa aquí |
|---|---|
| **Reserva vigente** | Reserva ya confirmada que todavía no ha terminado. Son las que cuentan para el límite de 3. |
| **Franja horaria** | Un día con hora de inicio y hora de fin. Por ejemplo: 1 de septiembre, de 10:00 a 12:00. |
| **Cruce de horarios** | Dos apartados sobre el mismo recurso que comparten aunque sea un minuto. Basta ese cruce para que haya conflicto. |
| **Sanción** | Castigo temporal que impide reservar. Se aplica, por ejemplo, cuando alguien no se presenta a usar lo que apartó. |
| **No presentación** | La persona reservó pero no llegó a usar el recurso. |
| **Dos solicitudes al mismo tiempo** | Dos estudiantes pidiendo el mismo salón y la misma franja casi en el mismo instante. El sistema debe dejar pasar solo a uno. |

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Estudiante | Primario (humano) | Solicita el apartado de un recurso para una franja horaria. |
| Monitor | Primario (humano) | Especialización de Estudiante: hereda esta capacidad. |
| Módulo 1 | Secundario (sistema) | Recibe el evento de confirmación de la reserva. |

**Casos de uso relacionados**

- `Consultar recursos` — es el punto de partida: el estudiante llega aquí desde la lista de recursos libres, aunque puede quedarse solo consultando; ver [spec-modulo2-uc1-consultar-recursos.md](./spec-modulo2-uc1-consultar-recursos.md)
- `Notificar estado de recursos` — paso que ocurre siempre: cada reserva confirmada o rechazada se avisa sin que nadie tenga que pedirlo; ver [spec-modulo2-uc5-notificar-estado-recursos.md](./spec-modulo2-uc5-notificar-estado-recursos.md)
- `Importar horarios semestrales` — origen de las denegaciones `RES-001`; ver [spec-modulo2-uc3-importar-horarios-semestrales.md](./spec-modulo2-uc3-importar-horarios-semestrales.md)

**Diccionario de errores**

| Código | Causa |
|---|---|
| `RES-001` | Conflicto académico: el recurso está reservado para actividad docente. |
| `RES-002` | Ya tiene 3 reservas vigentes, que es el máximo permitido. |
| `RES-003` | Sanción activa sobre el usuario. |
| `RES-004` | El recurso acaba de ser tomado: otra persona alcanzó a reservarlo primero. |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reservar recursos con validación de reglas (Priority: P1)

Como Estudiante, quiero apartar un recurso disponible para una fecha y una hora concretas, y que el sistema me diga con claridad cuándo no puedo hacerlo, para saber exactamente qué regla estoy incumpliendo: que ahí hay clase, que ya tengo mis 3 reservas o que tengo una sanción encima.

**Why this priority**: Es lo que le da valor al módulo. Junto con `Consultar recursos` forma la primera versión que ya se puede mostrar funcionando: consultar y apartar. El diccionario de errores es parte inseparable de esta historia, porque un rechazo sin explicación obliga al estudiante a insistir o a ir a preguntar en persona.

**Independent Test**: Se puede probar sola. Se hace una reserva sobre un recurso libre y se verifica que quedó guardada; luego se provoca a propósito cada una de las tres causas de rechazo y se verifica que el sistema devuelve el error que corresponde. No hace falta que existan los reportes ni los avisos.

**Acceptance Scenarios**:

1. **Scenario**: Reserva exitosa
   - **Given** el Estudiante no tiene sanciones activas, tiene 1 de las 3 reservas vigentes permitidas y la "Sala de Estudio 3" está `DISPONIBLE` el 2026-09-01 de 10:00 a 12:00
   - **When** solicita la reserva de ese recurso en esa franja
   - **Then** el sistema crea la reserva en estado `CONFIRMADA`, marca el recurso como `EN_USO` para esa franja y devuelve el identificador de la reserva

2. **Scenario**: Denegación por conflicto académico (RES-001)
   - **Given** el "Laboratorio de Redes" quedó marcado como `BLOQUEO_ACADEMICO` el 2026-09-01 de 08:00 a 10:00
   - **When** el Estudiante intenta reservarlo en esa franja
   - **Then** el sistema rechaza la solicitud con el error `RES-001 — Conflicto académico: el recurso está reservado para actividad docente` y no crea ningún registro

3. **Scenario**: Denegación por límite de reservas (RES-002)
   - **Given** el Estudiante ya tiene 3 reservas vigentes, que es el máximo permitido
   - **When** intenta crear una cuarta
   - **Then** el sistema rechaza la solicitud con el error `RES-002 — Límite máximo de reservas alcanzado` e informa cuáles son sus 3 reservas vigentes y cuándo termina la primera que se libera

4. **Scenario**: Denegación por sanción activa (RES-003)
   - **Given** el Estudiante tiene una sanción vigente hasta el 2026-09-15
   - **When** intenta reservar cualquier recurso
   - **Then** el sistema rechaza la solicitud con el error `RES-003 — Sanción activa` e informa la fecha de finalización de la sanción

5. **Scenario**: Dos estudiantes piden el mismo recurso al mismo tiempo
   - **Given** dos estudiantes solicitan casi en el mismo instante la misma franja del mismo recurso libre
   - **When** el sistema atiende ambas solicitudes
   - **Then** solo la primera queda `CONFIRMADA` y la segunda recibe `RES-004 — El recurso acaba de ser tomado`; nunca pueden quedar dos reservas cruzadas sobre el mismo recurso

### Edge Cases

- **Cruce parcial de horarios**: una solicitud de 09:30 a 10:30 sobre un recurso con clase de 08:00 a 10:00 se cruza con ella durante media hora. Se rechaza con `RES-001`: basta un minuto en común.
- **La última franja libre**: si dos personas la piden a la vez, el sistema debe dejar pasar solo a una. Nunca pueden quedar dos reservas confirmadas sobre el mismo recurso y la misma franja.
- **Sanción que empieza cuando ya hay reservas hechas**: además de impedirle reservar de nuevo, se le cancelan las reservas que ya tenía.
- **Varias causas de rechazo a la vez**: el sistema revisa en un orden fijo (primero la sanción, luego el límite de 3, luego la clase o el recurso ocupado) y devuelve un solo error, el de la primera causa que encuentra.
- **No presentación**: si pasan 10 minutos de la hora de inicio y la persona no llegó a usar el recurso, este vuelve a quedar `DISPONIBLE` para los demás y a ella se le aplica una sanción.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Los usuarios DEBEN poder crear una reserva sobre un recurso disponible indicando fecha, hora de inicio y hora de fin.
- **FR-002**: Antes de confirmar, el sistema DEBE revisar en este orden: primero si el usuario tiene una sanción activa, después si ya llegó a sus 3 reservas vigentes, y por último si el recurso tiene clase o ya está ocupado en esa franja.
- **FR-003**: Cuando rechace una reserva, el sistema DEBE decir la causa con el código y el mensaje que le corresponde: `RES-001` hay clase en ese recurso, `RES-002` ya tiene 3 reservas vigentes, `RES-003` tiene una sanción activa, `RES-004` otra persona alcanzó a reservarlo primero.
- **FR-004**: El sistema NO DEBE permitir dos reservas confirmadas que se crucen sobre el mismo recurso, ni siquiera cuando dos personas lo pidan al mismo tiempo.
- **FR-005**: El sistema DEBE dejar registrado cada rechazo con su código, el usuario, el recurso y la fecha y hora, para poder consultarlo después en los reportes.
- **FR-006**: El sistema DEBE volver a comprobar que el recurso sigue libre en el momento de confirmar, sin fiarse de lo que mostraba la consulta anterior.
- **FR-007**: El sistema DEBE guardar constancia de cada reserva creada, con quién la hizo y en qué fecha y hora.
- **FR-008**: El límite máximo de reservas vigentes por usuario DEBE ser 3, tanto para el Estudiante como para el Monitor. Ese número DEBE poder cambiarse desde la administración, sin modificar el sistema.
- **FR-009**: La duración máxima de una reserva DEBE poder configurarse desde la administración. [NEEDS CLARIFICATION: valor no definido]
- **FR-010**: Si pasan 10 minutos desde la hora de inicio y la persona no se presenta, el sistema DEBE liberar el recurso, dejarlo `DISPONIBLE` para los demás y aplicar una sanción al titular de la reserva. [NEEDS CLARIFICATION: cuánto dura esa sanción y cuántas ausencias hacen falta para aplicarla]

### Key Entities

- **Reserva**: apartado de un Recurso por un Usuario en una FranjaHoraria. Atributos: identificador, titular, recurso, franja, estado (`CONFIRMADA`, `CANCELADA`, `CANCELADA_POR_PRIORIDAD_ACADEMICA`, `FINALIZADA`), origen (estudiantil o académico).
- **Recurso**: espacio o equipo reservable, con su estado operativo por franja.
- **FranjaHoraria**: fecha con hora de inicio y hora de fin; es lo que se compara para saber si dos reservas se cruzan.
- **Usuario**: persona con un rol (Estudiante, Monitor); tiene sus reservas vigentes y cuántas de las 3 permitidas lleva usadas.
- **Sanción**: restricción temporal que impide reservar. Atributos: motivo, fecha de inicio, fecha de fin, estado.
- **Denegación**: registro de un intento de reserva rechazado, con el código del diccionario de errores, el usuario, el recurso y la fecha y hora.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un estudiante consulta y reserva un recurso en menos de 2 minutos desde que entra al sistema.
- **SC-002**: El 100 % de los intentos de reserva sobre franjas con clase son rechazados: cero reservas estudiantiles cruzadas con clases en un semestre completo.
- **SC-003**: El 100 % de los rechazos llegan con un código y un mensaje que dice la causa exacta; ningún rechazo genérico.
- **SC-004**: Cero reservas repetidas sobre el mismo recurso y la misma franja en las pruebas donde varias personas reservan a la vez.
- **SC-005**: Reducción del 50 % en las solicitudes manuales de apartado de espacios gestionadas por la Dirección de Programa durante el primer semestre de operación.
