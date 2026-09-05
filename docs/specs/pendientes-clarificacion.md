# Pendientes de clarificación — Módulo 2

Lista de decisiones que faltan por definir. Cada entrada dice **qué hay que preguntar** y **exactamente dónde se aplica** una vez se tenga la respuesta, para no volver a rastrear los archivos.

**Última revisión**: 2026-09-04

---

## P-01 — ¿Una sola ausencia dispara sanción, o se acumulan?

**Estado**: abierto, pero ya no bloquea a UC2.

La contradicción interna de UC2 quedó resuelta: el edge case y FR-010 ahora dicen lo mismo, que el Módulo 2 **reporta la ausencia al Módulo 3 y no sanciona a nadie**. Lo que sigue abierto es la regla que aplica el Módulo 3: cuántas ausencias hacen falta y cuánto dura el castigo.

**Qué preguntar**: ¿cuántas ausencias acumuladas originan una sanción, y por cuánto tiempo queda sancionado el usuario?

> **Pista encontrada**: la matriz de cumplimiento de [gestionunimag.md](../gestionunimag.md) ya dice *"No Asistencia (Salón) → Bloqueo de reserva de espacios por 1 semana"*. Eso apunta a que **una sola ausencia** basta y la sanción dura **una semana**. Falta confirmarlo con el equipo, porque además la sanción la aplica el **Módulo 3**, no el Módulo 2 (ver [spec-modulo2-uc9-reportar-no-asistencia.md](./spec-modulo2-uc9-reportar-no-asistencia.md)).

**Dónde aplicarlo**:

| Archivo | Punto | Qué cambiar |
|---|---|---|
| `spec-modulo2-uc9-reportar-no-asistencia.md` | Contexto | Fijar la regla que aplicará el Módulo 3, si se decide dejarla escrita aquí como referencia. |
| `spec-modulo2.md` | Punto abierto **Umbral de no-show** | Cerrar la parte del encadenamiento con sanciones. |

> Esta regla es del **Módulo 3**, no del 2. Puede que haya que preguntársela al equipo de ese módulo, no al de este.

> El umbral de tiempo ya está definido: **10 minutos**, contados desde el inicio de la franja en un espacio y desde la hora de recogida en un objeto (UC2 FR-010, UC4 y UC9 FR-001). Lo único abierto es la relación ausencia → sanción. El edge case de UC4 ya no lleva marca de clarificación: se corrigió su umbral, que decía 30 minutos y contradecía al resto del módulo.

---

## P-02 — ¿Cómo se registra que la persona sí se presentó?

**Estado**: abierto solo a medias. La **no** presentación ya está resuelta: la constata el Módulo 3 y nos la reporta, y sin ese reporte no hay ausencia (UC9 FR-001, UC2 FR-010). Lo que sigue sin definirse es el camino contrario, el de quien sí llega: qué acto marca el inicio de uso y hace que el recurso pase de `RESERVADO` a `EN_USO`.

**Qué preguntar**: cuando alguien llega a usar un salón o a recoger un equipo, ¿qué lo registra — el propio estudiante desde la app, alguien en el punto de préstamo, un lector de carné? ¿Y ese registro lo recibe el Módulo 2 directamente, o también viene del Módulo 3, que es quien comprueba la asistencia?

**Dónde aplicarlo**:

| Archivo | Punto | Qué cambiar |
|---|---|---|
| `spec-modulo2-uc2-reservar-recursos.md` | **Functional Requirements** | Añadir el FR que describa el registro de presentación y de entrega, ahora que se sabe quién lo hace. |
| `spec-modulo2-uc9-reportar-no-asistencia.md` | **FR-001** | Precisar por qué medio llega el reporte del monitor. |

---

## P-03 — Sanción retroactiva: ¿se cancelan las reservas ya confirmadas?

**Estado**: abierto, pero UC2 ya lo da por decidido sin respaldo.

