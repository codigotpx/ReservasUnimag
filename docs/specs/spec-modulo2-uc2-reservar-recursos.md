# Feature Specification: Reservar recursos

**Created**: 2026-08-24
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Reservar recursos` (continuación opcional de `Consultar recursos`: solo ocurre si alguien, después de mirar la lista, decide apartar algo. De esta reserva cuelgan a su vez `Cancelar reserva` y `Reportar no asistencia`, que tampoco tendrían sentido sin ella)
**Prioridad global**: P1

## Contexto

Es lo que el Módulo 2 existe para hacer: que el propio estudiante aparte un recurso, sin pedírselo a nadie. Se llega hasta aquí desde la lista de `Consultar recursos`: la persona mira qué hay libre y, si decide quedarse con alguno, sigue hacia la reserva; también puede quedarse solo mirando y no reservar nada.

Antes de confirmar, el sistema revisa las reglas que deciden si puede apartarlo —si tiene alguna sanción encima, si ya llegó a su tope de préstamos y si el recurso sigue libre en esa franja—. Cuando la respuesta es no, nunca se dice un simple "no se pudo": cada negativa viene con un código del diccionario de errores que dice exactamente cuál fue la razón.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Estudiante | Primario | Solicita el apartado de un recurso para una franja horaria. |
| Monitor | Primario | Especialización de Estudiante: hereda esta capacidad. |
| Módulo 1 | Secundario | Aporta el estado real del recurso y recibe el estado actualizado tras la reserva. |
| Módulo 3 | Secundario | Provee las sanciones vigentes de la persona y recibe el reporte cuando no se presenta a usar lo que apartó. |

**Casos de uso relacionados**

- `Consultar recursos` — ver [spec-modulo2-uc1-consultar-recursos.md](./spec-modulo2-uc1-consultar-recursos.md)
- `Actualizar estado de los recursos` — **paso que ocurre siempre por dentro**: al confirmar la reserva, el recurso cambia de estado y el Módulo 1 se entera; no se puede saltar; ver [spec-modulo2-uc7-actualizar-estado-recursos.md](./spec-modulo2-uc7-actualizar-estado-recursos.md)
- `Consultar disponibilidad de los recursos` — la comprobación que se hace en el último momento, antes de confirmar; ver [spec-modulo2-uc8-consultar-disponibilidad-recursos.md](./spec-modulo2-uc8-consultar-disponibilidad-recursos.md)
- `Consultar reportes` — de ahí sale la información de sanciones que sustenta la denegación `RES-003`; ver [spec-modulo2-uc6-consultar-reportes.md](./spec-modulo2-uc6-consultar-reportes.md)
- `Notificar estado de recursos al finalizar reserva` — le cuenta al Módulo 3 cómo terminó la reserva; ver [spec-modulo2-uc5-notificar-estado-recursos.md](./spec-modulo2-uc5-notificar-estado-recursos.md)
- `Importar horarios semestrales` — origen de las denegaciones `RES-001`; ver [spec-modulo2-uc3-importar-horarios-semestrales.md](./spec-modulo2-uc3-importar-horarios-semestrales.md)

**Diccionario de errores**

| Código | Causa |
|---|---|
| `RES-001` | Conflicto académico: el recurso está reservado para actividad docente. |
| `RES-002` | Límite máximo de préstamos vigentes alcanzado. |
| `RES-003` | Sanción activa sobre el usuario. |
| `RES-004` | El recurso acaba de ser tomado (conflicto de concurrencia). |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reservar recursos con validación de reglas (Priority: P1)

Como Estudiante, quiero apartar un recurso (aforos) disponible para una franja horaria concreta y en días (equipos), y que el sistema me explique con un mensaje claro y específico cuando no puedo hacerlo, para saber exactamente qué regla estoy incumpliendo (conflicto académico, límite de préstamos alcanzado o sanción activa).

**Why this priority**: Es la propuesta de valor central del módulo. Junto con `Consultar recursos` constituye el MVP mínimo demostrable: consultar y apartar. El "Diccionario de Errores" es parte inseparable de esta historia porque una denegación sin causa explicada genera reprocesos y tickets de soporte.

**Independent Test**: Se puede probar de forma independiente ejecutando el flujo de reserva sobre un recurso disponible y verificando la persistencia de la reserva, y luego forzando cada una de las tres condiciones de denegación para verificar que se retorna el código de error correspondiente. No requiere reportería ni notificaciones.

**Acceptance Scenarios**:

1. **Scenario**: Reserva exitosa de un espacio
   - **Given** el Estudiante no tiene sanciones activas, tiene 1 de 3 préstamos vigentes y la "Sala de Estudio 3" está `DISPONIBLE` el 2026-09-01 de 10:00 a 12:00
   - **When** solicita la reserva de ese recurso en esa franja
   - **Then** el sistema crea la reserva en estado `CONFIRMADA` y marca el recurso como `RESERVADO` para esa franja; cuando llega la hora de la franja y la persona se presenta, el recurso pasa a `EN_USO`. El sistema devuelve el identificador de la reserva

2. **Scenario**: Préstamo de un objeto
   - **Given** el "Libro de Cálculo I" está `DISPONIBLE` y su tipo tiene un plazo de 7 días hábiles
   - **When** el Estudiante pide recogerlo el martes 2026-09-01 a las 14:30
   - **Then** el sistema confirma el préstamo, calcula el vencimiento en el **jueves 2026-09-10 a las 22:00** —7 días hábiles saltando los fines de semana, al cierre de la jornada— y se lo informa antes de confirmar; el libro queda `EN_USO` sin interrupción hasta que se registre su devolución

3. **Scenario**: Denegación por conflicto académico (RES-001)
   - **Given** el "Laboratorio de Redes" quedó marcado como `BLOQUEO_ACADEMICO` el 2026-09-01 de 08:00 a 10:00
   - **When** el Estudiante intenta reservarlo en esa franja
   - **Then** el sistema rechaza la solicitud con el error `RES-001 — Conflicto académico: el recurso está reservado para actividad docente` y no crea ningún registro

4. **Scenario**: Denegación por límite de préstamos (RES-002)
   - **Given** el Estudiante ya tiene 3 reservas vigentes, que es el tope permitido
   - **When** intenta crear una cuarta reserva
   - **Then** el sistema rechaza la solicitud con el error `RES-002 — Límite máximo de préstamos alcanzado`, le informa que tiene 3 de 3 y le dice cuándo se libera la próxima

5. **Scenario**: Denegación por sanción activa (RES-003)
   - **Given** el Estudiante tiene una sanción vigente hasta el 2026-09-15
   - **When** intenta reservar cualquier recurso
   - **Then** el sistema consulta el reporte del usuario y rechaza la solicitud con el error `RES-003 — Sanción activa` e informa la fecha de finalización de la sanción

### Edge Cases

- **Solapamiento parcial**: ¿Qué ocurre si una solicitud de reserva de 09:30 a 10:30 cae parcialmente sobre un bloqueo académico de 08:00 a 10:00? Debe denegarse por `RES-001`.
- **Concurrencia sobre la misma franja**: si dos estudiantes solicitan simultáneamente el mismo recurso y franja, exactamente uno queda `CONFIRMADA` y el otro recibe `RES-004 — El recurso acaba de ser tomado`; dos reservas solapadas nunca pueden coexistir.
- **Sanción que inicia con reservas vigentes**: se le debe impedir crear nuevas reservas y además se le deben cancelar las que ya tenían.
- **Múltiples causas de denegación simultáneas**: se aplica el orden de validación definido (sanción, luego límite, luego conflicto/ocupación) y se devuelve un único código, el primero que falla.
- **Renovación pedida el mismo día del vencimiento**: se acepta mientras el vencimiento no haya pasado, es decir hasta las 22:00 de ese día. Un minuto después ya hay mora y solo cabe devolver.
- **Objeto que no se devuelve a tiempo**: llegado el vencimiento, el objeto no se libera solo; sigue `EN_USO` y en manos de quien lo tiene. La mora la calcula el Módulo 3 sobre el vencimiento, y el recurso vuelve a estar disponible únicamente cuando alguien registra la devolución.
- **Reserva más larga que el tope**: una solicitud de un espacio de 09:00 a 12:00 excede las 2 horas de FR-009 y se rechaza antes de mirar sanciones, cupo o disponibilidad; se le indica el tope y se le ofrece ajustar la franja, no un código `RES-00X`.
- **No presentación (no-show)**: el recurso pasa a `EN_USO` al llegar la hora de la franja, el módulo 3 reporta la no asistencia el recurso pasa a estar `DISPONIBLE` nuevamente.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Los usuarios DEBEN poder apartar un recurso disponible, y lo que se les pide depende de qué clase de recurso sea:
  - **Espacio** (salón, auditorio, laboratorio, sala de estudio): la persona indica **fecha, hora de inicio y hora de fin**, es decir una franja horaria completa, sujeta al tope de 2 horas de FR-009.
  - **Objeto** (libro, microscopio, kit de dibujo, videobeam): la persona indica solo **la fecha y hora en que va a recoger el recurso**. No elige cuándo lo devuelve: la fecha de devolución la calcula el sistema aplicando el plazo del tipo (FR-012), y se la informa antes de confirmar.
- **FR-002**: El sistema DEBE validar, antes de confirmar, y en este orden: sanción activa, límite de préstamos vigentes y conflicto académico u ocupación. La sanción se comprueba contra el Módulo 3 mediante `Consultar reportes`.
- **FR-003**: El sistema DEBE denegar la reserva con un código y mensaje específico del diccionario de errores: `RES-001` conflicto académico, `RES-002` límite máximo de préstamos alcanzado, `RES-003` sanción activa, `RES-004` recurso tomado concurrentemente.
- **FR-004**: El sistema DEBE garantizar que no existan dos reservas confirmadas solapadas sobre el mismo recurso, incluso bajo solicitudes concurrentes.
- **FR-005**: El sistema DEBE registrar toda denegación con su código, usuario, recurso y marca de tiempo, para alimentar la reportería.
- **FR-006**: El sistema DEBE revalidar la disponibilidad en el momento de confirmar, mediante `Consultar disponibilidad de los recursos`, sin confiar en el resultado de la consulta previa.
- **FR-007**: El sistema DEBE mantener registro de auditoría de toda creación de reserva, con autor y marca de tiempo.
- **FR-008**: El límite máximo de préstamos simultáneos DEBE ser de **3 reservas vigentes por persona**, y DEBE ser parametrizable para poder ajustarlo sin tocar el código. El mismo tope aplica a Estudiante y a Monitor, y es un cupo único: espacios y equipos cuentan juntos, son 3 en total y no 3 de cada tipo. Cuenta como vigente toda reserva `CONFIRMADA` cuya franja aún no ha terminado; las `CANCELADA`, `CANCELADA_POR_PRIORIDAD_ACADEMICA` y `FINALIZADA` liberan cupo de inmediato.
- **FR-009**: La duración máxima de una reserva sobre un **espacio** (salón, auditorio, laboratorio, sala de estudio) DEBE ser de **2 horas continuas**, y DEBE ser parametrizable. El sistema DEBE rechazar la solicitud cuya franja exceda ese tope antes de evaluar las reglas del diccionario de errores: no es una denegación por regla de negocio sino una franja mal formada, igual que una que cae fuera del horario de operación. Nada impide que la persona reserve dos franjas seguidas del mismo recurso, mientras le quede cupo de los 3 préstamos vigentes de FR-008.
- **FR-010**: El sistema DEBE liberar automáticamente el recurso y reportar la ausencia del titular al Módulo 3 si transcurren 10 minutos desde el inicio de la franja sin que se registre el uso; la sanción la decide y la aplica el Módulo 3.
- **FR-011**: Al confirmar una reserva, el sistema DEBE ejecutar siempre `Actualizar estado de los recursos`, sin que el usuario tenga que pedirlo y sin posibilidad de omitirlo.
- **FR-012**: El plazo máximo de préstamo de un **equipo** (mueble o recurso físico) DEBE depender de su tipo, porque no se presta igual un libro que un videobeam. El plazo NO lo define el Módulo 2: es el atributo **plazo máximo de préstamo** que el Módulo 1 fija al crear el recurso y entrega junto con el resto de la ficha, igual que el aforo o la ubicación. Se expresa en **días hábiles**, donde `0` significa que el equipo se devuelve el mismo día antes de las 22:00. El sistema DEBE tomarlo de ahí para calcular la fecha y hora pactadas de devolución, y DEBE rechazar la solicitud de préstamo cuya devolución prevista lo exceda. Los valores acordados con el Módulo 1 son: Libro 7 días hábiles, Kit de dibujo 3, Videobeam 1, Microscopio 0 (uso en el laboratorio). El Módulo 2 NO DEBE mantener una copia propia de esa tabla: si el Módulo 1 cambia un plazo o crea un tipo nuevo, el valor vigente es siempre el que venga en la ficha del recurso.
- **FR-013**: El vencimiento de un préstamo DEBE fijarse a las **22:00 del día en que se cumple el plazo**, es decir al cierre de la jornada, sin importar a qué hora se recogió el recurso. Así la persona dispone del día completo para devolver, la hora de vencimiento nunca cae fuera del horario de operación y el mensaje que se le muestra es directo: "devolver antes del jueves 10 a las 10:00 p. m.". De ese vencimiento depende el cálculo de mora del Módulo 3.
- **FR-014**: El plazo se cuenta en días **hábiles**, no naturales: los fines de semana y los días en que la universidad no abre no consumen plazo. Un préstamo hecho el viernes con plazo de 3 días hábiles vence el miércoles siguiente, no el lunes.
- **FR-015**: Mientras dura un préstamo, el objeto DEBE permanecer `EN_USO` de forma continua, sin trocearse en franjas: no se libera por las noches ni entre días, y no puede apartarlo nadie más en ningún momento de ese periodo. Solo vuelve a `DISPONIBLE` cuando se registra su devolución mediante `Reportar fecha y hora de entrega`, no al llegar la hora de vencimiento. Un objeto vencido y no devuelto sigue ocupado.
- **FR-016**: El titular de un préstamo DEBE poder **renovarlo una sola vez**, sin devolver el objeto ni volver a pedirlo. La renovación suma otra vez el plazo del tipo de recurso, contado **desde el vencimiento vigente** y no desde el día en que se renueva, para que nadie pierda días por renovar con antelación; el nuevo vencimiento se fija igual que el primero, a las 22:00 del día en que se cumple (FR-013). Una renovación NO consume cupo nuevo: sigue siendo el mismo préstamo y ocupa el mismo de los 3 de FR-008.
- **FR-017**: El sistema DEBE denegar la renovación cuando se dé cualquiera de estos casos: el préstamo ya fue renovado una vez; el préstamo ya venció, porque ahí lo que corre es la mora que calcula el Módulo 3 y lo que toca es devolver; el titular tiene una sanción activa; otra persona ya tiene ese mismo objeto apartado para recogerlo en una fecha que el nuevo vencimiento invadiría; o el objeto es de plazo `0`, de uso en sitio, que se devuelve el mismo día y no admite prórroga. En todos los casos DEBE explicarse cuál de estas razones aplica.

### Key Entities

- **Reserva**: apartado de un Recurso por un Usuario en una FranjaHoraria. Atributos: identificador, titular, recurso, franja, estado (`CONFIRMADA`, `CANCELADA`, `CANCELADA_POR_PRIORIDAD_ACADEMICA`, `FINALIZADA`), origen (estudiantil o académico).
- **Recurso**: espacio o equipo reservable. El espacio lleva su estado por franja; el objeto lo lleva por periodo de préstamo, que es continuo.
- **FranjaHoraria**: intervalo con fecha, hora de inicio y hora de fin, siempre dentro de un mismo día; es como se apartan los **espacios** y la unidad sobre la que se calcula el solapamiento.
- **PeriodoDePrestamo**: lo que ocupa un **objeto** mientras está prestado, que no cabe en una franja porque puede durar días. Atributos: fecha y hora de entrega (la que elige la persona), plazo aplicado según el tipo del recurso, fecha y hora de vencimiento (calculada, a las 22:00 del día en que se cumple el plazo), si ya se usó su única renovación y fecha y hora real de devolución, que llega desde `Reportar fecha y hora de entrega`. Para saber si un objeto está libre en una franja consultada se cruza esa franja contra este periodo, igual que se haría contra una reserva de espacio.
- **Usuario**: persona con un rol (Estudiante, Monitor); relación con sus reservas vigentes y su cupo de préstamos, que es de 3 simultáneas (FR-008).
- **Sanción**: restricción temporal sobre un Usuario. Atributos: motivo, fecha de inicio, fecha de fin, estado.
- **Denegación**: registro de un intento de reserva rechazado, con código del diccionario de errores, usuario, recurso y marca de tiempo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un estudiante completa el flujo de consultar y reservar un recurso en menos de 2 minutos desde el inicio de la sesión.
- **SC-002**: El 100 % de los intentos de reserva sobre franjas con bloqueo académico son denegados, con cero reservas estudiantiles solapadas con clases en un semestre completo.
- **SC-003**: El 100 % de las denegaciones se entregan con un código y un mensaje del diccionario de errores que identifica la causa exacta; ninguna denegación genérica.
- **SC-004**: Cero reservas duplicadas sobre el mismo recurso y franja bajo pruebas de concurrencia.
- **SC-005**: Reducción del 50 % en las solicitudes manuales de apartado de espacios gestionadas por la Dirección de Programa durante el primer semestre de operación.
