# Feature Specification: Consultar disponibilidad de los recursos

**Created**: 2026-09-03
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Consultar disponibilidad de los recursos` (comprobación puntual, apoyada en el inventario del Módulo 1)
**Prioridad global**: P1

## Contexto

Es la pregunta corta: *¿este recurso concreto está libre en esta franja concreta, sí o no?* Se apoya en el inventario del Módulo 1 y responde en el momento, sin ninguna foto guardada de antes.

No es lo mismo que `Consultar recursos`. Aquel muestra una lista al estudiante para que mire qué hay; este responde por un solo recurso y una sola franja, y es la comprobación que se hace justo antes de confirmar una reserva. Esa diferencia importa: entre que el estudiante ve la lista y decide apartar algo, otra persona puede haberse adelantado. Por eso la lista sirve para orientarse y esta comprobación sirve para decidir.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Módulo 1 | Secundario | Aporta el estado real del recurso en el inventario. |
| Estudiante / Monitor | Indirectos | No la piden a mano; ocurre por dentro cuando consultan o reservan. |

**Casos de uso relacionados**

- `Consultar recursos` — la lista que ve el estudiante se arma preguntando la disponibilidad de cada recurso; ver [spec-modulo2-uc1-consultar-recursos.md](./spec-modulo2-uc1-consultar-recursos.md)
- `Reservar recursos` — vuelve a preguntar aquí en el último momento, antes de confirmar; ver [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md)
- `Actualizar estado de los recursos` — deja escrito lo que esta consulta lee; ver [spec-modulo2-uc7-actualizar-estado-recursos.md](./spec-modulo2-uc7-actualizar-estado-recursos.md)

**Qué cuenta como "no disponible"**

Un recurso no está disponible en una franja si en esa franja está `RESERVADO`, `BLOQUEO_ACADEMICO`, `EN_USO` o `EN_MANTENIMIENTO`. Basta con que se crucen un minuto para que cuente como ocupado.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Saber si un recurso está libre en una franja (Priority: P1)

Como sistema, quiero poder preguntar en cualquier momento si un recurso está libre en una franja y recibir una respuesta clara con el motivo cuando no lo está, para no confirmar reservas sobre espacios que ya están ocupados y para poder explicarle a la persona por qué no puede apartarlo.

**Why this priority**: Es P1 porque tanto `Consultar recursos` como `Reservar recursos` dependen de ella. Sin esta comprobación el sistema tendría que confiar en información vieja, que es justo lo que produce dos reservas sobre el mismo salón.

**Independent Test**: Se puede probar sola cargando recursos en distintos estados y preguntando por cada uno en una franja concreta, verificando que responde correctamente y que, cuando dice que no, explica el motivo. No necesita que existan reservas, reportes ni notificaciones.

**Acceptance Scenarios**:

1. **Scenario**: El recurso está libre
   - **Given** la "Sala de Estudio 3" no tiene nada encima el 2026-09-01 de 10:00 a 12:00
   - **When** se consulta su disponibilidad para esa franja
   - **Then** el sistema responde que está disponible

2. **Scenario**: El recurso tiene clase
   - **Given** el "Salón 201" tiene clase el 2026-09-01 de 08:00 a 10:00
   - **When** se consulta su disponibilidad para esa franja
   - **Then** el sistema responde que no está disponible e indica que el motivo es un bloqueo académico

3. **Scenario**: El recurso ya está apartado
   - **Given** el "Auditorio Menor" está reservado por otra persona el 2026-09-01 de 14:00 a 16:00
   - **When** se consulta su disponibilidad para esa franja
   - **Then** el sistema responde que no está disponible e indica que ya está reservado, sin revelar quién lo reservó

4. **Scenario**: El recurso está en reparación
   - **Given** el "Videobeam 12" está en mantenimiento
   - **When** se consulta su disponibilidad para cualquier franja
   - **Then** el sistema responde que no está disponible e indica que está en mantenimiento

5. **Scenario**: Cruce parcial de horarios
   - **Given** el "Laboratorio de Redes" tiene clase de 08:00 a 10:00
   - **When** se consulta su disponibilidad de 09:30 a 10:30
   - **Then** el sistema responde que no está disponible, porque las dos franjas se cruzan aunque sea media hora

### Edge Cases

- **Franjas que se tocan pero no se cruzan**: una clase de 08:00 a 10:00 y una consulta de 10:00 a 12:00 no se cruzan; el recurso sí está disponible. El criterio del borde debe ser el mismo siempre.
- **El inventario no responde**: si el Módulo 1 no está disponible, la consulta NO debe responder "disponible" por defecto; debe avisar que no se pudo comprobar, para que nadie confirme una reserva a ciegas.
- **Recurso que no existe**: se responde que no se encontró, no que está ocupado.
- **La respuesta envejece enseguida**: la disponibilidad vale para el instante en que se preguntó; por eso `Reservar recursos` vuelve a preguntar antes de confirmar en vez de reutilizar la respuesta anterior.
- **Horario operativo y franjas que cruzan la medianoche**: No se permite reservar ni consultar disponibilidad dentro del horario nocturno de 22:00 (10:00 p. m.) a 06:00 (06:00 a. m.) del día siguiente (zona horaria `America/Bogota`, UTC-5). Toda franja válida debe iniciar y concluir dentro del rango operativo diurno (06:00 a 22:00) del mismo día; cualquier consulta sobre una franja fuera de este horario o que cruce la medianoche se reporta como no disponible / inválida.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE responder si un recurso concreto está o no disponible en una fecha y franja horaria concretas.
- **FR-002**: El sistema DEBE considerar no disponible todo recurso que en esa franja esté `RESERVADO`, `BLOQUEO_ACADEMICO`, `EN_USO` o `EN_MANTENIMIENTO`.
- **FR-003**: El sistema DEBE tratar como ocupada cualquier franja que se cruce con otra aunque sea un minuto.
- **FR-004**: Cuando el recurso no esté disponible, el sistema DEBE indicar el motivo, para que quien pregunte pueda explicárselo a la persona.
- **FR-005**: El sistema NO DEBE revelar la identidad de quien tiene reservado el recurso.
- **FR-006**: La consulta DEBE responder con el estado del momento, sin reutilizar respuestas anteriores.
- **FR-007**: Si el inventario del Módulo 1 no está disponible, el sistema DEBE informar que no se pudo comprobar y NO DEBE dar el recurso por disponible.
- **FR-008**: El sistema DEBE poder responder por varios recursos de una vez, para que `Consultar recursos` pueda armar su lista sin preguntar uno por uno.
- **FR-009**: La consulta NO DEBE cambiar nada: no aparta el recurso ni modifica su estado.

### Key Entities

- **Recurso**: espacio o equipo por el que se pregunta.
- **FranjaHoraria**: día con hora de inicio y hora de fin sobre la que se pregunta.
- **RespuestaDeDisponibilidad**: lo que devuelve la consulta. Atributos: recurso, franja, si está disponible o no, motivo cuando no lo está, fecha y hora de la respuesta.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La consulta responde en menos de 2 segundos para un recurso y una franja.
- **SC-002**: El 100 % de las respuestas negativas vienen acompañadas de un motivo; ninguna dice solo "no disponible".
- **SC-003**: Cero casos en los que la consulta diga que un recurso está libre cuando en el inventario figura ocupado.
- **SC-004**: Cero reservas confirmadas sobre recursos que esta consulta había reportado como ocupados.