El edge case **Sanción que inicia con reservas vigentes** de UC2 afirma que *"se le deben cancelar las que ya tenían"*. Sin embargo `spec-modulo2.md` sigue listando esa política como punto abierto y ningún FR la exige. O se sube a requisito, o se baja a pregunta — hoy es una afirmación huérfana.

**Qué preguntar**: cuando una sanción entra en vigor, ¿se cancelan las reservas ya confirmadas del sancionado, o solo se le impide crear nuevas?

**Dónde aplicarlo**:

| Archivo | Punto | Qué cambiar |
|---|---|---|
| `spec-modulo2-uc2-reservar-recursos.md` | Edge case **Sanción que inicia con reservas vigentes** | Confirmar o corregir la afirmación. |
| `spec-modulo2-uc2-reservar-recursos.md` | **Functional Requirements** | Si se cancelan, añadir el FR que lo exija. |
| `spec-modulo2-uc4-cancelar-reserva.md` | Estados de cancelación | Puede necesitar un motivo de cancelación propio, como ya existe `CANCELADA_POR_PRIORIDAD_ACADEMICA`. |
| `spec-modulo2-uc5-notificar-estado-recursos.md` | Catálogo de eventos | Si hay cancelación automática, debe emitir evento hacia el Módulo 1. |
| `spec-modulo2.md` | Punto abierto **Política de sanción retroactiva** | Cerrarlo. |

---

## P-06 — Direcciones de flecha del diagrama de casos de uso

**Estado**: abierto. Afecta a cómo se redactan las relaciones en varios specs.

En UML, una flecha `A --> B` con `<<include>>` significa *"A incluye a B"*, y con `<<extend>>` significa *"A extiende a B"*, siendo B el caso base.

**Los tres `<<extend>>` ya están bien.** El equipo corrigió `Cancelar reserva` → `Reservar recursos` y agregó `Reportar no asistencia` → `Reservar recursos`; faltaba `Consultar recursos` → `Reservar recursos`, que quedaba al revés y contradecía tanto a UC1 como al propio óvalo punteado de `Reservar recursos`. Se invirtió a `Reservar recursos` → `Consultar recursos`. Los tres apuntan ahora al caso base, en coherencia con el punteado: son extensiones `Reservar recursos`, `Cancelar reserva` y `Reportar no asistencia`, y `Consultar recursos` es el caso base sólido del que cuelga todo.

**Lo único que sigue abierto es un `<<include>>`.**

| Flecha tal como está dibujada | Lo que significa hoy | Lo que probablemente se quiso decir |
|---|---|---|
| `Importar horarios semestrales` `<<include>>` → `Reservar recursos` | Importar incluye a Reservar | Reservar consulta los bloqueos que dejó Importar |

La flecha `Consultar recursos <<include>> Consultar reportes` **sí está bien**: al consultar recursos el sistema comprueba de paso si la persona está sancionada.

**Qué preguntar**: ¿`Importar horarios semestrales` `<<include>>` → `Reservar recursos` se invierte, o los specs deben describirla tal como está dibujada?

**Dónde aplicarlo**: `unimag4.drawio` y la sección **Casos de uso relacionados** de UC1, UC2, UC3 y UC4.

> Menor, en el mismo diagrama: la flecha `Reportar no asistencia` → `Reservar recursos` está dibujada con puntos sueltos en vez de anclada a los dos óvalos. Se ve bien, pero se descoloca si alguien mueve un óvalo. Conviene reengancharla en draw.io.

---

## P-08 — ¿Qué pasa con un préstamo que nunca se devuelve?

**Estado**: abierto, pero ya menos. El comportamiento del recurso sí quedó definido: UC7 FR-011 y su edge case dicen que un préstamo vencido y no devuelto **sigue `EN_USO`**, que el paso del tiempo no lo libera y que solo la devolución registrada lo devuelve a `DISPONIBLE`. Lo que falta es el otro lado: a partir de cuándo se da por perdido y qué se hace entonces.

