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
| Estudiante | Primario | Cancela sus propias reservas. |
| Monitor | Primario | Especialización de Estudiante: hereda esta capacidad. |
| Módulo 1 | Secundario | Recibe el estado actualizado del recurso cuando la franja se libera. |
| Módulo 3 | Secundario | Recibe el aviso de cómo terminó la reserva. |

**Casos de uso relacionados**

- `Reservar recursos` — crea las reservas que aquí se cancelan; ver [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md)
- `Importar horarios semestrales` — origen de la cancelación automática por prioridad; ver [spec-modulo2-uc3-importar-horarios-semestrales.md](./spec-modulo2-uc3-importar-horarios-semestrales.md)
- `Actualizar estado de los recursos` — **paso que ocurre siempre por dentro**: al cancelar, la franja se libera y el Módulo 1 se entera; no se puede saltar; ver [spec-modulo2-uc7-actualizar-estado-recursos.md](./spec-modulo2-uc7-actualizar-estado-recursos.md)
- `Reportar cancelación de reserva` — le cuenta al Módulo 3 que la reserva se cerró por cancelación y no por incumplimiento; ver [spec-modulo2-uc11-reportar-cancelacion-reserva.md](./spec-modulo2-uc11-reportar-cancelacion-reserva.md)

**Diccionario de errores**

| Código | Causa |
|---|---|
| `CAN-001` | La reserva ya no es cancelable, por estar fuera de plazo. |

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

2. **Scenario**: Cancelación de un objeto que aún no se ha recogido
   - **Given** el Estudiante apartó el "Videobeam 12" para recogerlo el 2026-09-03 a las 14:30 y todavía no ha ido por él
   - **When** solicita cancelar ese préstamo
   - **Then** el sistema lo deja `CANCELADA`, libera el periodo completo —desde esa recogida hasta el vencimiento que tenía previsto—, descuenta el préstamo de su cupo y el videobeam vuelve a aparecer disponible para otros desde ese mismo momento

3. **Scenario**: Cancelación fuera de plazo
   - **Given** se venció el plazo de cancelación (10 minutos), la reserva ya inició o finalizó
   - **When** el Estudiante intenta cancelarla
   - **Then** el sistema rechaza la operación con `CAN-001 — La reserva ya no es cancelable` e indica el plazo mínimo de antelación exigido  

4. **Scenario**: Cancelación automática por prioridad académica
   - **Given** una reserva estudiantil es desplazada por una necesidad institucional extraordinaria
   - **When** el sistema ejecuta la cancelación automática
   - **Then** la reserva queda `CANCELADA_POR_PRIORIDAD_ACADEMICA`, no se contabiliza como ausencia del estudiante, y se emite el evento correspondiente

### Edge Cases

- **Cancelación en el límite del plazo**: solicitud que llega exactamente en el instante de la antelación mínima; el criterio de borde debe ser explícito y determinista.
- **Recurso dado de baja**: cuando un recurso pasa a `EN_MANTENIMIENTO`, sus reservas futuras deben cancelarse con motivo propio y notificarse, sin penalizar a los titulares.
- **Doble cancelación**: una segunda solicitud sobre una reserva ya `CANCELADA` debe ser idempotente y no liberar dos veces el cupo de préstamos.
- **No presentación**: pasados 10 minutos sin que la persona llegue —contados desde el inicio de la franja si es un espacio, o desde la hora de recogida si es un objeto—, el Módulo 3 puede reportar la ausencia, y con ese reporte el recurso se libera y queda la constancia, tal como se define en `Reportar no asistencia` y en `Reservar recursos` FR-010. El sistema no libera nada por su cuenta, y esa liberación no cuenta como una cancelación hecha por el estudiante.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Los usuarios DEBEN poder cancelar sus propios apartados no iniciados, respetando la antelación mínima configurada. Un apartado está sin iniciar si es un **espacio** cuya franja aún no ha empezado, o un **objeto** que la persona todavía no ha recogido; en los objetos la antelación de FR-007 se mide contra la hora de recogida acordada.
- **FR-002**: El sistema DEBE liberar el tiempo que el recurso tenía ocupado y actualizar el cupo de préstamos vigentes al cancelar, ejecutando siempre `Actualizar estado de los recursos`. En un espacio eso es la franja reservada; en un objeto es el periodo de préstamo entero, desde la recogida prevista hasta su vencimiento, no un trozo.
- **FR-003**: El sistema DEBE impedir que un usuario cancele reservas fuera del plazo (`CAN-001`).
- **FR-004**: El sistema DEBE soportar la cancelación automática por prioridad académica, marcando la reserva con `CANCELADA_POR_PRIORIDAD_ACADEMICA`.
- **FR-005**: Las cancelaciones por prioridad académica NO DEBEN penalizar al estudiante ni computar como ausencia.
- **FR-006**: El sistema DEBE mantener registro de auditoría de toda cancelación, con autor, motivo y marca de tiempo.
- **FR-007**: La antelación mínima de cancelación DEBE ser de 10 minutos.
- **FR-008**: El sistema NO DEBE permitir cancelar el préstamo de un **objeto que ya fue entregado**: una vez el recurso está en manos de la persona, lo que corresponde es devolverlo mediante `Reportar fecha y hora de entrega`. Al intentarlo, el sistema DEBE explicarlo y ofrecer el registro de devolución en su lugar.

### Key Entities

- **Reserva**: apartado cuyo estado transita a `CANCELADA` o `CANCELADA_POR_PRIORIDAD_ACADEMICA`; conserva el motivo de cancelación.
- **Recurso**: espacio o equipo que se libera con la cancelación.
- **FranjaHoraria**: intervalo liberado cuando lo cancelado es la reserva de un espacio; vuelve a ser consultable como disponible.
- **PeriodoDePrestamo**: lo que se libera cuando lo cancelado es el préstamo de un objeto que aún no se ha recogido. Se libera el periodo completo, no una franja suelta: el objeto vuelve a estar disponible desde ese momento y hasta el vencimiento que tenía previsto. Un objeto ya entregado no se cancela, se devuelve (`Reportar fecha y hora de entrega`).
- **Usuario**: titular de la reserva; su cupo de préstamos vigentes se actualiza al cancelar.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una franja cancelada vuelve a aparecer como `DISPONIBLE` en la consulta en menos de 5 segundos.
- **SC-002**: El 100 % de los intentos de cancelación fuera de plazo sobre reservas son rechazados con su código específico.
- **SC-003**: Cero estudiantes penalizados por cancelaciones originadas en prioridad académica.
- **SC-004**: Reducción del 40 % en las reservas que nadie llegó a usar, tras un semestre de operación.
