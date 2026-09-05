# Feature Specification: Reportar cancelación de reserva

**Created**: 2026-09-04
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Reportar cancelación de reserva` (informa al Módulo 3) — óvalo introducido en el diagrama 3 y conservado en el vigente `Unimag4.png`, fuente en `unimag4.drawio`
**Prioridad global**: P2

## Contexto

Cuando alguien deshace una reserva, ese hecho no puede quedarse dentro del Módulo 2. El Módulo 3 lleva la matriz de cumplimiento de cada persona y necesita saber que esa franja se cerró **porque se canceló**, no porque nadie se presentó. Es la diferencia entre alguien que avisó a tiempo y liberó el recurso para otros, y alguien que lo dejó bloqueado sin usar.

Este caso de uso cierra la familia de reportes hacia el Módulo 3, junto a `Reportar no asistencia` y `Reportar fecha y hora de entrega`: los tres describen cómo terminó algo y ninguno de los tres sanciona. El reparto es el mismo de siempre: el Módulo 2 registra y reporta los hechos, el Módulo 3 saca las consecuencias.

Hay dos orígenes de cancelación y el reporte tiene que distinguirlos, porque tienen consecuencias opuestas:

| Origen | Quién la provoca | Qué debe entender el Módulo 3 |
|---|---|---|
| Cancelación del titular | El Estudiante o Monitor que reservó | Cumplió: liberó a tiempo. Según [gestionunimag.md](../gestionunimag.md), esto alimenta positivamente su "score" de confianza. |
| Cancelación por prioridad académica | El sistema, al entrar una actividad docente | La persona no tuvo ninguna culpa; no puede penalizársele por esto. |

El deslinde con `Notificar estado de recursos al finalizar reserva` (UC5) ya está hecho: aquel notifica **en qué estado quedó el recurso después de usarlo**, y por eso no dice nada cuando la reserva se canceló o cuando nadie se presentó —en esos casos el recurso nunca se usó—. Todas las cancelaciones, sin excepción, se reportan desde aquí.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Módulo 3 | Secundario | Recibe el reporte de cancelación; actualiza el cumplimiento de la persona y decide si hay consecuencia. |
| Estudiante / Monitor | Indirectos | Son los titulares de las reservas canceladas; no ejecutan este caso de uso. |

**Casos de uso relacionados**

- `Cancelar reserva` — es lo que dispara este reporte, tanto la cancelación del titular como la automática por prioridad; ver [spec-modulo2-uc4-cancelar-reserva.md](./spec-modulo2-uc4-cancelar-reserva.md)
- `Reportar no asistencia` — el cierre contrario: la reserva no se canceló, simplemente nadie llegó; ver [spec-modulo2-uc9-reportar-no-asistencia.md](./spec-modulo2-uc9-reportar-no-asistencia.md)
- `Notificar estado de recursos al finalizar reserva` — el complemento: notifica el estado del recurso solo cuando sí se usó, y guarda silencio ante las cancelaciones que se reportan aquí; ver [spec-modulo2-uc5-notificar-estado-recursos.md](./spec-modulo2-uc5-notificar-estado-recursos.md)
- `Actualizar estado de los recursos` — libera la franja al cancelar y avisa al Módulo 1; es el camino paralelo hacia el otro módulo; ver [spec-modulo2-uc7-actualizar-estado-recursos.md](./spec-modulo2-uc7-actualizar-estado-recursos.md)
- `Consultar reportes` — el camino de vuelta: lo que aquí se reporta es parte de lo que el Módulo 3 devuelve después como cumplimiento o sanción; ver [spec-modulo2-uc6-consultar-reportes.md](./spec-modulo2-uc6-consultar-reportes.md)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Contarle al Módulo 3 que la reserva se canceló y por culpa de quién (Priority: P2)

Como sistema, quiero reportarle al Módulo 3 cada cancelación de reserva indicando si la hizo el titular o si fue el sistema por prioridad académica, para que la universidad pueda premiar a quien libera a tiempo, no castigar a quien fue desplazado por una clase, y distinguir una cancelación de una ausencia.

**Why this priority**: Es P2 porque acompaña a `Cancelar reserva`, que también es P2. Sin este reporte, el Módulo 3 ve una franja que quedó vacía y no puede saber si fue porque alguien avisó o porque alguien no apareció; en el peor caso, sanciona a quien hizo bien las cosas.

**Independent Test**: Se puede probar sola cancelando una reserva y verificando, contra un simulador del Módulo 3, que llegó el reporte con la persona, el recurso, la franja, el origen de la cancelación y la antelación con que se hizo. No necesita que las sanciones ni el "score" estén implementados.

**Acceptance Scenarios**:

1. **Scenario**: El titular cancela a tiempo
   - **Given** un Estudiante tiene reservada la "Sala de Estudio 3" el 2026-09-01 de 10:00 a 12:00
   - **When** la cancela a las 08:30 y la reserva queda `CANCELADA`
   - **Then** el sistema le reporta al Módulo 3 la cancelación con la persona, el recurso, la franja, el origen "titular" y la antelación de una hora y media, dejando claro que no es un incumplimiento

2. **Scenario**: Una clase desplaza la reserva
   - **Given** una reserva estudiantil es cancelada automáticamente porque entró una actividad docente
   - **When** la reserva queda `CANCELADA_POR_PRIORIDAD_ACADEMICA`
   - **Then** el sistema le reporta al Módulo 3 la cancelación con el origen "prioridad académica", indicando expresamente que **no** es responsabilidad de la persona

3. **Scenario**: El recurso se da de baja
   - **Given** el "Videobeam 12" pasa a mantenimiento y sus reservas futuras se cancelan
   - **When** se ejecutan esas cancelaciones
   - **Then** el sistema reporta cada una al Módulo 3 con el origen "recurso no disponible", sin atribuirle responsabilidad a ningún titular

4. **Scenario**: El Módulo 3 no responde
   - **Given** el Módulo 3 está caído temporalmente
   - **When** un Estudiante cancela su reserva
   - **Then** la cancelación se ejecuta igual y el recurso se libera igual; el reporte queda pendiente y se reintenta hasta entregarse

5. **Scenario**: Cancelación rechazada
   - **Given** un Estudiante intenta cancelar una reserva fuera de plazo y el sistema la rechaza con `CAN-001`
   - **When** termina el intento
   - **Then** no se reporta nada al Módulo 3, porque no hubo cancelación

**Notas de trazabilidad**

- Los orígenes de cancelación que aquí se reportan deben ser exactamente los estados que define `Cancelar reserva`; si allí se añade uno nuevo, aquí tiene que aparecer.

### Edge Cases

- **Cancelación en el límite del plazo**: una cancelación aceptada justo en el minuto de antelación mínima se reporta como cancelación del titular, igual que cualquier otra; el borde lo define `Cancelar reserva` y este caso de uso no lo reinterpreta.
- **Doble cancelación**: una segunda solicitud sobre una reserva ya cancelada es idempotente y no puede generar un segundo reporte.
- **Cancelación y ausencia sobre la misma reserva**: son excluyentes. Si la reserva se canceló, no puede reportarse además una ausencia por ella; el criterio manda sobre `Reportar no asistencia`.
- **Cancelación masiva por importación de horarios**: una carga que desplaza decenas de reservas genera un reporte por cada una, sin agruparlas de forma que se pierda a quién le tocó.
- **Cancelación de un préstamo ya entregado**: si el equipo ya está en manos de la persona, lo que corresponde no es una cancelación sino una devolución; ese caso lo cubre `Reportar fecha y hora de entrega`.
- **Orden de los reportes**: si sobre el mismo recurso se cancelan dos reservas seguidas, los reportes deben llegar al Módulo 3 en el orden en que ocurrieron.
- **Antelación en la cancelación automática**: cuando cancela el sistema, la antelación no dice nada de la persona; el reporte no debe permitir que el Módulo 3 la lea como mérito ni como falta.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE reportar al Módulo 3 toda cancelación de reserva que se haya ejecutado, cualquiera que sea su origen.
- **FR-002**: Cada reporte DEBE indicar la reserva, la persona, el recurso, el tiempo que se liberó —la franja cancelada si era un espacio, o el periodo de préstamo completo si era un objeto que aún no se había recogido—, el origen de la cancelación, el motivo y la fecha y hora en que se ejecutó.
- **FR-003**: El reporte DEBE distinguir la cancelación hecha por el titular de la cancelación automática por prioridad académica y de la originada en la indisponibilidad del recurso.
- **FR-004**: El reporte de una cancelación del titular DEBE incluir la antelación con la que se hizo respecto al inicio de la franja.
- **FR-005**: El sistema NO DEBE decidir ni aplicar consecuencia alguna por la cancelación; eso corresponde al Módulo 3.
- **FR-006**: Las cancelaciones que no son responsabilidad de la persona DEBEN reportarse marcadas como tales, para que no computen en su contra.
- **FR-007**: Una misma cancelación NO DEBE reportarse más de una vez, aunque el envío se reintente.
- **FR-008**: Si el Módulo 3 no está disponible, la cancelación DEBE ejecutarse igualmente y el reporte DEBE reintentarse hasta entregarse.
- **FR-009**: El sistema NO DEBE reportar los intentos de cancelación rechazados.
- **FR-010**: El sistema DEBE conservar el historial de cancelaciones reportadas y el resultado de cada envío, para poder auditarlo.
- **FR-011**: Una reserva reportada como cancelada NO DEBE generar además un reporte de ausencia por la misma franja.

### Key Entities

- **ReporteDeCancelación**: constancia de que una reserva se deshizo y aviso correspondiente al Módulo 3. Atributos: reserva de origen, persona, recurso, franja, origen de la cancelación, motivo, antelación, fecha y hora de ejecución, resultado del envío.
- **Reserva**: el apartado que se cancela; aporta su estado final (`CANCELADA` o `CANCELADA_POR_PRIORIDAD_ACADEMICA`) y el motivo.
- **Recurso**: el espacio o equipo cuya franja vuelve a quedar libre.
- **Usuario**: el titular de la reserva cancelada, a quien el Módulo 3 le anota el hecho.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las cancelaciones ejecutadas quedan reportadas al Módulo 3 con su origen.
- **SC-002**: El reporte llega al Módulo 3 dentro de los 5 minutos siguientes a la cancelación.
- **SC-003**: Cero cancelaciones reportadas dos veces sobre la misma reserva.
- **SC-004**: Cero personas penalizadas por cancelaciones originadas en prioridad académica o en la indisponibilidad del recurso.
- **SC-005**: Cero reportes perdidos ante una caída del Módulo 3 de hasta 30 minutos.
- **SC-006**: Cero reservas con reporte de cancelación y reporte de ausencia a la vez.