**Qué preguntar**: ¿a partir de cuántos días un préstamo sin devolver se considera pérdida, y qué hace el sistema entonces — lo escala, genera un cobro, lo saca del inventario?

**Dónde aplicarlo**:

| Archivo | Punto | Qué cambiar |
|---|---|---|
| `spec-modulo2-uc10-reportar-fecha-hora-entrega.md` | Edge case **Devolución que nunca llega** | Definir el plazo y la acción. |
| `spec-modulo2-uc7-actualizar-estado-recursos.md` | Estados del recurso | Puede necesitar un estado o motivo para un recurso dado por perdido. |

---

## P-09 — El Monitor se quedó sin capacidad propia

**Estado**: abierto. Apareció al ajustar los specs al diagrama. La comprobación de asistencia, que podía haber sido su capacidad propia, resultó ser del Módulo 3, así que al Monitor solo le queda como candidato el registro de entregas y devoluciones de equipos (UC10).

El Monitor es una especialización de Estudiante: hereda todo lo suyo. Lo único que lo distinguía dentro del Módulo 2 era **consultar reportes**, y esa consulta ya no la hace ninguna persona: la ejecuta el sistema y el resultado va al Módulo 3. Tal como quedan el diagrama y los specs, el Monitor no hace nada que un Estudiante no pueda hacer, así que como actor separado ya no aporta.

**Qué preguntar**: ¿qué puede hacer un Monitor que un Estudiante no? Un candidato razonable es registrar la devolución de un equipo en el punto de préstamo, que es como está escrito hoy en `Reportar fecha y hora de entrega`, pero eso no está en el diagrama y hay que confirmarlo.

**Dónde aplicarlo**:

| Archivo | Punto | Qué cambiar |
|---|---|---|
| `unimag4.drawio` | Actor **Monitor** | Dibujarle una línea propia al caso de uso que lo distinga, o eliminarlo como actor separado. |
| `spec-modulo2.md` | Tabla de **Actores** | Describir su capacidad propia. |
| `spec-modulo2-uc10-reportar-fecha-hora-entrega.md` | Tabla de **Actores** | Confirmar o quitar la fila que le atribuye el registro de devoluciones. |

---

## P-10 — Si el Módulo 3 no responde, ¿se bloquea la reserva o se permite?

**Estado**: abierto. Decisión de política, no técnica.

`Consultar reportes` obtiene del Módulo 3 si la persona está sancionada. Si el Módulo 3 está caído, el sistema no puede saberlo. Hay dos caminos y ninguno es gratis: bloquear todas las reservas mientras dure la caída (nadie sancionado se cuela, pero todo el mundo queda parado), o permitirlas y revisarlas después (el servicio sigue, pero alguien sancionado puede reservar).

Hoy el spec está escrito con la opción conservadora: no se confirma la reserva.

**Qué preguntar**: ¿qué prefiere la universidad, que el sistema se detenga o que siga funcionando asumiendo el riesgo?

**Dónde aplicarlo**:

| Archivo | Punto | Qué cambiar |
|---|---|---|
| `spec-modulo2-uc6-consultar-reportes.md` | Escenario **El Módulo 3 no responde** y **FR-006** | Fijar la política y quitar el `[NEEDS CLARIFICATION]`. |
| `spec-modulo2-uc2-reservar-recursos.md` | **FR-002** | Debe decir qué pasa cuando la sanción no se pudo comprobar. |

---

## P-11 — ¿Las sanciones distinguen entre espacios y equipos?

**Estado**: abierto.

La matriz de [gestionunimag.md](../gestionunimag.md) dice que la no asistencia produce *"bloqueo de reserva de espacios por 1 semana"*. Habla de espacios, no de equipos. Si el alcance es real, una persona con esa sanción debería poder seguir pidiendo prestado un microscopio, y hoy la denegación `RES-003` no distingue: bloquea todo.

**Qué preguntar**: ¿cada sanción tiene un alcance (espacios, equipos o ambos), o una sanción bloquea cualquier reserva?

