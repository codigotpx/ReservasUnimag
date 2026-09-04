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

## P-05 — Estado `FUERA_DE_SERVICIO` fuera del catálogo

**Estado**: abierto. Inconsistencia entre casos de uso de distintos autores.

`spec-modulo2-uc1-consultar-recursos.md` fija el catálogo oficial en cinco estados, conforme a `gestionunimag.md`: `DISPONIBLE`, `RESERVADO`, `BLOQUEO_ACADEMICO`, `EN_USO` y `EN_MANTENIMIENTO`. Pero UC3 y UC4 usan un sexto estado, `FUERA_DE_SERVICIO`, que no existe en esa lista.

**Qué preguntar**: ¿`FUERA_DE_SERVICIO` es lo mismo que `EN_MANTENIMIENTO` y sobra, o es un estado distinto que hay que añadir al catálogo del Módulo 1?

**Dónde aplicarlo**:

| Archivo | Punto | Qué cambiar |
|---|---|---|
| `spec-modulo2-uc3-importar-horarios-semestrales.md` | Edge case **Recurso dado de baja** | Usar el nombre correcto. |
| `spec-modulo2-uc4-cancelar-reserva.md` | Edge case **Recurso dado de baja** | Usar el nombre correcto. |
| `spec-modulo2-uc1-consultar-recursos.md` | **Estados del recurso** y **FR-002** | Si es un estado nuevo, añadirlo al catálogo y a la lista de exclusión. |

---

## P-06 — Direcciones de flecha del diagrama de casos de uso

**Estado**: abierto. Afecta a cómo se redactan las relaciones en varios specs.

En `unimag3.drawio` (y antes en `unimag2.drawio`) hay tres flechas cuya dirección dice lo contrario de lo que parece querer decir el equipo. En UML, una flecha `A --> B` con `<<include>>` significa *"A incluye a B"*, y con `<<extend>>` significa *"A extiende a B"*, siendo B el caso base.

| Flecha tal como está dibujada | Lo que significa hoy | Lo que probablemente se quiso decir |
|---|---|---|
| `Consultar recursos` `<<extend>>` → `Reservar recursos` | Consultar es un añadido opcional de Reservar | Reservar es la continuación opcional de Consultar |
| `Reservar recursos` `<<extend>>` → `Cancelar reserva` | Reservar extiende a Cancelar | Cancelar extiende a Reservar |
| `Importar horarios semestrales` `<<include>>` → `Reservar recursos` | Importar incluye a Reservar | Reservar consulta los bloqueos que dejó Importar |

La flecha `Consultar recursos <<include>> Consultar reportes` **sí está bien**: al consultar recursos el sistema comprueba de paso si la persona está sancionada.

**Qué preguntar**: ¿se corrigen las tres flechas restantes en el diagrama, o los specs deben describir las relaciones tal como están dibujadas?

**Dónde aplicarlo**: `unimag3.drawio` y la sección **Casos de uso relacionados** de UC1, UC2, UC3 y UC4.

---

## P-07 — ¿`Reportar fecha y hora de entrega` aplica solo a equipos?

**Estado**: abierto.

Un microscopio o un videobeam se devuelven físicamente; un salón no. Para los espacios, la hora de fin de la franja haría las veces de devolución. El caso de uso está escrito asumiendo préstamos de equipos.

**Qué preguntar**: ¿este caso de uso cubre solo equipos en préstamo, o también hay que registrar una "entrega" para los espacios?

**Dónde aplicarlo**:

| Archivo | Punto | Qué cambiar |
|---|---|---|
| `spec-modulo2-uc10-reportar-fecha-hora-entrega.md` | Contexto y edge case **Recursos que no se prestan** | Fijar el alcance y quitar el `[NEEDS CLARIFICATION]`. |
| `spec-modulo2-uc7-actualizar-estado-recursos.md` | Escenario de fin de franja | Verificar que la liberación de espacios sea coherente con lo que se decida. |

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
| `unimag3.drawio` | Actor **Monitor** | Dibujarle una línea propia al caso de uso que lo distinga, o eliminarlo como actor separado. |
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

## P-12 — ¿`Reportar cancelación de reserva` reemplaza los avisos de cancelación de UC5?

**Estado**: abierto. Apareció con el óvalo nuevo del diagrama 3 (`unimag3.drawio`).

El diagrama 3 agrega el óvalo `Reportar cancelación de reserva`, conectado directamente al Módulo 3, igual que `Reportar no asistencia` y `Reportar fecha y hora de entrega`. Pero `Notificar estado de recursos al finalizar reserva` (UC5) ya tenía en su catálogo los avisos `RESERVA_CANCELADA` y `RESERVA_CANCELADA_POR_PRIORIDAD`, que cuentan exactamente lo mismo. Tal como está, el Módulo 3 recibiría dos veces la misma cancelación.

**Qué preguntar**: ¿el caso de uso nuevo se queda con todas las cancelaciones y UC5 pierde esos dos avisos, o UC5 sigue siendo el único emisor y el óvalo nuevo solo detalla lo que ya hacía?

> Lo más limpio es lo primero: cada reporte hacia el Módulo 3 tiene un óvalo propio (ausencia, devolución, cancelación) y UC5 se queda con los cierres que no encajan en ninguno de los tres, como `RESERVA_FINALIZADA` y `RECURSO_CON_NOVEDAD`. Falta confirmarlo.

**Dónde aplicarlo**:

| Archivo | Punto | Qué cambiar |
|---|---|---|
| `spec-modulo2-uc11-reportar-cancelacion-reserva.md` | Contexto | Quitar el `[NEEDS CLARIFICATION]` y fijar el alcance. |
| `spec-modulo2-uc5-notificar-estado-recursos.md` | **Catálogo de avisos** | Quitar `RESERVA_CANCELADA` y `RESERVA_CANCELADA_POR_PRIORIDAD` si pasan a UC11, junto con los escenarios 2 y 3. |
| `spec-modulo2.md` | Punto abierto sobre el solapamiento | Cerrarlo. |

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

## Resueltos

- **Umbral de no-show** — definido en 10 minutos desde el inicio de la franja. Aplicado en UC2 FR-010; UC4 y `spec-modulo2.md` ya remiten a él. *(2026-09-03)*
- **Estado de una reserva vigente** — es `RESERVADO`, no `EN_USO`; el recurso solo pasa a `EN_USO` cuando llega la franja y la persona se presenta. Corregido en el glosario de UC3, que aún decía lo contrario. *(2026-09-03)*
- **Quién sanciona** — el Módulo 2 detecta y reporta; el Módulo 3 decide y aplica. Alineados UC2, UC4, UC5 y UC9 con ese reparto; UC11 nace ya con ese reparto. *(2026-09-03)*
- **`Consultar reportes` no la pide ninguna persona y va en sentido contrario al que se creía** — no produce reportes para nadie: **obtiene** del Módulo 3 el reporte de cumplimiento de una persona, para poder explicarle por qué no puede reservar cuando tiene una sanción. Se retiró el error `REP-001` del diccionario consolidado, porque ya no hay ningún rol al que negarle el acceso. *(2026-09-03)*
- **Enlace roto en UC4** — su sección *Casos de uso relacionados* apuntaba `Reportar cancelación de reserva` al archivo de UC5; ahora apunta a [spec-modulo2-uc11-reportar-cancelacion-reserva.md](./spec-modulo2-uc11-reportar-cancelacion-reserva.md), que es el caso de uso que el diagrama 3 hizo explícito. *(2026-09-04)*
