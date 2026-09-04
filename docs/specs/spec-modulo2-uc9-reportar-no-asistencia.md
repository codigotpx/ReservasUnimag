# Feature Specification: Reportar no asistencia

**Created**: 2026-09-03
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Reportar no asistencia` (informa al Módulo 3)
**Prioridad global**: P2

## Contexto

Cuando alguien aparta un salón y no aparece, ese espacio queda muerto: nadie más pudo usarlo porque figuraba ocupado. Este caso de uso se encarga de dejar constancia de esa ausencia y de avisarle al Módulo 3, que es el que lleva la matriz de cumplimiento y aplica las sanciones.

La división de trabajo es importante: el Módulo 2 detecta y reporta, el Módulo 3 castiga. Según [gestionunimag.md](../gestionunimag.md), la consecuencia prevista para una no asistencia a un espacio es el bloqueo de reservas de espacios por una semana, pero quien la aplica es el Módulo 3, no este módulo.

La ausencia se detecta a los 10 minutos de empezada la franja sin que se haya registrado el uso, tal como se define en `Reservar recursos`.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Módulo 3 | Secundario | Recibe el reporte de ausencia y decide y aplica la sanción. |
| Estudiante / Monitor | Indirectos | Son quienes incurren en la ausencia; reciben la consecuencia por parte del Módulo 3. |

**Casos de uso relacionados**

- `Reservar recursos` — define el plazo de 10 minutos y la reserva sobre la que se mide la ausencia; ver [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md)
- `Cancelar reserva` — cancelar a tiempo es justamente la forma de evitar este reporte; ver [spec-modulo2-uc4-cancelar-reserva.md](./spec-modulo2-uc4-cancelar-reserva.md)
- `Actualizar estado de los recursos` — libera el recurso en cuanto se confirma la ausencia; ver [spec-modulo2-uc7-actualizar-estado-recursos.md](./spec-modulo2-uc7-actualizar-estado-recursos.md)
- `Reportar cancelación de reserva` — el cierre contrario: la persona sí avisó, y por eso no se le reporta ninguna ausencia; ver [spec-modulo2-uc11-reportar-cancelacion-reserva.md](./spec-modulo2-uc11-reportar-cancelacion-reserva.md)
- `Consultar reportes` — el camino de vuelta: las ausencias que aquí se reportan son parte de lo que el Módulo 3 devuelve después como sanción; ver [spec-modulo2-uc6-consultar-reportes.md](./spec-modulo2-uc6-consultar-reportes.md)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Dejar constancia de quien no se presentó (Priority: P2)

Como sistema, quiero detectar cuando una persona no se presenta a usar el recurso que apartó y reportárselo al Módulo 3, para que la universidad pueda desincentivar las reservas fantasma y para que el espacio no siga bloqueado inútilmente.

**Why this priority**: Es P2 porque el sistema funciona sin ella —se puede consultar y reservar igual—, pero sin este reporte la ocupación real se degrada: las personas apartan por si acaso, no van, y nadie asume ninguna consecuencia. Va junto a `Cancelar reserva`, que es la otra mitad de la misma idea.

**Independent Test**: Se puede probar sola creando una reserva, dejando pasar el plazo sin registrar el uso y verificando que se generó el reporte de ausencia con la persona, el recurso y la franja correctos, y que se envió al Módulo 3. No necesita que las sanciones estén implementadas.

**Acceptance Scenarios**:

1. **Scenario**: La persona no se presenta
   - **Given** un Estudiante tiene reservada la "Sala de Estudio 3" el 2026-09-01 a las 10:00 y no canceló
   - **When** pasan 10 minutos desde las 10:00 sin que se registre el uso
   - **Then** el sistema deja constancia de la ausencia, libera el recurso y le reporta la ausencia al Módulo 3 con la persona, el recurso y la franja

2. **Scenario**: La persona sí llega a tiempo
   - **Given** un Estudiante tiene reservada la "Sala de Estudio 3" a las 10:00
   - **When** se registra el uso a las 10:07
   - **Then** no se genera ninguna ausencia y no se reporta nada al Módulo 3

3. **Scenario**: La persona canceló antes
   - **Given** un Estudiante canceló su reserva media hora antes de la franja
   - **When** llega la hora de la franja y nadie usa el recurso
   - **Then** no se genera ninguna ausencia, porque el recurso ya se había liberado a tiempo

4. **Scenario**: La reserva la canceló el sistema por una clase
   - **Given** una reserva estudiantil fue cancelada automáticamente porque entró una actividad docente
   - **When** llega la hora de esa franja
   - **Then** no se genera ninguna ausencia ni se reporta nada, porque la persona no tuvo culpa

### Edge Cases

- **Llega justo en el minuto 10**: el criterio del borde debe ser explícito y siempre el mismo; una persona que se presenta exactamente al cumplirse el plazo no puede quedar reportada unas veces sí y otras no.
- **Llega después de que se liberó el recurso**: si aparece en el minuto 15, la ausencia ya está reportada y no se deshace; el recurso ya volvió a estar disponible para otros y puede que alguien más lo haya tomado.
- **El Módulo 3 no responde**: la ausencia se registra igualmente y el recurso se libera igual; el reporte queda pendiente y se reintenta hasta entregarse.
- **Reporte repetido**: una misma ausencia no puede reportarse dos veces, para que la persona no reciba dos sanciones por el mismo hecho.
- **Recurso caído durante la franja**: si el recurso pasó a mantenimiento y por eso la persona no pudo usarlo, no debe contarse como ausencia suya.
- **Reserva de varias horas**: la ausencia se mide desde el inicio de la franja, no desde cada hora dentro de ella; una reserva de 10:00 a 14:00 genera como máximo una ausencia.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE detectar como ausencia toda reserva vigente en la que no se registre el uso dentro de los 10 minutos siguientes al inicio de la franja.
- **FR-002**: El sistema DEBE reportar cada ausencia al Módulo 3 indicando la persona, el recurso, la franja y la fecha y hora en que se detectó.
- **FR-003**: El sistema NO DEBE aplicar la sanción; esa decisión y su aplicación corresponden al Módulo 3.
- **FR-004**: El sistema DEBE liberar el recurso al confirmar la ausencia, dejándolo disponible para el resto de la franja.
- **FR-005**: El sistema NO DEBE generar ausencia cuando la reserva fue cancelada a tiempo por su titular.
- **FR-006**: El sistema NO DEBE generar ausencia cuando la reserva fue cancelada automáticamente por prioridad académica.
- **FR-007**: Una misma reserva NO DEBE generar más de una ausencia.
- **FR-008**: Si el Módulo 3 no está disponible, la ausencia DEBE quedar registrada y el reporte DEBE reintentarse hasta entregarse.
- **FR-009**: El sistema DEBE guardar el historial de ausencias para que alimente los reportes de cumplimiento.
- **FR-010**: El sistema DEBE informar a la persona que se le registró una ausencia y por qué reserva.

### Key Entities

- **Ausencia**: constancia de que alguien no usó lo que apartó. Atributos: persona, recurso, franja, reserva de origen, fecha y hora de detección, resultado del reporte al Módulo 3.
- **Reserva**: el apartado que quedó sin usar.
- **Recurso**: el espacio o equipo que quedó bloqueado sin necesidad.
- **Usuario**: la persona a la que se le anota la ausencia.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las reservas no usadas quedan reportadas al Módulo 3 dentro de los 15 minutos siguientes al inicio de la franja.
- **SC-002**: Cero ausencias reportadas dos veces sobre la misma reserva.
- **SC-003**: Cero ausencias atribuidas a personas cuya reserva fue cancelada por prioridad académica o por mantenimiento del recurso.
- **SC-004**: Reducción del 40 % en las franjas apartadas y no usadas durante el primer semestre de operación.
- **SC-005**: Cero reportes perdidos ante una caída del Módulo 3 de hasta 30 minutos.