**Dónde aplicarlo**:

| Archivo | Punto | Qué cambiar |
|---|---|---|
| `spec-modulo2-uc6-consultar-reportes.md` | Edge case **Sanciones que no aplican** y entidad **Sanción** | Confirmar si el alcance viene en el reporte del Módulo 3. |
| `spec-modulo2-uc2-reservar-recursos.md` | **FR-002** y escenario de `RES-003` | Si hay alcance, la denegación solo aplica al tipo de recurso sancionado. |

---

## P-13 — ¿El Módulo 3 premia la cancelación a tiempo?

**Estado**: abierto.

`Reportar cancelación de reserva` reporta la antelación con la que el titular canceló, partiendo de que [gestionunimag.md](../gestionunimag.md) trata el cumplimiento como un "score" de confianza. Pero la matriz de esa fuente solo describe castigos: no dice si cancelar a tiempo suma algo, ni desde cuánta antelación cuenta como mérito.

**Qué preguntar**: ¿una cancelación a tiempo mejora el "score" de la persona, y a partir de cuánta antelación? ¿O simplemente evita la ausencia y nada más?

> Es una regla del **Módulo 3**, no del 2. Este módulo reporta la antelación en cualquier caso; lo abierto es qué hace el otro con ella.

**Dónde aplicarlo**:

| Archivo | Punto | Qué cambiar |
|---|---|---|
| `spec-modulo2-uc11-reportar-cancelacion-reserva.md` | Contexto y **FR-004** | Confirmar que la antelación es el dato que el Módulo 3 necesita, o cambiarlo por el que pida. |
| `spec-modulo2-uc6-consultar-reportes.md` | Entidad del reporte de cumplimiento | Verificar si el "score" entra en lo que devuelve el Módulo 3. |

---

## P-14 — Si el Módulo 1 no responde, ¿qué muestra `Consultar recursos`?

**Estado**: abierto. Es el gemelo de P-10, pero con el otro módulo.

El diagrama ya deja explícito que `Consultar recursos` depende del Módulo 1: de allí salen el catálogo, los atributos y el estado de cada recurso. Si ese módulo está caído, el Módulo 2 no tiene de dónde armar la lista. Hay dos caminos: no mostrar nada y decir que el servicio no está disponible, o mostrar la última información conocida advirtiendo que puede estar vieja.

Hoy el spec está escrito con la opción conservadora: no se presenta como vigente una lista que no se pudo comprobar (UC1 FR-008), y su edge case ya **no lleva marca de clarificación**, así que en la práctica el Módulo 2 está decidido. Lo que queda es confirmarlo con el equipo, no reescribirlo.

**Qué preguntar**: ¿la consulta se cae con el Módulo 1, o sigue mostrando la última foto conocida con una advertencia?

> Ojo con la interacción: si se decide mostrar información vieja, `Reservar recursos` tiene que seguir revalidando contra el Módulo 1 antes de confirmar. Una lista orientativa es aceptable; una reserva confirmada sobre datos viejos no.

**Dónde aplicarlo**:

| Archivo | Punto | Qué cambiar |
|---|---|---|
| `spec-modulo2-uc1-consultar-recursos.md` | Edge case **El Módulo 1 no responde** y **FR-008** | Fijar la política y quitar el `[NEEDS CLARIFICATION]`. |
| `spec-modulo2-uc8-consultar-disponibilidad-recursos.md` | Comportamiento ante inventario no disponible | Debe decir lo mismo que UC1. |
| `spec-modulo2-uc2-reservar-recursos.md` | Revalidación previa a confirmar | Verificar que nunca confirme sobre datos no comprobados. |

---

## P-15 — ¿Quién calcula el cruce de franjas, el Módulo 1 o el 2?

**Estado**: abierto. Salió al comparar con el spec del Módulo 1 `Consultar disponibilidad del recurso`.

