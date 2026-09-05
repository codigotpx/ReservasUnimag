# Feature Specification: Actualizar estado de los recursos

**Created**: 2026-09-03
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Actualizar estado de los recursos` (paso que ocurre siempre por dentro de `Reservar recursos` y de `Cancelar reserva`)
**Prioridad global**: P1

## Contexto

Es el paso que mantiene el inventario diciendo la verdad. Cada vez que algo le pasa a una reserva —nace, se cancela, empieza, termina— el recurso involucrado tiene que cambiar de estado, y ese cambio se le informa al Módulo 1, que es el dueño del inventario físico de la universidad.

Nadie lo pide por separado: ocurre por dentro de `Reservar recursos` y de `Cancelar reserva`, y ninguno de los dos puede saltárselo. Si este paso falla, el resto del sistema empieza a mostrar salones libres que en realidad están ocupados, que es exactamente el problema que el proyecto quiere resolver.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Módulo 1 | Secundario | Recibe el estado actualizado de cada recurso; es el dueño del inventario. |
| Estudiante / Monitor | Indirectos | No lo ejecutan; se benefician de que la información que ven esté al día. |

**Casos de uso relacionados**

- `Reservar recursos` — lo ejecuta siempre al confirmar una reserva; ver [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md)
- `Cancelar reserva` — lo ejecuta siempre al liberar una franja; ver [spec-modulo2-uc4-cancelar-reserva.md](./spec-modulo2-uc4-cancelar-reserva.md)
- `Importar horarios semestrales` — marca los espacios con bloqueo académico; ver [spec-modulo2-uc3-importar-horarios-semestrales.md](./spec-modulo2-uc3-importar-horarios-semestrales.md)
- `Consultar disponibilidad de los recursos` — lee lo que este caso de uso deja escrito; ver [spec-modulo2-uc8-consultar-disponibilidad-recursos.md](./spec-modulo2-uc8-consultar-disponibilidad-recursos.md)

**Estados del recurso**

Conforme a [gestionunimag.md](../gestionunimag.md), el inventario maneja cinco estados y este caso de uso solo puede mover un recurso entre ellos:

| Estado | Qué significa |
|---|---|
| `DISPONIBLE` | Libre, se puede apartar. |
| `RESERVADO` | Apartado por alguien, pero todavía nadie lo está usando. |
| `BLOQUEO_ACADEMICO` | Guardado para una clase del semestre. |
| `EN_USO` | La persona llegó y lo está usando ahora mismo. |
| `EN_MANTENIMIENTO` | En reparación o fuera de servicio. |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Mantener al día el estado de cada recurso (Priority: P1)

Como sistema, quiero cambiar el estado de un recurso cada vez que su situación cambia y avisarle al Módulo 1, para que cualquiera que consulte —un estudiante, un monitor o la Dirección de Programa— vea siempre la realidad y no una foto vieja.

**Why this priority**: Es P1 porque va pegado a `Reservar recursos`, que también es P1. Una reserva que se confirma pero no cambia el estado del recurso es peor que no tener reservas: dos personas pueden apartar el mismo salón creyendo ambas que está libre.

**Independent Test**: Se puede probar solo, sin necesidad de reportes ni notificaciones: se provoca cada situación (confirmar, empezar a usar, cancelar, terminar la franja) y se comprueba que el recurso quedó en el estado correcto y que el Módulo 1 recibió el aviso.

**Acceptance Scenarios**:

1. **Scenario**: Una reserva se confirma
   - **Given** la "Sala de Estudio 3" está `DISPONIBLE` el 2026-09-01 de 10:00 a 12:00
   - **When** un Estudiante confirma una reserva sobre esa franja
   - **Then** el recurso queda `RESERVADO` únicamente en esa franja, sigue `DISPONIBLE` en las demás, y el Módulo 1 recibe el cambio

2. **Scenario**: La persona llega y empieza a usar el recurso
   - **Given** la "Sala de Estudio 3" está `RESERVADO` para las 10:00
   - **When** llega la hora y queda registrado que la persona se presentó
   - **Then** el recurso pasa a `EN_USO` y el Módulo 1 recibe el cambio

3. **Scenario**: La reserva se cancela
   - **Given** el "Auditorio Menor" está `RESERVADO` el 2026-09-05 de 14:00 a 16:00
   - **When** el titular cancela esa reserva
   - **Then** el recurso vuelve a `DISPONIBLE` en esa franja y el Módulo 1 recibe el cambio

4. **Scenario**: Termina la franja reservada
   - **Given** el "Laboratorio de Redes" está `EN_USO` hasta las 16:00
   - **When** llegan las 16:00 y no hay otra reserva encima
   - **Then** el recurso vuelve a `DISPONIBLE` sin que nadie tenga que hacer nada

5. **Scenario**: Se devuelve un objeto prestado
   - **Given** el "Libro de Cálculo I" está `EN_USO` desde que se prestó, y su plazo venció ayer sin que nadie lo devolviera
   - **When** la persona lo devuelve hoy y queda registrada la devolución
   - **Then** el recurso vuelve a `DISPONIBLE` en ese momento y el Módulo 1 recibe el cambio; antes de esa devolución, y aunque el plazo estuviera vencido, el recurso nunca dejó de estar `EN_USO`

6. **Scenario**: Entra una clase del semestre
   - **Given** se importó el horario y el "Salón 201" tiene clase los martes de 08:00 a 10:00
   - **When** se aplica esa carga
   - **Then** el recurso queda en `BLOQUEO_ACADEMICO` en esas franjas y el Módulo 1 recibe el cambio

### Edge Cases

- **Recurso en mantenimiento**: si un recurso está `EN_MANTENIMIENTO`, ningún cambio derivado de una reserva puede sacarlo de ese estado; el mantenimiento manda sobre todo lo demás.
- **Dos cambios sobre la misma franja casi al mismo tiempo**: debe quedar un único estado final coherente, nunca un recurso que aparezca `DISPONIBLE` y `RESERVADO` a la vez.
- **El Módulo 1 no responde**: la reserva o la cancelación se completan igual; el aviso queda pendiente y se vuelve a intentar hasta que llegue, sin dejar al inventario desactualizado en silencio.
- **Aviso repetido**: si un mismo cambio se reintenta, el recurso no debe terminar contado dos veces ni cambiar de estado dos veces.
- **Franja que ya pasó**: un cambio que llega tarde, referido a una franja que ya terminó, no debe reabrir ni volver a ocupar el recurso; se registra y se descarta.
- **Préstamo vencido y no devuelto**: que se cumpla la fecha de vencimiento no dispara ningún cambio de estado. El objeto sigue `EN_USO` y el inventario lo refleja así; quien calcula la mora es el Módulo 3, y el único hecho que devuelve el recurso a `DISPONIBLE` es la devolución registrada.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE cambiar el estado del recurso cada vez que una reserva se confirma, se cancela, empieza a usarse o termina.
- **FR-002**: El cambio de estado DEBE afectar únicamente al tiempo involucrado, sin alterar la disponibilidad del recurso fuera de él: la franja horaria cuando se trata de un espacio, y el periodo de préstamo completo cuando se trata de un objeto, que queda ocupado de principio a fin sin liberarse por las noches.
- **FR-003**: El sistema DEBE usar solo los cinco estados del inventario: `DISPONIBLE`, `RESERVADO`, `BLOQUEO_ACADEMICO`, `EN_USO` y `EN_MANTENIMIENTO`.
- **FR-004**: El sistema DEBE informar al Módulo 1 de cada cambio de estado, indicando el recurso, la franja, el estado nuevo y el motivo del cambio.
- **FR-005**: El sistema NO DEBE sacar de `EN_MANTENIMIENTO` a un recurso por efecto de una reserva o una cancelación.
- **FR-006**: Un recurso NO DEBE poder quedar en dos estados distintos para el mismo momento, ni por franja ni dentro de un periodo de préstamo.
- **FR-007**: Si el Módulo 1 no está disponible, la operación de negocio DEBE completarse igualmente y el aviso DEBE reintentarse hasta entregarse.
- **FR-008**: Repetir el mismo aviso NO DEBE producir un segundo cambio de estado.
- **FR-009**: El sistema DEBE guardar un registro de cada cambio de estado con el recurso, la franja, el estado anterior, el estado nuevo, el motivo y la fecha y hora.
- **FR-010**: Al terminar la franja, un **espacio** DEBE volver a `DISPONIBLE` por sí solo, salvo que exista otra reserva o un bloqueo académico encima.
- **FR-011**: Un **objeto** prestado NO DEBE volver a `DISPONIBLE` por el paso del tiempo. Sigue `EN_USO` hasta que se registre su devolución mediante `Reportar fecha y hora de entrega`, incluso después de vencido el plazo: mientras el recurso no vuelva físicamente, el inventario tiene que seguir diciendo que está fuera. Es la diferencia de fondo con un espacio, que se desocupa solo cuando pasa la hora.

### Key Entities

- **Recurso**: espacio o equipo del inventario; lo que cambia de estado.
- **FranjaHoraria**: día con hora de inicio y hora de fin; el estado de un **espacio** se guarda por franja, no para el recurso entero.
- **PeriodoDePrestamo**: el tramo continuo en que un **objeto** está prestado. Su estado no se guarda por franjas: el objeto queda `EN_USO` de corrido durante todo el periodo y solo cambia cuando se registra la devolución.
- **EstadoDelRecurso**: situación del recurso en una franja concreta, siempre uno de los cinco valores del inventario.
- **CambioDeEstado**: registro de un cambio ocurrido. Atributos: recurso, franja, estado anterior, estado nuevo, motivo, fecha y hora, resultado del aviso al Módulo 1.
- **Reserva**: origen de la mayoría de los cambios de estado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El estado que muestra el sistema coincide con la situación real del recurso en el 100 % de las franjas revisadas durante una auditoría.
- **SC-002**: Un recurso liberado vuelve a aparecer como disponible en menos de 5 segundos.
- **SC-003**: Cero recursos con dos estados distintos para la misma franja bajo pruebas de uso simultáneo.
- **SC-004**: Cero avisos perdidos hacia el Módulo 1 ante una caída de hasta 30 minutos.
- **SC-005**: Ninguna reserva ni cancelación falla por culpa de un error al avisar al Módulo 1.
