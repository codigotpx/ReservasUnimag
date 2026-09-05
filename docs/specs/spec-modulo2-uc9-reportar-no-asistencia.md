# Feature Specification: Reportar no asistencia

**Created**: 2026-09-03
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Reportar no asistencia` — continuación opcional de `Reservar recursos` (`<<extend>>`), que informa al Módulo 3
**Prioridad global**: P2

## Contexto

Cuando alguien aparta un salón y no aparece, ese espacio queda muerto: nadie más pudo usarlo porque figuraba ocupado. Este caso de uso se encarga de dejar constancia de esa ausencia, liberar el recurso y avisarle al Módulo 3, que es el que lleva la matriz de cumplimiento y aplica las sanciones.

**La ausencia no la descubre el sistema solo: nos la reporta el Módulo 3.** Es él quien lleva el control de uso y comprueba en el sitio si la persona apareció; cuando constata que no llegó, nos lo informa, y es ese reporte —no un reloj— lo que dispara todo lo demás. Tiene sentido, porque desde una base de datos nadie puede saber si alguien entró a una sala o fue a recoger un libro.

El criterio sí está fijado: **10 minutos** sin que la persona aparezca, contados desde el inicio de la franja cuando se apartó un espacio, y desde la hora de recogida acordada cuando lo apartado es un objeto. Pasado ese plazo el Módulo 3 puede reportar la ausencia; mientras no llegue ese reporte, el recurso sigue apartado a nombre de su titular.

La división de trabajo queda así: el Módulo 3 constata la ausencia y aplica la sanción; el Módulo 2 recibe el aviso, libera el recurso y deja la constancia.

Sobre el nombre del caso de uso: en el diagrama es `Reportar no asistencia` porque describe el hecho que entra al módulo, no la dirección del mensaje. Lo que se reporta viene del Módulo 3 hacia nosotros. Según [gestionunimag.md](../gestionunimag.md), la consecuencia prevista para una no asistencia a un espacio es el bloqueo de reservas de espacios por una semana, pero quien la aplica es el Módulo 3, no este módulo.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Módulo 3 | Secundario | Constata que el titular no se presentó y se lo reporta al Módulo 2; después decide y aplica la sanción. Sin ese reporte no hay ausencia. |
| Estudiante | Indirecto | Es quien incurre en la ausencia; recibe la consecuencia por parte del Módulo 3. |

**Casos de uso relacionados**

- `Reservar recursos` — **caso base al que extiende** (`<<extend>>`): solo hay ausencia si antes hubo una reserva. Define además el plazo de 10 minutos sobre el que se mide; ver [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md)
- `Cancelar reserva` — cancelar a tiempo es justamente la forma de evitar este reporte; ver [spec-modulo2-uc4-cancelar-reserva.md](./spec-modulo2-uc4-cancelar-reserva.md)
- `Actualizar estado de los recursos` — libera el recurso en cuanto se confirma la ausencia; ver [spec-modulo2-uc7-actualizar-estado-recursos.md](./spec-modulo2-uc7-actualizar-estado-recursos.md)
- `Reportar cancelación de reserva` — el cierre contrario: la persona sí avisó, y por eso no se le reporta ninguna ausencia; ver [spec-modulo2-uc11-reportar-cancelacion-reserva.md](./spec-modulo2-uc11-reportar-cancelacion-reserva.md)
- `Consultar reportes` — el camino de vuelta: las ausencias que aquí se reportan son parte de lo que el Módulo 3 devuelve después como sanción; ver [spec-modulo2-uc6-consultar-reportes.md](./spec-modulo2-uc6-consultar-reportes.md)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Dejar constancia de quien no se presentó (Priority: P2)

Como sistema, quiero recibir del Módulo 3 el aviso de que una persona no se presentó a usar lo que apartó, para liberar ese recurso de inmediato y dejar constancia de la ausencia, y así la universidad pueda desincentivar las reservas fantasma sin que el espacio siga bloqueado inútilmente.

**Why this priority**: Es P2 porque el sistema funciona sin ella —se puede consultar y reservar igual—, pero sin este reporte la ocupación real se degrada: las personas apartan por si acaso, no van, y nadie asume ninguna consecuencia. Va junto a `Cancelar reserva`, que es la otra mitad de la misma idea.

**Independent Test**: Se puede probar sola creando una reserva, dejando pasar el plazo y simulando el reporte de ausencia del Módulo 3; se verifica que quedó la constancia con la persona, el recurso y el tiempo apartado correctos, y que el recurso volvió a estar disponible. No necesita que las sanciones estén implementadas.

**Acceptance Scenarios**:

1. **Scenario**: El Módulo 3 reporta que la persona no se presentó
   - **Given** un Estudiante tiene reservada la "Sala de Estudio 3" el 2026-09-01 a las 10:00, no canceló y a las 10:10 no ha aparecido
   - **When** el Módulo 3 le reporta al sistema esa no presentación
   - **Then** el sistema deja constancia de la ausencia con la persona, el recurso y el tiempo que tenía apartado, libera el recurso y confirma al Módulo 3 que lo procesó

2. **Scenario**: Nadie reporta la ausencia
   - **Given** un Estudiante no se presentó a su reserva de las 10:00 y no llegó ningún reporte del Módulo 3
   - **When** pasan las horas
   - **Then** el sistema no genera ninguna ausencia por su cuenta ni libera el recurso: la reserva sigue a nombre de su titular hasta que termine la franja, y solo entonces el recurso vuelve a estar disponible como cualquier reserva que llegó a su fin

3. **Scenario**: La persona sí llega a tiempo
   - **Given** un Estudiante tiene reservada la "Sala de Estudio 3" a las 10:00
   - **When** llega a las 10:07 y queda registrado que sí se presentó
   - **Then** no se genera ninguna ausencia y no se reporta nada al Módulo 3

4. **Scenario**: La persona canceló antes
   - **Given** un Estudiante canceló su reserva media hora antes de la franja
   - **When** llega la hora de la franja y nadie usa el recurso
   - **Then** no se genera ninguna ausencia, porque el recurso ya se había liberado a tiempo

5. **Scenario**: La reserva la canceló el sistema por una clase
   - **Given** una reserva estudiantil fue cancelada automáticamente porque entró una actividad docente
   - **When** llega la hora de esa franja
   - **Then** no se genera ninguna ausencia ni se reporta nada, porque la persona no tuvo culpa

### Edge Cases

- **Llega justo en el minuto 10**: el criterio del borde debe ser explícito y siempre el mismo, para que ambos módulos cuenten igual a partir de qué instante cabe reportar; una persona que se presenta exactamente al cumplirse el plazo no puede quedar reportada unas veces sí y otras no.
- **Llega después de que se reportó la ausencia**: si aparece cuando el reporte ya entró, la ausencia no se deshace sola; el recurso ya volvió a estar disponible para otros y puede que alguien más lo haya tomado.
- **Reporte equivocado**: detrás del aviso del Módulo 3 hay una comprobación humana, así que cabe el error. El Módulo 3 debe poder anular un reporte que envió, y el Módulo 2 debe deshacer la constancia; anularlo no devuelve automáticamente el recurso a su titular si otra persona ya lo tomó.
- **Reporte que llega tarde**: si el aviso entra cuando la franja ya terminó, la constancia se registra igual para el historial, pero no hay nada que liberar: el recurso ya se había desocupado solo al acabar su franja.
- **El Módulo 3 no responde**: la ausencia se registra igualmente y el recurso se libera igual; el reporte queda pendiente y se reintenta hasta entregarse.
- **Reporte repetido**: una misma ausencia no puede reportarse dos veces, para que la persona no reciba dos sanciones por el mismo hecho.
- **Recurso caído durante la franja**: si el recurso pasó a mantenimiento y por eso la persona no pudo usarlo, no debe contarse como ausencia suya.
- **Reserva de varias horas**: la ausencia se mide desde el inicio de la franja, no desde cada hora dentro de ella; una reserva de 10:00 a 14:00 genera como máximo una ausencia.
- **Objeto que nadie recoge**: quien aparta un libro para el jueves a las 14:30 y no aparece genera una sola ausencia a las 14:40, igual que con un salón, y el libro queda libre para los demás desde ese instante. No se espera al vencimiento del préstamo para darlo por ausente.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE registrar una ausencia cuando el **Módulo 3** le reporte que el titular no se presentó. El sistema NO DEBE generar ausencias por su cuenta: sin ese reporte no hay ausencia.
- **FR-002**: El sistema DEBE dejar constancia de cada ausencia recibida indicando la persona, el recurso, el tiempo que tenía apartado —la franja si es un espacio, la hora de recogida y el vencimiento previsto si es un objeto— y la fecha y hora del reporte, y DEBE confirmarle al Módulo 3 que la procesó.
- **FR-003**: El sistema NO DEBE aplicar la sanción; esa decisión y su aplicación corresponden al Módulo 3.
- **FR-004**: El sistema DEBE liberar el recurso al registrar la ausencia reportada: un **espacio** queda disponible para el resto de su franja, y en un **objeto** se cancela el periodo de préstamo completo, de modo que vuelve a estar disponible desde ese momento y no solo durante unos minutos.
- **FR-005**: El sistema NO DEBE generar ausencia cuando la reserva fue cancelada a tiempo por su titular.
- **FR-006**: El sistema NO DEBE generar ausencia cuando la reserva fue cancelada automáticamente por prioridad académica.
- **FR-007**: Una misma reserva NO DEBE generar más de una ausencia.
- **FR-008**: Si el Módulo 3 no está disponible no llegan reportes, y el Módulo 2 NO DEBE suplir esa función declarando ausencias por su cuenta: los recursos siguen apartados hasta que termine su franja. Cuando el Módulo 3 se restablezca, el sistema DEBE aceptar los reportes atrasados y registrarlos, aunque ya no quede nada que liberar.
- **FR-009**: El sistema DEBE guardar el historial de ausencias para que alimente los reportes de cumplimiento.
- **FR-010**: El sistema DEBE informar a la persona que se le registró una ausencia y por qué reserva.
- **FR-011**: El sistema DEBE aceptar ese reporte únicamente a partir de los **10 minutos** siguientes al inicio de la franja, si lo apartado es un espacio, o a la hora de recogida acordada, si es un objeto. Antes de ese plazo la persona todavía está a tiempo de llegar, y un reporte anticipado DEBE rechazarse indicando desde cuándo se admite.
- **FR-012**: El sistema DEBE permitir que el Módulo 3 anule un reporte de ausencia enviado por error, dejando constancia de la anulación y de su fecha y hora. Anular no le devuelve el recurso al titular si otra persona ya lo tomó.

### Key Entities

- **Ausencia**: constancia de que alguien no usó lo que apartó. Atributos: persona, recurso, el tiempo apartado (franja de un espacio, u hora de recogida y vencimiento previsto de un objeto), reserva de origen, fecha y hora del reporte recibido del Módulo 3, y si fue anulado.
- **Reserva**: el apartado que quedó sin usar.
- **Recurso**: el espacio o equipo que quedó bloqueado sin necesidad.
- **Usuario**: la persona a la que se le anota la ausencia.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las ausencias que reporta el Módulo 3 quedan registradas y con el recurso liberado dentro de los 5 minutos siguientes a la llegada del reporte. El tiempo se mide desde el reporte, no desde el inicio de la franja, porque el Módulo 2 no puede responder por lo que tarde el otro módulo en avisar.
- **SC-002**: Cero ausencias reportadas dos veces sobre la misma reserva.
- **SC-003**: Cero ausencias atribuidas a personas cuya reserva fue cancelada por prioridad académica o por mantenimiento del recurso.
- **SC-004**: Reducción del 40 % en las franjas apartadas y no usadas durante el primer semestre de operación.
- **SC-005**: Cero reportes perdidos ante una caída del Módulo 3 de hasta 30 minutos.
