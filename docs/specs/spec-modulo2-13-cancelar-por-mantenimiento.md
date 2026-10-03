# Feature Specification: Cancelar reservas por mantenimiento de un espacio

**Created**: 2026-09-21
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Tipo**: especificación de regla de negocio. **No es un caso de uso**: no tiene óvalo en el diagrama. Describe lo que ocurre cuando `Recibir check-out` registra que un espacio requiere mantenimiento, y lo resuelve con casos de uso que ya existen.
**Origen**: último edge case de la User Story 2 de [spec-modulo2-uc12-recibir-check-out.md](./spec-modulo2-uc12-recibir-check-out.md)
**Prioridad global**: P3

## Contexto

Después de cada uso de un espacio, el Módulo 3 lo revisa y nos manda su check-out con un dictamen: `SIN_NOVEDAD` o `REQUIERE_MANTENIMIENTO` (UC12 FR-011). `Recibir check-out` registra ese dictamen sobre la reserva que se acaba de usar, y ahí termina su trabajo.

El problema está en las reservas que vienen después. Si un Monitor usó el "Salón 204" de 08:00 a 10:00 y la revisión dice que se dañó el aire acondicionado, el Estudiante que tiene el mismo salón reservado de 14:00 a 16:00 va a llegar a un espacio que no se puede usar. Su reserva sigue `CONFIRMADA`, él no sabe nada y va a perder el viaje. Tampoco puede reservar otro espacio a tiempo, porque se entera en la puerta.

Esta especificación define la regla: **cuando un espacio queda con dictamen `REQUIERE_MANTENIMIENTO`, el sistema cancela automáticamente las reservas estudiantiles confirmadas que tenía por delante y le avisa a cada titular**. La cancelación no es culpa de nadie: no penaliza al titular, le devuelve el cupo y se le reporta al Módulo 3 como originada en la indisponibilidad del recurso.

No hace falta un caso de uso nuevo, porque todas las piezas ya existen:

- `Cancelar reserva` ya prevé la cancelación automática con el estado `CANCELADA_POR_RECURSO_NO_DISPONIBLE`, sin penalizar al titular (UC4 FR-010).
- `Actualizar estado de los recursos` libera la franja y el cupo al cancelar (UC4 FR-002).
- `Reportar cancelación de reserva` ya distingue el origen "recurso no disponible" (UC11 FR-003).

Lo que faltaba era **qué dispara** esa cancelación y **qué se le dice** a la persona. UC4 FR-010 esperaba un aviso del Módulo 1 que hoy no existe (P-20, punto 2). Para los espacios, el aviso es el propio dictamen del check-out, que ya nos llega.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Módulo 3 | Secundario | Revisa el espacio después de su uso y envía el check-out con el dictamen `REQUIERE_MANTENIMIENTO`, que es lo que dispara la regla. Después recibe el reporte de cada cancelación. |
| Estudiante / Monitor | Afectados | Titulares de las reservas canceladas. No ejecutan nada: reciben el aviso de la cancelación. |

**Casos de uso relacionados**

- `Recibir check-out` — registra el dictamen que dispara esta regla (UC12 FR-011 y FR-012); ver [spec-modulo2-uc12-recibir-check-out.md](./spec-modulo2-uc12-recibir-check-out.md)
- `Cancelar reserva` — ejecuta la cancelación automática con `CANCELADA_POR_RECURSO_NO_DISPONIBLE` (UC4 FR-010); ver [spec-modulo2-uc4-cancelar-reserva.md](./spec-modulo2-uc4-cancelar-reserva.md)
- `Actualizar estado de los recursos` — libera la franja y el cupo de cada reserva cancelada; ver [spec-modulo2-uc7-actualizar-estado-recursos.md](./spec-modulo2-uc7-actualizar-estado-recursos.md)
- `Reportar cancelación de reserva` — le cuenta al Módulo 3 cada cancelación con el origen "recurso no disponible"; ver [spec-modulo2-uc11-reportar-cancelacion-reserva.md](./spec-modulo2-uc11-reportar-cancelacion-reserva.md)
- `Consultar disponibilidad de los recursos` — muestra el espacio como `EN_MANTENIMIENTO` una vez el Módulo 1 lo refleje; ver [spec-modulo2-uc8-consultar-disponibilidad-recursos.md](./spec-modulo2-uc8-consultar-disponibilidad-recursos.md)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cancelar las reservas de un espacio que quedó en mantenimiento (Priority: P3)

Como sistema, quiero cancelar automáticamente las reservas confirmadas de un espacio cuando su revisión dice que requiere mantenimiento, para que nadie llegue a un espacio que no se puede usar y su franja no quede apartada a nombre de alguien que no la va a poder aprovechar.

**Why this priority**: Es P3 porque depende de `Recibir check-out`, que también es P3. Sin esta regla el dictamen queda registrado, pero las reservas siguientes siguen `CONFIRMADA` y sus titulares se enteran del daño en la puerta del salón.

