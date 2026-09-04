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
| `spec-modulo2-uc4-cancelar-reserva.md` | Edge case **No presentación** | Quitar su `[NEEDS CLARIFICATION: cuánto dura la sanción y cuántas ausencias hacen falta]`. |
| `spec-modulo2.md` | Punto abierto **Umbral de no-show** | Cerrar la parte del encadenamiento con sanciones. |

> Esta regla es del **Módulo 3**, no del 2. Puede que haya que preguntársela al equipo de ese módulo, no al de este.

> El umbral de tiempo ya está definido: **10 minutos** desde el inicio de la franja (UC2 FR-010). Lo único abierto es la relación ausencia → sanción.

---

## P-02 — ¿Cómo se registra que la persona se presentó?

**Estado**: abierto. Hueco de requisito en UC2.

El escenario 1 dice que el recurso pasa a `EN_USO` *"cuando la persona se presenta"*, y FR-010 se dispara *"sin que se registre el uso"*. Ningún requisito define qué cuenta como presentarse ni por qué medio se registra (código en el sitio, confirmación en la app, validación de un monitor, lector de carné…). Sin eso, ni la transición `RESERVADO` → `EN_USO` ni el no-show son implementables.

**Qué preguntar**: ¿qué acción concreta marca el inicio de uso, y quién la ejecuta — el propio estudiante, un monitor, o un dispositivo?

**Dónde aplicarlo**:

| Archivo | Punto | Qué cambiar |
|---|---|---|
| `spec-modulo2-uc2-reservar-recursos.md` | **Functional Requirements** | Añadir un FR nuevo que defina el registro de uso y la transición `RESERVADO` → `EN_USO`. |
| `spec-modulo2-uc2-reservar-recursos.md` | Escenario 1 y edge case de no-show | Reemplazar *"la persona se presenta"* por el mecanismo real. |
| `spec-modulo2-uc1-consultar-recursos.md` | Estados del recurso | Verificar que la transición quede consistente con el catálogo de cinco estados. |

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

## P-04 — Parámetros sin valor

**Estado**: abierto. No bloquean la redacción, sí la implementación.

**Qué preguntar**:

- Límite máximo de préstamos simultáneos: ¿valor por defecto? ¿varía por rol (Estudiante / Monitor) o por tipo de recurso?
- Duración máxima de una reserva: ¿cuántas horas?

**Dónde aplicarlo**:

| Archivo | Punto | Qué cambiar |
|---|---|---|
| `spec-modulo2-uc2-reservar-recursos.md` | **FR-008** | Fijar el valor por defecto y quitar el `[NEEDS CLARIFICATION]`. |
| `spec-modulo2-uc2-reservar-recursos.md` | **FR-009** | Fijar la duración máxima y quitar el `[NEEDS CLARIFICATION]`. |
| `spec-modulo2.md` | Punto abierto **Parámetros no definidos** | Cerrar los dos que correspondan. |

> La **antelación mínima de cancelación** ya está definida en 5 minutos (UC4 FR-008), pero `spec-modulo2.md` la sigue listando como no definida. Ese punto se puede corregir sin esperar a nadie.

---

## P-06 — Direcciones de flecha del diagrama de casos de uso

**Estado**: abierto. Afecta a cómo se redactan las relaciones en varios specs.

Quedan **dos** flechas cuya dirección dice lo contrario de lo que parece querer decir el equipo. La tercera, `Reservar recursos` `<<extend>>` → `Cancelar reserva`, ya se corrigió en `unimag4.drawio`: ahora va `Cancelar reserva` → `Reservar recursos`, que es lo correcto. En UML, una flecha `A --> B` con `<<include>>` significa *"A incluye a B"*, y con `<<extend>>` significa *"A extiende a B"*, siendo B el caso base.

| Flecha tal como está dibujada | Lo que significa hoy | Lo que probablemente se quiso decir |
|---|---|---|
| `Consultar recursos` `<<extend>>` → `Reservar recursos` | Consultar es un añadido opcional de Reservar | Reservar es la continuación opcional de Consultar |
| `Importar horarios semestrales` `<<include>>` → `Reservar recursos` | Importar incluye a Reservar | Reservar consulta los bloqueos que dejó Importar |

La flecha `Consultar recursos <<include>> Consultar reportes` **sí está bien**: al consultar recursos el sistema comprueba de paso si la persona está sancionada.

**Qué preguntar**: ¿se corrigen las tres flechas restantes en el diagrama, o los specs deben describir las relaciones tal como están dibujadas?

**Dónde aplicarlo**: `unimag4.drawio` y la sección **Casos de uso relacionados** de UC1, UC2, UC3 y UC4.

---

## P-08 — ¿Qué pasa con un préstamo que nunca se devuelve?

**Estado**: abierto.

**Qué preguntar**: ¿a partir de cuántos días un préstamo sin devolver se considera pérdida, y qué hace el sistema entonces — lo escala, genera un cobro, lo saca del inventario?

**Dónde aplicarlo**:

| Archivo | Punto | Qué cambiar |
|---|---|---|
| `spec-modulo2-uc10-reportar-fecha-hora-entrega.md` | Edge case **Devolución que nunca llega** | Definir el plazo y la acción. |
| `spec-modulo2-uc7-actualizar-estado-recursos.md` | Estados del recurso | Puede necesitar un estado o motivo para un recurso dado por perdido. |

---

## P-09 — El Monitor se quedó sin capacidad propia

**Estado**: abierto. Apareció al ajustar los specs al diagrama.

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

Hoy el spec está escrito con la opción conservadora: no se presenta como vigente una lista que no se pudo comprobar (UC1 FR-008).

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

## P-16 — El aforo no viene en la respuesta del Módulo 1

**Estado**: abierto. Es la razón por la que `Consultar recursos` necesita su propia línea al Módulo 1.

Nuestro UC1 FR-001 permite filtrar *"para espacios, por aforo mínimo"*, y el escenario 1 muestra el recurso junto con su aforo máximo. Pero `Consultar disponibilidad del recurso` del Módulo 1 solo filtra por **categoría** (FR-003) y solo devuelve *"el estado de disponibilidad actual"*. Su entidad `Espacio` sí tiene `aforo maximo`, `equipamiento`, `ubicacion` y `facultad`, pero esa operación no los expone.

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