Su spec devuelve dos cosas: el **estado actual** del recurso (FR-001) y su **horario de ocupación** con hora de inicio y fin (FR-002). Eso es un calendario, no una respuesta de sí o no. Nuestro UC8 FR-001, en cambio, está escrito como si el Módulo 1 ya resolviera la pregunta: *"si un recurso concreto está o no disponible en una fecha y franja horaria concretas"*.

Su edge case de *"fecha/hora que ya pasó → fecha inválida"* indica que sí aceptan un parámetro de fecha, pero ningún requisito suyo dice qué calculan con él.

**Qué preguntar**: cuando el Módulo 2 pregunta por una franja futura, ¿el Módulo 1 responde disponible/no disponible, o devuelve el calendario de ocupación para que el Módulo 2 cruce las franjas?

> Nuestro UC1 FR-003 ya dice que el Módulo 2 calcula la disponibilidad como *"la ausencia de intersección"*. Si esa sigue siendo la idea, el que hay que reescribir es UC8, no ellos.

**Dónde aplicarlo**:

| Archivo | Punto | Qué cambiar |
|---|---|---|
| `spec-modulo2-uc8-consultar-disponibilidad-recursos.md` | **FR-001** y escenarios | Decir si esta consulta recibe un sí/no del Módulo 1 o si cruza el calendario ella misma. |
| `spec-modulo2-uc1-consultar-recursos.md` | **FR-003** | Debe quedar coherente con lo anterior; hoy atribuye el cálculo al Módulo 2. |

---

## P-16 — Datos que necesitamos del Módulo 1 y su consulta no devuelve

**Estado**: abierto, a la espera de su respuesta. Ya se les envió la solicitud. Es la razón por la que `Consultar recursos` necesita su propia línea al Módulo 1.

Nuestro UC1 FR-001 permite filtrar *"para espacios, por aforo mínimo"*, y el escenario 1 muestra el recurso junto con su aforo máximo. Pero `Consultar disponibilidad del recurso` del Módulo 1 solo filtra por **categoría** (FR-003) y solo devuelve *"el estado de disponibilidad actual"*. Su entidad `Espacio` sí tiene `aforo maximo`, `equipamiento`, `ubicacion` y `facultad`, pero esa operación no los expone.

Al concretar el préstamo de objetos apareció un segundo dato que tampoco tienen: el **plazo máximo de préstamo por tipo de recurso**, que UC2 FR-012 define como atributo del recurso —un número en días hábiles, no una fecha— y que el Módulo 1 aceptó agregar a la creación de recurso. Hasta que exista, FR-012 no se puede implementar.

La lista completa de lo que les pedimos en la respuesta: identificador, nombre y tipo; aforo máximo, equipamiento fijo, facultad y ubicación de los espacios; placa, estado físico y ubicación de los objetos; el plazo máximo de préstamo; el motivo cuando el recurso no está disponible; el total de resultados; y las horas en `America/Bogota`. Aparte, poder filtrar por aforo mínimo y preguntar por varios recursos de una vez (UC8 FR-008).

**Qué preguntar**: ¿extienden `Consultar disponibilidad del recurso` para devolver los atributos del catálogo y filtrar por aforo, o el Módulo 1 expone una **operación aparte** de consulta de catálogo?

> De la respuesta depende cómo se lee la asociación directa `Consultar recursos` → Módulo 1 del diagrama: si es una segunda operación, conviene que el diagrama del Módulo 1 la muestre como un óvalo propio.

**Dónde aplicarlo**:

| Archivo | Punto | Qué cambiar |
|---|---|---|
| `spec-modulo2-uc1-consultar-recursos.md` | Contexto, **FR-001** y **FR-006** | Nombrar la operación real del Módulo 1 de la que sale el catálogo. |
| `unimag4.drawio` | Asociación `Consultar recursos` — Módulo 1 | Confirmar que representa una operación distinta de `Consultar disponibilidad`. |

---

## P-17 — Los tiempos de respuesta no cuadran entre módulos

**Estado**: abierto, mitigado de nuestro lado.