**Independent Test**: Se puede probar sola creando varias reservas confirmadas sobre un espacio, enviando el check-out de una reserva anterior con dictamen `REQUIERE_MANTENIMIENTO` y verificando que las reservas que no han comenzado quedaron `CANCELADA_POR_RECURSO_NO_DISPONIBLE`, que el cupo de cada titular se liberó y que se generó un reporte de cancelación por cada una, contra un simulador del Módulo 3.

**Acceptance Scenarios**:

1. **Scenario**: El espacio tiene reservas más tarde el mismo día
   - **Given** un Monitor usó el "Salón 204" el 2026-09-22 de 08:00 a 10:00, y un Estudiante tiene ese salón `CONFIRMADA` de 14:00 a 16:00
   - **When** a las 10:15 el Módulo 3 reporta el check-out de la reserva del Monitor con dictamen "requiere mantenimiento"
   - **Then** el sistema cancela la reserva del Estudiante con `CANCELADA_POR_RECURSO_NO_DISPONIBLE`, le devuelve el cupo y reporta la cancelación al Módulo 3 con el origen "recurso no disponible"

2. **Scenario**: Varias reservas afectadas
   - **Given** el "Laboratorio 3" tiene tres reservas estudiantiles confirmadas de tres personas distintas después de la revisión
   - **When** llega el dictamen "requiere mantenimiento"
   - **Then** el sistema cancela las tres, cada una con su propio reporte al Módulo 3 y su propio aviso a su titular

3. **Scenario**: Revisión sin novedad
   - **Given** el "Salón 204" tiene reservas confirmadas para la tarde
   - **When** el Módulo 3 reporta el check-out con dictamen "sin novedad"
   - **Then** el sistema no cancela ninguna reserva

4. **Scenario**: La cancelación no penaliza
   - **Given** la reserva de un Estudiante se canceló por mantenimiento del espacio
   - **When** el Módulo 3 recibe el reporte de esa cancelación
   - **Then** el reporte indica que la cancelación no es responsabilidad del titular, y la reserva no cuenta como ausencia ni como cancelación tardía

---

### User Story 2 - Avisarle al titular que su reserva se canceló (Priority: P3)

Como Estudiante o Monitor, quiero enterarme de que mi reserva se canceló porque el espacio quedó en mantenimiento, para no ir hasta allá en vano y tener tiempo de reservar otro espacio.

**Why this priority**: Va con la historia 1: cancelar sin avisar deja a la persona igual que antes, solo que ahora el sistema tampoco la espera. El aviso es lo que convierte la cancelación en algo útil para ella.

**Independent Test**: Se puede probar sola disparando la cancelación de una reserva por mantenimiento y verificando que su titular recibió un aviso con el espacio, la franja y el motivo, y que en su lista de reservas la ve cancelada con ese mismo motivo.

**Acceptance Scenarios**:

1. **Scenario**: El titular recibe el aviso
   - **Given** la reserva del Estudiante sobre el "Salón 204" de 14:00 a 16:00 se canceló por mantenimiento
   - **When** termina la cancelación
   - **Then** el Estudiante recibe un aviso que dice qué espacio, qué franja y que se canceló porque el espacio requiere mantenimiento, sin que él tenga responsabilidad

2. **Scenario**: El titular consulta sus reservas
   - **Given** la reserva del Estudiante se canceló por mantenimiento
   - **When** revisa sus reservas en la aplicación
   - **Then** la ve cancelada con el motivo "el espacio requiere mantenimiento", distinta de las que él mismo canceló

3. **Scenario**: El titular busca otro espacio
   - **Given** el Estudiante recibió el aviso de la cancelación
   - **When** lo abre
   - **Then** el aviso le ofrece buscar otro espacio para la misma franja, y el sistema no le reserva ninguno por su cuenta

### Edge Cases

