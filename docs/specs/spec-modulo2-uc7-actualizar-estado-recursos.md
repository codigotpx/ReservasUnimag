# Feature Specification: Actualizar estado de los recursos

**Created**: 2026-09-03
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Actualizar estado de los recursos` (paso que ocurre siempre por dentro —`<<include>>`— de `Reservar recursos`, `Cancelar reserva`, `Recibir reporte de no asistencia` y `Recibir check-out`)
**Prioridad global**: P1

## Contexto

Es el paso que mantiene el calendario del Módulo 2 diciendo la verdad. Cada vez que algo le pasa a una reserva —nace, se cancela, empieza, termina— cambia el tiempo que el recurso tenía comprometido, y ese cambio hay que escribirlo donde los demás casos de uso lo leen.

Con el reparto de estados de [spec-modulo2.md](./spec-modulo2.md), la ocupación de una franja o de un periodo de préstamo es del Módulo 2, y el estado físico del recurso es del Módulo 1. De ese estado físico hay uno que nace aquí: el `EN_USO`. Que un recurso empiece a usarse es un hecho de la reserva —de una persona que llega a lo que apartó, o de una clase del semestre que empieza a su hora—, así que es el Módulo 2 el que se lo comunica al Módulo 1 para que lo refleje en su inventario. La entrada a `EN_MANTENIMIENTO` la constata el Módulo 3 en el sitio y se la reporta directamente, igual que ya hace con los daños, sin pasar por nosotros.

El aviso lleva el tramo de uso, y con eso el Módulo 1 sabe también cuándo termina: un **espacio** deja de estar `EN_USO` al acabarse el tramo, sin que haya que avisarle otra vez. Un **activo** no lleva fin, porque el suyo no es una hora sino la devolución, y esa se la reporta el Módulo 3.

Nadie lo pide por separado: ocurre por dentro de `Reservar recursos`, `Cancelar reserva`, `Recibir reporte de no asistencia` y `Recibir check-out`, y ninguno de los cuatro puede saltárselo. Si este paso falla, el resto del sistema empieza a mostrar salones libres que en realidad están ocupados, que es exactamente el problema que el proyecto quiere resolver.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Módulo 1 | Secundario | Recibe el inicio de uso de cada recurso para pasarlo a `EN_USO`; es el dueño del estado físico del inventario. |
| Estudiante / Monitor | Indirectos | No lo ejecutan; se benefician de que la información que ven esté al día. |

**Casos de uso relacionados**

- `Reservar recursos` — lo ejecuta siempre al confirmar una reserva; ver [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md)
- `Cancelar reserva` — lo ejecuta siempre al liberar una franja; ver [spec-modulo2-uc4-cancelar-reserva.md](./spec-modulo2-uc4-cancelar-reserva.md)
- `Recibir reporte de no asistencia` — lo ejecuta siempre al registrar la ausencia que reporta el Módulo 3, para liberar el recurso; ver [spec-modulo2-uc9-recibir-reporte-no-asistencia.md](./spec-modulo2-uc9-recibir-reporte-no-asistencia.md)
- `Recibir check-out` — lo ejecuta siempre al cerrar un préstamo, para desocupar su periodo; ver [spec-modulo2-uc12-recibir-check-out.md](./spec-modulo2-uc12-recibir-check-out.md)
- `Importar horarios semestrales` — sus bloqueos académicos llegan aquí a través de `Reservar recursos`, que Importar incluye; ver [spec-modulo2-uc3-importar-horarios-semestrales.md](./spec-modulo2-uc3-importar-horarios-semestrales.md)
- `Consultar disponibilidad de los recursos` — lee lo que este caso de uso deja escrito; ver [spec-modulo2-uc8-consultar-disponibilidad-recursos.md](./spec-modulo2-uc8-consultar-disponibilidad-recursos.md)

**Estados del recurso**

[gestionunimag.md](../gestionunimag.md) lista cinco estados, pero no todos son del mismo módulo. Este caso de uso escribe los dos de ocupación, le comunica al Módulo 1 el paso a `EN_USO`, y los otros dos solo los lee:

| Estado | Dueño | Qué significa | Qué hace este caso de uso con él |
|---|---|---|---|
| `DISPONIBLE` | Módulo 1 | Libre, se puede apartar. | Lo consulta; no lo escribe ni lo envía. |
| `RESERVADO` | Módulo 2 | Apartado por alguien, pero todavía nadie lo está usando. | Lo pone y lo quita en el calendario propio. |
| `BLOQUEO_ACADEMICO` | Módulo 2 | Guardado para una clase del semestre. | Lo pone y lo quita en el calendario propio. |
| `EN_USO` | Módulo 1 | El recurso se está usando ahora mismo. | Se lo comunica al Módulo 1 cuando el uso empieza, con el tramo que durará. |
| `EN_MANTENIMIENTO` | Módulo 1 | En reparación o fuera de servicio. | Lo consulta y lo respeta; nunca lo pone ni lo quita. |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Mantener al día la ocupación de cada recurso (Priority: P1)

Como sistema, quiero actualizar la ocupación de un recurso cada vez que su situación cambia, y avisarle al Módulo 1 cuando su uso empieza, para que cualquiera que consulte —un estudiante, un monitor o la Dirección de Programa— vea siempre la realidad y no una foto vieja.

**Why this priority**: Es P1 porque va pegado a `Reservar recursos`, que también es P1. Una reserva que se confirma pero no ocupa la franja es peor que no tener reservas: dos personas pueden apartar el mismo salón creyendo ambas que está libre.

**Independent Test**: Se puede probar solo, sin necesidad de reportes ni notificaciones: se provoca cada situación (confirmar, empezar a usar, cancelar, terminar la franja) y se comprueba que el recurso quedó ocupado o libre en el tiempo correcto y que el Módulo 1 recibió el aviso cuando el uso empezó.

**Acceptance Scenarios**:

1. **Scenario**: Una reserva se confirma
   - **Given** la "Sala de Estudio 3" aparece `DISPONIBLE` el 2026-09-01 de 10:00 a 12:00
   - **When** un Estudiante confirma una reserva sobre esa franja
   - **Then** el recurso queda `RESERVADO` únicamente en esa franja, en las demás sigue apareciendo `DISPONIBLE`, y al Módulo 1 no se le envía nada: esa ocupación vive en la base del Módulo 2

2. **Scenario**: La persona llega y empieza a usar el recurso
   - **Given** la "Sala de Estudio 3" está `RESERVADO` para las 10:00
   - **When** llega la hora y queda registrado que la persona se presentó
   - **Then** el sistema se lo comunica al Módulo 1 para que el recurso pase a `EN_USO`, y la franja sigue ocupada en el calendario del Módulo 2 hasta su hora de fin

3. **Scenario**: La reserva se cancela
   - **Given** el "Auditorio Menor" está `RESERVADO` el 2026-09-05 de 14:00 a 16:00
   - **When** el titular cancela esa reserva
   - **Then** esa franja queda libre y el recurso vuelve a aparecer `DISPONIBLE`, sin enviarle nada al Módulo 1, que nunca supo de esa reserva

4. **Scenario**: Termina la franja reservada
   - **Given** el "Laboratorio de Redes" está ocupado hasta las 16:00 y el Módulo 1 lo tiene `EN_USO`
   - **When** llegan las 16:00 y no hay otra reserva encima
   - **Then** la franja queda libre sin que nadie tenga que hacer nada, y el Módulo 1 lo deja de tener `EN_USO` porque esa era la hora de fin que le llegó con el aviso; no se le envía nada nuevo

5. **Scenario**: Se devuelve un activo prestado
   - **Given** el "Libro de Cálculo I" está prestado desde la recogida, el Módulo 1 lo tiene `EN_USO`, y su plazo venció ayer sin que nadie lo devolviera
   - **When** la persona lo devuelve hoy y el Módulo 3 nos manda el check-out
   - **Then** el periodo de préstamo queda desocupado en ese momento y el activo se puede volver a reservar; antes de ese check-out, y aunque el plazo estuviera vencido, el periodo nunca dejó de estar ocupado y el Módulo 1 lo mantuvo `EN_USO`, porque su aviso no llevaba hora de fin

6. **Scenario**: Entra una clase del semestre
   - **Given** se importó el horario y el "Salón 201" tiene clase los martes de 08:00 a 10:00
   - **When** se aplica esa carga
   - **Then** el recurso queda en `BLOQUEO_ACADEMICO` en esas franjas, dentro de la base del Módulo 2, y todavía no se le avisa nada al Módulo 1: la clase no ha empezado

7. **Scenario**: Empieza la clase
   - **Given** el "Salón 201" tiene un `BLOQUEO_ACADEMICO` el martes de 08:00 a 10:00
   - **When** llegan las 08:00
   - **Then** el uso empieza sin que nadie lo registre, y el sistema se lo comunica al Módulo 1 con el tramo de 08:00 a 10:00, para que lo tenga `EN_USO` durante la clase y libre después; al estudiante se le sigue mostrando `BLOQUEO_ACADEMICO`, que manda sobre `EN_USO`

### Edge Cases

- **Recurso en mantenimiento**: si el Módulo 1 reporta un recurso `EN_MANTENIMIENTO`, ningún cambio derivado de una reserva lo saca de ahí; este caso de uso no escribe ese estado, solo lo respeta, y el mantenimiento manda sobre todo lo demás.
- **Uso que empieza sobre un recurso que ya no está**: si al ir a comunicar el inicio de uso el Módulo 1 reporta el recurso `EN_MANTENIMIENTO`, no se le envía el `EN_USO`; la situación se trata por el camino de la reserva cancelada por recurso no disponible (UC4 FR-010).
- **Clase que se cancela antes de su hora**: si el bloqueo académico se cancela o se desplaza antes de que la clase empiece, el uso nunca empieza y no sale ningún aviso. Lo que ya se avisó no se desdice: si la clase empezó y después se cancela el resto del semestre, el tramo de ese día se cumple igual.
- **Dos cambios sobre la misma franja casi al mismo tiempo**: debe quedar una única ocupación final coherente, nunca una franja que aparezca libre y `RESERVADO` a la vez.
- **El Módulo 1 no responde**: la reserva, la cancelación o el registro de presentación se completan igual; el aviso de inicio de uso queda pendiente y se vuelve a intentar hasta que llegue, sin dejar al inventario desactualizado en silencio.
- **Aviso repetido**: si un mismo cambio se reintenta, el recurso no debe terminar contado dos veces ni ocupado dos veces, y el Módulo 1 no debe recibir dos inicios de uso para el mismo tramo.
- **Franja que ya pasó**: un cambio que llega tarde, referido a una franja que ya terminó, no debe reabrir ni volver a ocupar el recurso; se registra y se descarta.
- **Préstamo vencido y no devuelto**: que se cumpla la fecha de vencimiento no desocupa nada. El periodo sigue ocupado y el Módulo 1 sigue teniendo el activo `EN_USO`; quien calcula la mora es el Módulo 3, y el único hecho que cierra el préstamo es el check-out.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE actualizar la ocupación del recurso cada vez que una reserva se confirma, se cancela, empieza a usarse o termina.
- **FR-002**: El cambio DEBE afectar únicamente al tiempo involucrado, sin alterar la disponibilidad del recurso fuera de él: la franja horaria cuando se trata de un espacio, y el periodo de préstamo completo cuando se trata de un activo, que queda ocupado de principio a fin sin liberarse por las noches.
- **FR-003**: El sistema DEBE moverse solo entre los cinco estados del inventario —`DISPONIBLE`, `RESERVADO`, `BLOQUEO_ACADEMICO`, `EN_USO` y `EN_MANTENIMIENTO`— respetando su reparto: escribe `RESERVADO` y `BLOQUEO_ACADEMICO` en su propia base, y los otros tres son del Módulo 1.
- **FR-004**: El sistema DEBE comunicarle al Módulo 1 el inicio de uso de un recurso, indicando el recurso, el tramo de uso y el motivo, para que el Módulo 1 lo pase a `EN_USO`. El uso empieza de dos maneras: cuando queda registrado que la persona se presentó a lo que apartó, y cuando llega la hora de inicio de un bloqueo académico, que no necesita que nadie lo registre porque la clase está en el horario.
- **FR-004a**: El tramo que lleva el aviso DEBE decirle al Módulo 1 hasta cuándo dura el uso cuando se trata de un **espacio**, que es la hora de fin de la franja, y NO DEBE llevar fin cuando se trata de un **activo**, cuyo uso termina con la devolución que le reporta el Módulo 3. Así el Módulo 1 libera el espacio por sí solo y mantiene el activo fuera hasta que vuelva.
- **FR-005**: El sistema NO DEBE sacar de `EN_MANTENIMIENTO` a un recurso por efecto de una reserva o una cancelación, ni comunicarle al Módulo 1 el inicio de uso de un recurso que esté en ese estado.
- **FR-006**: Un recurso NO DEBE poder quedar con dos ocupaciones distintas para el mismo momento, ni por franja ni dentro de un periodo de préstamo.
- **FR-007**: Si el Módulo 1 no está disponible, la operación de negocio DEBE completarse igualmente y el aviso de inicio de uso DEBE reintentarse hasta entregarse.
- **FR-008**: Repetir el mismo aviso NO DEBE producir un segundo cambio de ocupación ni un segundo paso a `EN_USO`.
- **FR-009**: El sistema DEBE guardar un registro de cada cambio con el recurso, la franja, la ocupación anterior, la nueva, el motivo, la fecha y hora, y —cuando el cambio lleva aviso— el resultado del envío al Módulo 1.
- **FR-010**: Al terminar la franja, la ocupación de un **espacio** DEBE liberarse por sí sola, salvo que exista otra reserva o un bloqueo académico encima.
- **FR-011**: El periodo de préstamo de un **activo** NO DEBE liberarse por el paso del tiempo. Sigue ocupado hasta que llegue su check-out desde el Módulo 3 (`Recibir check-out`), incluso después de vencido el plazo: mientras el recurso no vuelva físicamente, nadie más puede pedirlo. Es la diferencia de fondo con un espacio, que se desocupa solo cuando pasa la hora.
- **FR-012**: El sistema NO DEBE enviarle al Módulo 1 ningún otro cambio de estado: `RESERVADO` y `BLOQUEO_ACADEMICO` no salen de la base del Módulo 2; el regreso a `DISPONIBLE` de un espacio lo deduce el Módulo 1 del tramo que ya recibió, el de un activo se lo reporta el Módulo 3 con la devolución, y la entrada a `EN_MANTENIMIENTO` también se la reporta el Módulo 3.

### Key Entities

- **Recurso**: espacio o activo del inventario; lo que se ocupa y se libera.
- **FranjaHoraria**: día con hora de inicio y hora de fin; la ocupación de un **espacio** se guarda por franja, no para el recurso entero.
- **PeriodoDePrestamo**: el tramo continuo en que un **activo** está prestado. No se guarda por franjas: el activo queda ocupado de corrido durante todo el periodo y solo se libera con el check-out.
- **EstadoDelRecurso**: situación del recurso en una franja concreta, siempre uno de los cinco valores del inventario, sea de los que guarda el Módulo 2 o de los que consulta al Módulo 1.
- **CambioDeEstado**: registro de un cambio ocurrido. Atributos: recurso, franja, ocupación anterior, ocupación nueva, motivo, fecha y hora, y resultado del aviso al Módulo 1 cuando el cambio lo lleva.
- **Reserva**: origen de la mayoría de los cambios.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La ocupación que muestra el sistema coincide con la situación real del recurso en el 100 % de las franjas revisadas durante una auditoría.
- **SC-002**: Un recurso liberado vuelve a aparecer como disponible en menos de 5 segundos.
- **SC-003**: Cero recursos con dos ocupaciones distintas para la misma franja bajo pruebas de uso simultáneo.
- **SC-004**: Cero avisos de inicio de uso perdidos hacia el Módulo 1 ante una caída de hasta 30 minutos.
- **SC-005**: Ninguna reserva ni cancelación falla por culpa de un error al avisar al Módulo 1.