El Módulo 1 promete su consulta en **menos de 5 s** con hasta 100 consultas concurrentes, y admite hasta **10 s** en pico de 500. Nuestro UC1 prometía **2 s con 500 concurrentes**: imposible, porque el Módulo 2 no puede responder más rápido que su fuente.

Ya se corrigió del lado nuestro (UC1 SC-001 pasó a 6 s / 12 s, y UC8 SC-001 a 5 s), pero esos números son ahora una consecuencia de los suyos, no una decisión de producto.

**Qué preguntar**: ¿son 5 s y 10 s los objetivos definitivos del Módulo 1, o hay margen para bajarlos? Si la universidad quiere una consulta de menos de 3 s, el compromiso tiene que cambiar en el Módulo 1 primero.

**Dónde aplicarlo**:

| Archivo | Punto | Qué cambiar |
|---|---|---|
| `spec-modulo2-uc1-consultar-recursos.md` | **SC-001** | Recalcular si el Módulo 1 mejora su compromiso. |
| `spec-modulo2-uc8-consultar-disponibilidad-recursos.md` | **SC-001** | Igual. |

---

## P-18 — ¿El "monitor" del Módulo 1 es el mismo que el nuestro?

**Estado**: abierto. Relacionado con P-09.

El spec del Módulo 1 nombra como actor a *"el monitor de recursos"*, que por el contexto suena a personal que administra el inventario. Nuestro Monitor es una **especialización de Estudiante**. Si son dos roles distintos con el mismo nombre, va a haber confusión en cuanto los dos módulos se junten.

**Qué preguntar**: ¿el "monitor de recursos" del Módulo 1 es el mismo Monitor estudiantil del Módulo 2, o es personal administrativo?

> Si resulta ser el mismo, se cierra P-09 de paso: administrar el inventario sería justamente la capacidad propia que hoy le falta al Monitor.

**Dónde aplicarlo**:

| Archivo | Punto | Qué cambiar |
|---|---|---|
| `spec-modulo2.md` | Tabla de **Actores** | Precisar de qué monitor se habla. |
| `pendientes-clarificacion.md` | **P-09** | Puede cerrarse con la respuesta. |

---

## Resueltos

- **La ausencia la reporta el Módulo 3, no la detecta un reloj** (parte de P-02) — el Módulo 2 no declara ausencias por su cuenta: el Módulo 3 constata que el titular no llegó y se lo reporta, y ese aviso es lo que libera el recurso y deja la constancia. Cambia el sentido del flujo: UC9 ya no reporta hacia el Módulo 3, sino que recibe de él. El plazo de 10 minutos sigue vigente como criterio de admisión: un reporte anticipado se rechaza. Si el Módulo 3 está caído no llegan reportes y los recursos siguen apartados hasta el fin de su franja, porque el Módulo 2 no suple esa función. Se añadió la anulación de un reporte enviado por error. Reescrito UC9 (contexto, actores, historia, escenarios, FR-001, FR-002, FR-004, FR-008, FR-011, FR-012, entidad `Ausencia` y SC-001, que ahora se mide desde la llegada del reporte) y alineados UC2 FR-010 y su tabla de actores, UC4, UC5 y UC7. *(2026-09-04)*

