# Feature Specification: Recibir check-out

**Created**: 2026-09-12
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Recibir check-out` — la flecha va del Módulo 3 hacia este caso de uso, porque el check-out lo hace él; de aquí sale un `<<include>>` hacia `Actualizar estado de los recursos`
**Prioridad global**: P3

## Contexto

Un check-out es un acto físico: alguien trae de vuelta el microscopio, otra persona lo recibe y comprueba en qué estado viene. Solo existe donde hay algo que devolver, o sea en los **activos**; un espacio no se devuelve, se libera solo cuando termina su franja.

Ese acto no es nuestro. Según [gestionunimag.md](../gestionunimag.md), la *"Gestión de Devoluciones y Novedades"* es del **Módulo 3**: es él quien registra el estado del recurso como un check-out exitoso o reporta una novedad técnica si vuelve dañado. Nosotros no estamos en el mostrador y no podemos ver si el equipo volvió completo o rayado.

Tampoco es nuestro el estado físico en que queda el recurso. `DISPONIBLE` y `EN_MANTENIMIENTO` son estados del Módulo 1 (ver el reparto de estados en [spec-modulo2.md](./spec-modulo2.md)), y el Módulo 3 le reporta los daños directamente a él. A nosotros el check-out solo nos trae lo que toca a la reserva: qué préstamo se cierra y cuándo volvió el recurso de verdad.

Lo que sí es nuestro es la consecuencia sobre la reserva. Mientras el Módulo 3 no nos avise, el préstamo sigue abierto y nadie más puede pedir ese activo: `Actualizar estado de los recursos` FR-011 dice que un préstamo no se libera por el paso del tiempo, ni siquiera vencido. Este caso de uso es el que recibe ese aviso y cierra el préstamo: libera el cupo de la persona y desocupa el periodo en el calendario del Módulo 2, para que el activo se pueda volver a reservar. Si además quedó dañado, eso no llega por aquí: se ve al consultar la disponibilidad, que le pregunta al Módulo 1 su estado.

La división de trabajo queda como en `Recibir reporte de no asistencia`: el Módulo 3 constata el hecho en el sitio y nos lo reporta; el Módulo 2 cierra el préstamo y deja la constancia. La mora la calcula él, con la hora pactada que nosotros le dimos al reservar en `Reportar información de la reserva` y la hora real que él mismo observa.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Módulo 3 | Secundario | Recibe físicamente el recurso, hace el check-out y nos reporta qué préstamo se cierra, con la fecha y la hora reales. Sin ese reporte no hay devolución. Los daños se los reporta directamente al Módulo 1. Después calcula la mora y aplica la sanción. |
| Estudiante / Monitor | Indirectos | Son quienes devuelven el recurso; no ejecutan este caso de uso. |

**Casos de uso relacionados**

- `Reservar recursos` — creó el préstamo que aquí se cierra, y fijó la fecha y hora pactadas de devolución; ver [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md)
- `Actualizar estado de los recursos` — **`<<include>>`**: con el check-out recibido, el préstamo se cierra y su periodo queda libre en el calendario del Módulo 2; ver [spec-modulo2-uc7-actualizar-estado-recursos.md](./spec-modulo2-uc7-actualizar-estado-recursos.md)
- `Reportar información de la reserva` — el camino de ida: le dio al Módulo 3 la ficha del préstamo contra la que ahora compara; ver [spec-modulo2-uc10-reportar-informacion-reserva.md](./spec-modulo2-uc10-reportar-informacion-reserva.md)
- `Recibir reporte de no asistencia` — el otro aviso que entra desde el Módulo 3, y el caso contrario: la persona nunca llegó a llevarse el recurso; ver [spec-modulo2-uc9-recibir-reporte-no-asistencia.md](./spec-modulo2-uc9-recibir-reporte-no-asistencia.md)
- `Reportar cancelación de reserva` — la otra forma de cerrar un préstamo, antes de que llegue a entregarse; ver [spec-modulo2-uc11-reportar-cancelacion-reserva.md](./spec-modulo2-uc11-reportar-cancelacion-reserva.md)
- `Consultar sanciones` — el camino de vuelta: la mora que nace de este check-out es parte de lo que después se lee ahí; ver [spec-modulo2-uc6-consultar-sanciones.md](./spec-modulo2-uc6-consultar-sanciones.md)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cerrar el préstamo cuando el Módulo 3 avisa que el recurso volvió (Priority: P3)

Como sistema, quiero recibir del Módulo 3 el check-out de un préstamo y cerrarlo, para que el activo se pueda volver a reservar en cuanto regresa y no se quede bloqueado a nombre de alguien que ya lo devolvió.

**Why this priority**: Es P3 porque el núcleo del módulo —consultar y reservar— funciona sin ella, y porque afecta solo a los activos en préstamo, no a los espacios. Pero sin este aviso ningún préstamo se cierra nunca: los activos prestados se quedan ocupados para siempre en nuestro calendario.

**Independent Test**: Se puede probar sola enviando un check-out sobre un préstamo abierto y verificando que el préstamo se cerró, que el cupo de la persona se liberó y que su periodo quedó libre en el calendario. No necesita que las sanciones estén implementadas.

**Acceptance Scenarios**:

1. **Scenario**: Check-out de una devolución a tiempo
   - **Given** un Estudiante tiene prestado el "Microscopio 07" con devolución pactada el 2026-09-01 a las 22:00
   - **When** el Módulo 3 reporta el check-out con fecha y hora reales del 2026-09-01 a las 15:40
   - **Then** el sistema cierra el préstamo, guarda la fecha y hora recibidas, libera el cupo del titular y el activo vuelve a poder reservarse

2. **Scenario**: Check-out de una devolución con retraso
   - **Given** un Estudiante tiene prestado el "Kit de dibujo 22" con devolución pactada el 2026-09-01 a las 22:00
   - **When** el Módulo 3 reporta el check-out del día siguiente a las 10:30
   - **Then** el sistema cierra el préstamo igual y libera el periodo igual; el retraso queda registrado, pero la mora la calcula y la aplica el Módulo 3

3. **Scenario**: El recurso volvió dañado
   - **Given** un Estudiante devuelve el "Videobeam 12" y en el mostrador se detecta un daño
   - **When** el Módulo 3 reporta el check-out
   - **Then** el sistema cierra el préstamo igual que en una devolución normal. El daño no pasa por este módulo: el Módulo 3 se lo reporta al Módulo 1, y desde ese momento `Consultar disponibilidad de los recursos` muestra el videobeam como `EN_MANTENIMIENTO`

4. **Scenario**: El check-out no llega
   - **Given** la hora pactada de devolución ya pasó y el Módulo 3 no ha reportado ningún check-out del "Microscopio 07"
   - **When** se consulta el estado del préstamo
   - **Then** el préstamo figura como pendiente de devolución y sigue ocupando su periodo, sin que el activo esté disponible para otros

### Edge Cases

- **Check-out exactamente en la hora pactada**: devolver a las 22:00 en punto —la hora de vencimiento que fija `Reservar recursos` FR-013— tiene que contarse siempre igual. El criterio lo aplica el Módulo 3, pero el dato que registramos debe permitir distinguirlo sin ambigüedad.
- **Check-out repetido sobre el mismo préstamo**: recibirlo dos veces no puede cerrar dos veces el préstamo ni liberar dos veces el cupo.
- **Check-out sobre un préstamo que ya no está abierto**: si la reserva fue cancelada antes de la entrega, o si el reporte llega sobre un préstamo ya cerrado, el sistema DEBE rechazarlo explicando por qué en vez de crear un cierre nuevo.
- **El Módulo 3 no responde o el aviso se pierde**: no hay nada que reintentar de nuestro lado, porque el mensaje entra, no sale. La consecuencia es que el préstamo sigue abierto y aparece en la lista de pendientes de devolución hasta que el check-out llegue. El sistema no lo cierra por su cuenta.
- **Devolución que nunca llega (umbral de pérdida de 7 días)**: Si transcurren 7 días calendario desde la fecha y hora pactadas sin que llegue ningún check-out:
  1. El préstamo se da por vencido de forma definitiva y se cierra con el estado `NO_DEVUELTO_PERDIDO`.
  2. El recurso se retira permanentemente de la oferta de reservas (baja lógica, no eliminación de la base de datos) y se le notifica al Módulo 1 para que actualice su estado patrimonial a `DADO_DE_BAJA`.
  3. Se escala el caso al Módulo 3 con el expediente completo (persona, recurso, placa de inventario y días de mora) para que aplique la sanción disciplinaria correspondiente e inicie el proceso administrativo de cobro por reposición.
- **Check-out el mismo día de la entrega**: si alguien recoge un activo y lo devuelve sin haberlo llegado a usar, el check-out se procesa igual y no cuenta como retraso; el préstamo se cierra ahí y el cupo se libera.
- **Espacios físicos (salones, auditorios, salas de estudio)**: no tienen check-out, porque no hay nada que devolver. Su liberación ocurre sola al cumplirse la hora de fin de la franja, a través de `Actualizar estado de los recursos`. Este caso de uso aplica en exclusiva a activos en préstamo físico.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE aceptar del Módulo 3 el check-out de un préstamo, con la fecha y hora reales en que el recurso volvió.
- **FR-002**: El sistema DEBE guardar, junto a cada préstamo, la fecha y hora pactadas de devolución y las reales recibidas en el check-out.
- **FR-003**: El sistema DEBE cerrar el préstamo al procesar el check-out y liberar el cupo de préstamos vigentes de su titular.
- **FR-004**: El sistema DEBE ejecutar `Actualizar estado de los recursos` con cada check-out procesado, para desocupar el periodo del préstamo en el calendario del Módulo 2. NO DEBE fijar el estado físico del recurso: `DISPONIBLE` o `EN_MANTENIMIENTO` los decide el Módulo 1 con lo que le reporta el Módulo 3.
- **FR-005**: El sistema NO DEBE calcular ni aplicar la sanción por mora ni el cobro por daño; eso corresponde al Módulo 3.
- **FR-006**: El sistema NO DEBE cerrar un préstamo por su cuenta. Sin check-out recibido, el préstamo sigue abierto y ocupando su periodo, incluso después de vencido el plazo.
- **FR-007**: Un mismo check-out NO DEBE procesarse más de una vez, ni producir un segundo cierre del préstamo.
- **FR-008**: El sistema DEBE rechazar el check-out que llegue sobre un préstamo que no está abierto, explicando el motivo, y DEBE confirmarle al Módulo 3 el resultado en ambos casos.
- **FR-009**: El sistema DEBE mostrar como pendientes los préstamos cuya hora pactada ya pasó y sobre los que no ha llegado ningún check-out.
- **FR-010**: El sistema DEBE guardar la fecha y hora en que recibió cada check-out, además de las que el check-out reporta, para poder auditar la diferencia entre lo que pasó y cuándo nos enteramos.
- **FR-011**: El sistema NO DEBE recibir ni guardar la descripción de los daños del recurso; esa información va del Módulo 3 al Módulo 1.

### Key Entities

- **Préstamo**: entrega de un recurso a una persona por un tiempo acordado. Atributos: persona, recurso, fecha y hora de entrega, fecha y hora pactadas de devolución, fecha y hora reales de devolución, estado. La fecha pactada no se decide aquí: la calcula `Reservar recursos` al confirmar el préstamo (UC2 FR-012 a FR-014), y una renovación la desplaza una única vez (UC2 FR-016), así que la vigente es la que haya quedado tras ella.
- **CheckOut**: aviso que llega del Módulo 3 diciendo que el recurso volvió. Atributos: préstamo de origen, fecha y hora reales de la devolución, fecha y hora en que se recibió el aviso, resultado de su procesamiento.
- **Recurso**: el activo prestado cuyo periodo queda libre.
- **Usuario**: la persona responsable del préstamo, que recupera un cupo al cerrarse.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de los check-out recibidos cierran su préstamo y liberan su periodo en el calendario.
- **SC-002**: Un activo con check-out procesado vuelve a poder reservarse en menos de 5 segundos, salvo que el Módulo 1 lo tenga en `EN_MANTENIMIENTO`.
- **SC-003**: Cero préstamos cerrados dos veces por un check-out repetido.
- **SC-004**: Cero préstamos cerrados sin que haya llegado su check-out.
- **SC-005**: Cero préstamos vencidos sin check-out que no aparezcan en la lista de pendientes de devolución.
