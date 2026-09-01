# Feature Specification: Cancelar reserva

**Created**: 2026-08-24
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Cancelar reserva` (continuación opcional: solo ocurre si hay una reserva y alguien decide deshacerla)
**Prioridad global**: P2

## Contexto

Cierra el ciclo de vida de la reserva. Permite al Estudiante liberar a tiempo un recurso que ya no va a usar, y recoge también la cancelación automática que dispara la jerarquía académica cuando una necesidad institucional extraordinaria desplaza una reserva estudiantil.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Estudiante | Primario (humano) | Cancela sus propias reservas. |
| Monitor | Primario (humano) | Especialización de Estudiante: hereda esta capacidad. |
| Módulo 1 | Secundario (sistema) | Recibe el evento de cancelación. |

**Casos de uso relacionados**

- `Reservar recursos` — crea las reservas que aquí se cancelan; ver [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md)
- `Importar horarios semestrales` — origen de la cancelación automática por prioridad; ver [spec-modulo2-uc3-importar-horarios-semestrales.md](./spec-modulo2-uc3-importar-horarios-semestrales.md)
- `Notificar estado de recursos` — paso que ocurre siempre: toda cancelación se avisa sin que nadie tenga que pedirlo; ver [spec-modulo2-uc5-notificar-estado-recursos.md](./spec-modulo2-uc5-notificar-estado-recursos.md)

**Diccionario de errores**

| Código | Causa |
|---|---|
| `CAN-001` | No autorizado sobre esta reserva (no es el titular). |
| `CAN-002` | La reserva ya no es cancelable (ya inició o finalizó). |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cancelar reserva (Priority: P2)

Como Estudiante, quiero cancelar una reserva que ya no voy a usar, para liberar el recurso a tiempo para otros compañeros y no acumular ausencias que deriven en una sanción.

**Why this priority**: Cierra el ciclo de vida de la reserva y es la principal fuente de liberación de disponibilidad. Sin esta historia el sistema funciona, pero la ocupación real se degrada por reservas fantasma; por eso va inmediatamente después del núcleo consultar/reservar.

**Independent Test**: Se puede probar de forma independiente creando una reserva confirmada, cancelándola y verificando que el recurso vuelve a aparecer como `DISPONIBLE` en la consulta de esa misma franja y que el contador de préstamos vigentes del estudiante disminuye.

**Acceptance Scenarios**:

1. **Scenario**: Cancelación por el titular
   - **Given** el Estudiante tiene una reserva `CONFIRMADA` que aún no ha iniciado
   - **When** solicita cancelarla
   - **Then** el sistema cambia la reserva a `CANCELADA`, libera la franja del recurso y descuenta el préstamo de su cupo vigente

2. **Scenario**: Cancelación no permitida sobre reserva ajena
   - **Given** una reserva pertenece a otro estudiante
   - **When** un estudiante distinto intenta cancelarla
   - **Then** el sistema rechaza la operación con `CAN-001 — No autorizado sobre esta reserva` y la reserva permanece `CONFIRMADA`

3. **Scenario**: Cancelación fuera de plazo
   - **Given** la reserva ya inició o finalizó
   - **When** el Estudiante intenta cancelarla
   - **Then** el sistema rechaza la operación con `CAN-002 — La reserva ya no es cancelable` e indica el plazo mínimo de antelación exigido

4. **Scenario**: Cancelación automática por prioridad académica
   - **Given** una reserva estudiantil es desplazada por una necesidad institucional extraordinaria
   - **When** el sistema ejecuta la cancelación automática
   - **Then** la reserva queda `CANCELADA_POR_PRIORIDAD_ACADEMICA`, no se contabiliza como ausencia ni penaliza al estudiante, y se emite el evento correspondiente

### Edge Cases

- **Cancelación en el límite del plazo**: solicitud que llega exactamente en el instante de la antelación mínima; el criterio de borde debe ser explícito y determinista.
- **Recurso dado de baja**: cuando un recurso pasa a `FUERA_DE_SERVICIO`, sus reservas futuras deben cancelarse con motivo propio y notificarse, sin penalizar a los titulares.
- **Doble cancelación**: una segunda solicitud sobre una reserva ya `CANCELADA` debe ser idempotente y no liberar dos veces el cupo de préstamos.
- **No presentación**: si pasan 10 minutos desde la hora de inicio y la persona no llegó a usar el recurso, este se libera solo y se le aplica una sanción, tal como se define en `Reservar recursos`. Esa liberación no cuenta como una cancelación hecha por el estudiante. [NEEDS CLARIFICATION: cuánto dura la sanción y cuántas ausencias hacen falta para aplicarla]

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Los usuarios DEBEN poder cancelar sus propias reservas no iniciadas, respetando la antelación mínima configurada.
- **FR-002**: El sistema DEBE liberar la franja del recurso y actualizar el cupo de préstamos vigentes al cancelar.
- **FR-003**: El sistema DEBE impedir que un usuario cancele reservas de las que no es titular (`CAN-001`) y que cancele reservas ya iniciadas o finalizadas (`CAN-002`).
- **FR-004**: El sistema DEBE soportar la cancelación automática por prioridad académica, marcando la reserva con `CANCELADA_POR_PRIORIDAD_ACADEMICA`.
- **FR-005**: Las cancelaciones por prioridad académica NO DEBEN penalizar al estudiante ni computar como ausencia.
- **FR-006**: La cancelación DEBE ser idempotente: repetirla sobre una reserva ya cancelada no altera el estado ni el cupo.
- **FR-007**: El sistema DEBE mantener registro de auditoría de toda cancelación, con autor, motivo y marca de tiempo.
- **FR-008**: La antelación mínima de cancelación DEBE ser de 5 minutos.

### Key Entities

- **Reserva**: apartado cuyo estado transita a `CANCELADA` o `CANCELADA_POR_PRIORIDAD_ACADEMICA`; conserva el motivo de cancelación.
- **Recurso**: espacio o equipo cuya franja se libera con la cancelación.
- **FranjaHoraria**: intervalo liberado, que vuelve a ser consultable como disponible.
- **Usuario**: titular de la reserva; su cupo de préstamos vigentes se actualiza al cancelar.
- **Sanción**: restricción que las ausencias acumuladas pueden originar y que las cancelaciones a tiempo evitan.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una franja cancelada vuelve a aparecer como `DISPONIBLE` en la consulta en menos de 5 segundos.
- **SC-002**: El 100 % de los intentos de cancelación sobre reservas ajenas o ya iniciadas son rechazados con su código específico.
- **SC-003**: Cero estudiantes penalizados por cancelaciones originadas en prioridad académica.
- **SC-004**: Reducción del 40 % en las reservas que nadie llegó a usar, tras un semestre de operación.