- **Un espacio se aparta por franjas y un objeto por periodos** — es el cambio de fondo que trajo el préstamo por días. `FranjaHoraria` queda acotada a un mismo día y a los espacios (2 horas máximo); nace `PeriodoDePrestamo` para los objetos, que va de la recogida al vencimiento y puede durar días. Lo que se **pregunta** sigue siendo siempre una franja de un solo día; lo que **ocupa** puede ser una franja o un periodo, y el cruce se calcula igual contra ambos. Aplicado en los once specs: UC1 (FR-003, FR-015 y escenario del objeto prestado), UC2 (FR-001, FR-004, FR-010, FR-012 a FR-017, entidades y escenarios), UC3 (FR-010: la prioridad académica desplaza un objeto apartado pero **no** uno ya entregado, que se reporta como choque), UC4 (FR-001, FR-002, FR-008 y escenario de cancelación de objeto no recogido), UC5 (FR-002), UC6 (FR-003, que ahora cubre la renovación), UC7 (FR-002, FR-006, FR-010, FR-011 y escenario de devolución), UC8 (FR-012 y escenario del objeto prestado), UC9 (FR-001, FR-002, FR-004), UC10 (escenarios alineados con el vencimiento a las 22:00) y UC11 (FR-002). *(2026-09-04)*
- **Umbral de no-show mal copiado en UC4** — su edge case decía **30 minutos** mientras UC2, UC5, UC9 y `spec-modulo2.md` decían 10. Se corrigió a 10 y se precisó desde dónde se cuentan según el tipo de recurso. *(2026-09-04)*
- **Paginación de `Consultar recursos`** — páginas de **20 recursos**, sin paginar cuando hay 20 o menos; cada página informa el total y en cuál va, y el orden es estable y determinista (nombre, y el identificador del Módulo 1 como desempate) para que nada se duplique ni se salte al pasar de página. El tamaño lo decide el Módulo 2 y no depende de cómo el Módulo 1 entregue el inventario. UC1 FR-009, FR-013 y FR-014. *(2026-09-04)*
- **Los recursos ocupados se muestran, no se ocultan** — la consulta lista todo lo que cumple los filtros y marca con su estado lo que no se puede seleccionar; si no queda ninguno seleccionable, la lista se muestra igual con un mensaje que lo explica. El escenario "sin resultados" de UC1, que devolvía una lista vacía, contradecía a los demás y se reescribió. UC1 FR-012. *(2026-09-04)*

- **Renovación de préstamos** (último punto de P-04, que queda cerrado) — se permite renovar **una sola vez** por préstamo, sin devolver el objeto. El plazo se suma desde el vencimiento vigente, no desde el día de la renovación, para no castigar a quien renueva con antelación, y no consume cupo nuevo de los 3 préstamos. No se renueva un préstamo ya vencido (ahí corre la mora del Módulo 3), ni el de alguien con sanción activa, ni un objeto de plazo `0` de uso en sitio, ni cuando otra persona ya tiene ese objeto apartado para una fecha que el nuevo vencimiento invadiría. Aplicado en UC2 FR-016 y FR-017, con su edge case, y reflejado en la entidad `Préstamo` de UC10 y en `spec-modulo2.md`. *(2026-09-04)*

- **Duración máxima de una reserva** (parte de P-04) — un **espacio** se reserva por franjas de máximo **2 horas continuas** (UC2 FR-009); se pueden encadenar dos franjas seguidas mientras quede cupo. Un **equipo** se presta por un plazo que depende de su tipo, expresado en días hábiles, y ese plazo es un atributo que el Módulo 1 fija al crear el recurso (UC2 FR-012 y FR-014): Libro 7, Kit de dibujo 3, Videobeam 1, Microscopio 0. El Módulo 1 aceptó agregar el campo a su ficha de recurso. El vencimiento siempre se ajusta para caer dentro de la ventana de 06:00 a 22:00 (UC2 FR-013). *(2026-09-04)*

- **Límite máximo de préstamos simultáneos** (parte de P-04) — definido por el equipo en **3 reservas vigentes por persona**, parametrizable. Mismo tope para Estudiante y Monitor, y cupo único: espacios y equipos cuentan juntos. Solo ocupan cupo las reservas `CONFIRMADA` cuya franja no ha terminado. Aplicado en UC2 FR-008, en su escenario de denegación `RES-002`, en la entidad Usuario y en `spec-modulo2.md`. *(2026-09-04)*
- **Antelación mínima de cancelación** (parte de P-04) — ya estaba definida en **10 minutos** en UC4 FR-007; P-04 la citaba mal, como 5 minutos y en un FR-008 que no existe. `spec-modulo2.md` la seguía listando entre los parámetros sin definir y ya no lo hace. *(2026-09-04)*