- **Reserva que ya comenzó**: el check-out llega después del uso revisado, así que la reserva siguiente puede haber empezado ya (por ejemplo, franjas seguidas de 08:00 a 10:00 y de 10:00 a 12:00, con revisión a las 10:10). Esa reserva no se cancela: la persona ya está en el sitio, y lo que haga con ella lo resuelve en persona el Módulo 3. Solo se cancelan las reservas cuya franja no ha comenzado.
- **Hasta cuándo se cancela**: el Módulo 2 no sabe cuánto dura el mantenimiento, porque ese estado es del Módulo 1. [NEEDS CLARIFICATION: ¿se cancelan todas las reservas futuras del espacio, solo las del mismo día, o las que caigan antes de una fecha de fin de mantenimiento que informe el Módulo 1? Ver P-20, punto 3.]
- **Bloqueos académicos sobre el espacio**: una clase tampoco se puede dar en un espacio dañado, pero un bloqueo académico no tiene titular estudiantil y cancelarlo dejaría la clase sin salón. [NEEDS CLARIFICATION: ¿se cancela también el bloqueo, o solo se le avisa a la Dirección de Programa para que reubique la clase?]
- **Reservas nuevas después del dictamen**: mientras el Módulo 1 no refleje `EN_MANTENIMIENTO`, la consulta podría seguir mostrando el espacio disponible y alguien podría reservarlo. [NEEDS CLARIFICATION: ¿el Módulo 2 impide por su cuenta nuevas reservas del espacio desde el dictamen, o espera a que el Módulo 1 lo marque? Depende de que el Módulo 3 le reporte el dictamen al Módulo 1; ver P-20, punto 2.]
- **Check-out repetido**: si el mismo check-out llega dos veces, UC12 FR-007 impide procesarlo de nuevo, así que no se cancela nada dos veces ni se envían avisos duplicados.
- **Reserva ya cancelada o finalizada**: solo se cancelan las reservas `CONFIRMADA`. Las que ya estaban canceladas o finalizadas no cambian.
- **Activos que vuelven dañados**: esta especificación cubre solo espacios. El check-out de un activo no trae dictamen (UC12 FR-010), y sus daños van del Módulo 3 al Módulo 1; cómo se entera el Módulo 2 sigue abierto en P-20, punto 2.
- **El Módulo 3 no recibe el reporte**: la cancelación y el aviso al titular no dependen de él; el reporte se reintenta como cualquier otro de `Reportar cancelación de reserva`.
- **El aviso no se puede entregar**: la cancelación se mantiene. La reserva cancelada con su motivo sigue visible para el titular en la aplicación, aunque el aviso no le haya llegado.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Al procesar el check-out de un espacio con dictamen `REQUIERE_MANTENIMIENTO`, el sistema DEBE cancelar automáticamente las reservas estudiantiles `CONFIRMADA` de ese espacio cuya franja no haya comenzado, dentro del horizonte que se defina [NEEDS CLARIFICATION: ver el edge case **Hasta cuándo se cancela**].
- **FR-002**: Cada reserva cancelada por esta regla DEBE quedar con el estado `CANCELADA_POR_RECURSO_NO_DISPONIBLE` y con el motivo "el espacio requiere mantenimiento", que referencia el check-out que la originó.
- **FR-003**: La cancelación DEBE ejecutarse por el mismo camino de `Cancelar reserva` FR-010: liberar la franja y el cupo con `Actualizar estado de los recursos`, y reportarla con `Reportar cancelación de reserva` con el origen "recurso no disponible".
- **FR-004**: Estas cancelaciones NO DEBEN penalizar al titular, ni contar como ausencia o como cancelación fuera de plazo.
- **FR-005**: El sistema NO DEBE cancelar reservas cuya franja ya haya comenzado, ni reservas que no estén `CONFIRMADA`.
- **FR-006**: El sistema NO DEBE cancelar ninguna reserva cuando el dictamen sea `SIN_NOVEDAD`.
- **FR-007**: El sistema DEBE avisarle a cada titular afectado que su reserva se canceló, indicando el espacio, la franja, el motivo y que la cancelación no es responsabilidad suya. [NEEDS CLARIFICATION: canal del aviso — notificación dentro de la aplicación, correo institucional o ambos.]
- **FR-008**: El aviso DEBE ofrecerle al titular buscar otro espacio para la misma franja. El sistema NO DEBE reservarle otro espacio por su cuenta.
- **FR-009**: El titular DEBE poder ver en sus reservas la reserva cancelada con su motivo, distinguible de las que canceló él mismo.
- **FR-010**: Toda la operación DEBE ser idempotente: un mismo check-out no puede cancelar dos veces una reserva, liberar dos veces un cupo ni generar avisos o reportes duplicados.
- **FR-011**: El sistema DEBE dejar registro de qué check-out originó cada cancelación, para poder rastrear todas las reservas afectadas por un mismo dictamen.

### Key Entities

- **CheckOut**: el aviso del Módulo 3 con el dictamen `REQUIERE_MANTENIMIENTO` que dispara la regla; ver UC12.
- **Reserva**: cada apartado estudiantil confirmado que se cancela; pasa a `CANCELADA_POR_RECURSO_NO_DISPONIBLE` y guarda el motivo y el check-out de origen.
- **Recurso**: el espacio que quedó en mantenimiento.
- **Usuario**: el titular de cada reserva cancelada, que recupera su cupo y recibe el aviso.
- **AvisoAlTitular**: lo que se le comunica a la persona afectada. Atributos: titular, reserva cancelada, espacio, franja, motivo, fecha y hora de envío y resultado de la entrega.
- **ReporteDeCancelación**: el reporte al Módulo 3 de cada reserva cancelada, con el origen "recurso no disponible"; ver UC11.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las reservas estudiantiles confirmadas que no han comenzado, dentro del horizonte definido, quedan canceladas después de un dictamen `REQUIERE_MANTENIMIENTO` sobre su espacio.
- **SC-002**: Cada titular afectado recibe el aviso en menos de 5 minutos desde que llega el check-out.
- **SC-003**: Cero titulares penalizados por cancelaciones originadas en el mantenimiento de un espacio.
- **SC-004**: El 100 % de las cancelaciones por mantenimiento quedan reportadas al Módulo 3 con el origen "recurso no disponible".
- **SC-005**: Cero reservas canceladas, avisos o reportes duplicados por un check-out repetido.
- **SC-006**: Cero reservas ya comenzadas canceladas por esta regla.