- **Umbral de no-show** — definido en 10 minutos desde el inicio de la franja. Aplicado en UC2 FR-010; UC4 y `spec-modulo2.md` ya remiten a él. *(2026-09-03)*
- **Estado de una reserva vigente** — es `RESERVADO`, no `EN_USO`; el recurso solo pasa a `EN_USO` cuando llega la franja y la persona se presenta. Corregido en el glosario de UC3, que aún decía lo contrario. *(2026-09-03)*
- **Quién sanciona** — el Módulo 2 detecta y reporta; el Módulo 3 decide y aplica. Alineados UC2, UC4, UC5 y UC9 con ese reparto; UC11 nace ya con ese reparto. *(2026-09-03)*
- **`Consultar reportes` no la pide ninguna persona y va en sentido contrario al que se creía** — no produce reportes para nadie: **obtiene** del Módulo 3 el reporte de cumplimiento de una persona, para poder explicarle por qué no puede reservar cuando tiene una sanción. Se retiró el error `REP-001` del diccionario consolidado, porque ya no hay ningún rol al que negarle el acceso. *(2026-09-03)*
- **Solapamiento entre `Reportar cancelación de reserva` y los avisos de UC5** (antes P-12) — resuelto en `Use Case 5 completed`: UC5 se queda solo con `RECURSO_SIN_NOVEDAD` y `RECURSO_CON_NOVEDAD`, porque notifica **en qué estado quedó el recurso después de usarlo**. Una reserva cancelada o una ausencia no generan aviso desde UC5, ya que el recurso nunca se usó; esas dos situaciones las reportan UC11 y UC9. Se movió a UC11 el edge case de cierre masivo por importación, que contradecía el escenario de prioridad académica de UC5. *(2026-09-04)*
- **`Reportar fecha y hora de entrega` aplica solo a equipos** (antes P-07) — resuelto por el equipo en `arreglo de specs 7-10 y diagrama`: los espacios no se devuelven físicamente, se liberan solos al terminar la franja. Este caso de uso queda exclusivo para muebles y equipos en préstamo. *(2026-09-04)*
- **Horario de operación y franjas que cruzan la medianoche** — resuelto por el equipo: la ventana es de 06:00 a 22:00 en hora local de Colombia (`America/Bogota`, UTC-5), no se permiten franjas nocturnas ni que crucen la medianoche. Aplicado en UC1, UC8 y `spec-modulo2.md`; UC1 lo recoge además como FR-010 y FR-011. *(2026-09-04)*
- **Estado `FUERA_DE_SERVICIO` fuera del catálogo** (antes P-05) — el spec del Módulo 1 para `Consultar disponibilidad del recurso` confirma exactamente los cinco estados de `gestionunimag.md`, sin un sexto. Se reemplazó `FUERA_DE_SERVICIO` por `EN_MANTENIMIENTO` en UC3 y UC4. *(2026-09-04)*
- **`Consultar recursos` no decía de dónde salen los recursos** — el spec ya nombraba al Módulo 1 como actor secundario, pero el diagrama no dibujaba ninguna línea entre ambos. Se agregaron la asociación directa `Consultar recursos` — Módulo 1 (el catálogo y sus atributos) y el `<<include>>` hacia `Consultar disponibilidad de los recursos` (el estado en la franja), que UC1 y UC8 ya daban por supuesto. El `<<include>>` lo agregó también el equipo por su cuenta en `unimag4.drawio`; la asociación directa a Módulo 1 se portó a ese diagrama. *(2026-09-04)*
- **Enlace roto en UC4** — su sección *Casos de uso relacionados* apuntaba `Reportar cancelación de reserva` al archivo de UC5; ahora apunta a [spec-modulo2-uc11-reportar-cancelacion-reserva.md](./spec-modulo2-uc11-reportar-cancelacion-reserva.md), que es el caso de uso que el diagrama 3 hizo explícito. *(2026-09-04)*
